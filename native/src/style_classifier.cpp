#include "style_classifier.h"

#include <algorithm>
#include <cmath>
#include <numeric>
#include <vector>

#include "audio_decode.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "StyleClassifier"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...)
#define LOGE(...)
#endif

#include <essentia/algorithmfactory.h>
#include <essentia/essentiamath.h>
#include <onnxruntime_c_api.h>

using namespace essentia;
using namespace essentia::standard;

static const int STYLE_SR = 16000;
static const int FRAME_SIZE = 512;
static const int HOP_SIZE = 256;
static const int NUM_BANDS = 96;
static const int PATCH_FRAMES = 128;
static const int NUM_CLASSES = 400;

static bool is_cancelled(EssentiaCancelFlag* flag) {
  return essentia_cancel_flag_is_set(flag) != 0;
}

struct OrtContext {
  const OrtApi* ort = nullptr;
  OrtAllocator* allocator = nullptr;
  OrtEnv* env = nullptr;
  OrtSessionOptions* session_opts = nullptr;
  OrtSession* session = nullptr;
  OrtMemoryInfo* mem_info = nullptr;
  char* input_name = nullptr;
  char* output_name = nullptr;

  ~OrtContext() {
    if (input_name && allocator) allocator->Free(allocator, input_name);
    if (output_name && allocator) allocator->Free(allocator, output_name);
    if (mem_info && ort) ort->ReleaseMemoryInfo(mem_info);
    if (session && ort) ort->ReleaseSession(session);
    if (session_opts && ort) ort->ReleaseSessionOptions(session_opts);
    if (env && ort) ort->ReleaseEnv(env);
  }
};

extern "C" {

StyleResult essentia_classify_style(const char* audio_path, const char* model_path,
                                    EssentiaCancelFlag* cancel_flag) {
  StyleResult result = {};
  result.count = 0;
  result.error_code = 0;

  std::vector<float> audio;
  int decode_ret = decode_audio(audio_path, audio, STYLE_SR, cancel_flag);
  if (decode_ret < 0) {
    result.error_code = 2;
    return result;
  }
  if (decode_ret == 1 || is_cancelled(cancel_flag)) {
    result.error_code = 1;
    return result;
  }

  if (audio.empty()) {
    LOGE("No audio samples decoded");
    result.error_code = 2;
    return result;
  }

  LOGI("Decoded %zu samples (%.1f seconds) at %dHz", audio.size(), (float)audio.size() / STYLE_SR,
       STYLE_SR);

  if (is_cancelled(cancel_flag)) {
    result.error_code = 1;
    return result;
  }

  AlgorithmFactory& factory = AlgorithmFactory::instance();

  std::vector<std::vector<float> > mel_frames;

  try {
    std::vector<float> windowed_frame;
    std::vector<float> spectrum;
    std::vector<float> mel_bands;

    Algorithm* frameCutter = factory.create("FrameCutter", "frameSize", FRAME_SIZE, "hopSize",
                                            HOP_SIZE, "startFromZero", false);
    Algorithm* windowing =
        factory.create("Windowing", "type", "hann", "size", FRAME_SIZE, "normalized", false);
    Algorithm* spec = factory.create("Spectrum", "size", FRAME_SIZE);
    Algorithm* melBands = factory.create(
        "MelBands", "numberBands", NUM_BANDS, "sampleRate", (Real)STYLE_SR, "warpingFormula",
        "slaneyMel", "weighting", "linear", "normalize", "unit_tri", "inputSize",
        FRAME_SIZE / 2 + 1, "lowFrequencyBound", 0.0, "highFrequencyBound", (Real)(STYLE_SR / 2));
    Algorithm* shiftOp = factory.create("UnaryOperator", "type", "identity", "shift", (Real)1.0,
                                        "scale", (Real)10000.0);
    Algorithm* logOp = factory.create("UnaryOperator", "type", "log10");

    std::vector<float> current_frame;
    frameCutter->input("signal").set(audio);
    frameCutter->output("frame").set(current_frame);

    windowing->input("frame").set(current_frame);
    windowing->output("frame").set(windowed_frame);

    spec->input("frame").set(windowed_frame);
    spec->output("spectrum").set(spectrum);

    melBands->input("spectrum").set(spectrum);
    melBands->output("bands").set(mel_bands);

    std::vector<float> shifted_mel_bands;
    shiftOp->input("array").set(mel_bands);
    shiftOp->output("array").set(shifted_mel_bands);

    std::vector<float> log_mel_bands;
    logOp->input("array").set(shifted_mel_bands);
    logOp->output("array").set(log_mel_bands);

    while (true) {
      frameCutter->compute();
      if (current_frame.empty()) break;

      if (is_cancelled(cancel_flag)) {
        delete frameCutter;
        delete windowing;
        delete spec;
        delete melBands;
        delete shiftOp;
        delete logOp;
        result.error_code = 1;
        return result;
      }

      windowing->compute();
      spec->compute();
      melBands->compute();
      shiftOp->compute();
      logOp->compute();

      mel_frames.push_back(log_mel_bands);
    }

    delete frameCutter;
    delete windowing;
    delete spec;
    delete melBands;
    delete shiftOp;
    delete logOp;
  } catch (const std::exception& e) {
    LOGE("Mel spectrogram error: %s", e.what());
    result.error_code = 3;
    return result;
  }

  if ((int)mel_frames.size() < PATCH_FRAMES) {
    LOGE("Not enough frames for a patch: %zu < %d", mel_frames.size(), PATCH_FRAMES);
    result.error_code = 3;
    return result;
  }

  std::vector<std::vector<float> > patches;
  for (int start = 0; start + PATCH_FRAMES <= (int)mel_frames.size(); start += PATCH_FRAMES) {
    std::vector<float> patch;
    patch.reserve(PATCH_FRAMES * NUM_BANDS);
    for (int f = start; f < start + PATCH_FRAMES; f++) {
      patch.insert(patch.end(), mel_frames[f].begin(), mel_frames[f].end());
    }
    patches.push_back(std::move(patch));
  }

  if (is_cancelled(cancel_flag)) {
    result.error_code = 1;
    return result;
  }

  LOGI("Loading ONNX model: %s", model_path);

  OrtContext ctx;
  ctx.ort = OrtGetApiBase()->GetApi(ORT_API_VERSION);
  if (!ctx.ort) {
    LOGE("Failed to get ONNX Runtime API");
    result.error_code = 4;
    return result;
  }

  OrtStatus* status = ctx.ort->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "style_classifier", &ctx.env);
  if (status) {
    LOGE("CreateEnv failed: %s", ctx.ort->GetErrorMessage(status));
    ctx.ort->ReleaseStatus(status);
    result.error_code = 4;
    return result;
  }

  status = ctx.ort->CreateSessionOptions(&ctx.session_opts);
  if (status) {
    LOGE("CreateSessionOptions failed: %s", ctx.ort->GetErrorMessage(status));
    ctx.ort->ReleaseStatus(status);
    result.error_code = 4;
    return result;
  }

  status = ctx.ort->CreateSession(ctx.env, model_path, ctx.session_opts, &ctx.session);
  if (status) {
    LOGE("CreateSession failed: %s", ctx.ort->GetErrorMessage(status));
    ctx.ort->ReleaseStatus(status);
    result.error_code = 4;
    return result;
  }

  status = ctx.ort->GetAllocatorWithDefaultOptions(&ctx.allocator);
  if (status) {
    LOGE("GetAllocator failed: %s", ctx.ort->GetErrorMessage(status));
    ctx.ort->ReleaseStatus(status);
    result.error_code = 4;
    return result;
  }

  status = ctx.ort->SessionGetInputName(ctx.session, 0, ctx.allocator, &ctx.input_name);
  if (status) {
    LOGE("SessionGetInputName failed: %s", ctx.ort->GetErrorMessage(status));
    ctx.ort->ReleaseStatus(status);
    result.error_code = 4;
    return result;
  }
  status = ctx.ort->SessionGetOutputName(ctx.session, 0, ctx.allocator, &ctx.output_name);
  if (status) {
    LOGE("SessionGetOutputName failed: %s", ctx.ort->GetErrorMessage(status));
    ctx.ort->ReleaseStatus(status);
    result.error_code = 4;
    return result;
  }

  status = ctx.ort->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &ctx.mem_info);
  if (status) {
    LOGE("CreateCpuMemoryInfo failed: %s", ctx.ort->GetErrorMessage(status));
    ctx.ort->ReleaseStatus(status);
    result.error_code = 4;
    return result;
  }

  std::vector<float> avg_output(NUM_CLASSES, 0.0f);
  const int64_t input_shape[] = {1, PATCH_FRAMES, NUM_BANDS};

  for (size_t p = 0; p < patches.size(); p++) {
    if (is_cancelled(cancel_flag)) {
      result.error_code = 1;
      return result;
    }

    OrtValue* input_tensor = nullptr;
    status = ctx.ort->CreateTensorWithDataAsOrtValue(
        ctx.mem_info, patches[p].data(), patches[p].size() * sizeof(float), input_shape, 3,
        ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &input_tensor);
    if (status) {
      LOGE("CreateTensor failed for patch %zu: %s", p, ctx.ort->GetErrorMessage(status));
      ctx.ort->ReleaseStatus(status);
      result.error_code = 3;
      return result;
    }

    OrtValue* output_tensor = nullptr;
    const char* input_names[] = {ctx.input_name};
    const char* output_names[] = {ctx.output_name};

    status = ctx.ort->Run(ctx.session, nullptr, input_names, (const OrtValue* const*)&input_tensor,
                          1, output_names, 1, &output_tensor);
    if (status) {
      LOGE("Run failed for patch %zu: %s", p, ctx.ort->GetErrorMessage(status));
      ctx.ort->ReleaseStatus(status);
      ctx.ort->ReleaseValue(input_tensor);
      result.error_code = 3;
      return result;
    }

    float* output_data = nullptr;
    status = ctx.ort->GetTensorMutableData(output_tensor, (void**)&output_data);
    if (status) {
      LOGE("GetTensorMutableData failed: %s", ctx.ort->GetErrorMessage(status));
      ctx.ort->ReleaseStatus(status);
      ctx.ort->ReleaseValue(output_tensor);
      ctx.ort->ReleaseValue(input_tensor);
      result.error_code = 3;
      return result;
    }

    for (int c = 0; c < NUM_CLASSES; c++) {
      avg_output[c] += output_data[c];
    }

    ctx.ort->ReleaseValue(output_tensor);
    ctx.ort->ReleaseValue(input_tensor);
  }

  for (int c = 0; c < NUM_CLASSES; c++) {
    avg_output[c] /= (float)patches.size();
  }

  std::vector<int> indices(NUM_CLASSES);
  std::iota(indices.begin(), indices.end(), 0);
  std::partial_sort(indices.begin(), indices.begin() + STYLE_MAX_RESULTS, indices.end(),
                    [&avg_output](int a, int b) { return avg_output[a] > avg_output[b]; });

  result.count = STYLE_MAX_RESULTS;
  for (int i = 0; i < STYLE_MAX_RESULTS; i++) {
    result.indices[i] = indices[i];
    result.confidences[i] = avg_output[indices[i]];
  }
  result.error_code = 0;

  LOGI("Top style: idx=%d conf=%.3f", result.indices[0], result.confidences[0]);
  return result;
}

}  // extern "C"

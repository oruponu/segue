#include "analysis.h"

#include <essentia/algorithmfactory.h>

#include <algorithm>
#include <exception>
#include <memory>
#include <mutex>
#include <numeric>
#include <string>

#include "cancel.h"
#include "constants.h"
#include "essentia_lock.h"
#include "log.h"

namespace segue {
namespace {

using essentia::Real;
using essentia::standard::Algorithm;
using essentia::standard::AlgorithmFactory;

constexpr int kStyleSampleRate = 16000;
constexpr int kMelFrameSize = 512;
constexpr int kMelHopSize = 256;
constexpr int kMelBands = 96;
constexpr int kPatchFrames = 128;
constexpr int kStyleClasses = 400;

int keyToNote(const std::string& key) {
  static const char* kNames[] = {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"};
  static const char* kAltNames[] = {"", "Db", "", "D#", "", "", "Gb", "", "G#", "", "A#", "Cb"};
  for (int i = 0; i < 12; ++i) {
    if (key == kNames[i] || (kAltNames[i][0] != '\0' && key == kAltNames[i])) return i;
  }
  return -1;
}

// Log mel spectrogram (same pipeline as the legacy implementation). Sets cancelled and stops early
// when the token is cancelled
bool computeMelFrames(const std::vector<float>& mono, const SegueCancelToken* token,
                      std::vector<std::vector<float>>& melFrames, bool& cancelled) {
  AlgorithmFactory& factory = AlgorithmFactory::instance();

  std::vector<Real> resampled;
  std::unique_ptr<Algorithm> resample(factory.create("Resample", "inputSampleRate",
                                                     (Real)kSampleRate, "outputSampleRate",
                                                     (Real)kStyleSampleRate, "quality", 4));
  resample->input("signal").set(mono);
  resample->output("signal").set(resampled);
  resample->compute();
  if (isCancelled(token)) {
    cancelled = true;
    return true;
  }

  std::unique_ptr<Algorithm> frameCutter(factory.create(
      "FrameCutter", "frameSize", kMelFrameSize, "hopSize", kMelHopSize, "startFromZero", false));
  std::unique_ptr<Algorithm> windowing(
      factory.create("Windowing", "type", "hann", "size", kMelFrameSize, "normalized", false));
  std::unique_ptr<Algorithm> spectrum(factory.create("Spectrum", "size", kMelFrameSize));
  std::unique_ptr<Algorithm> melBands(
      factory.create("MelBands", "numberBands", kMelBands, "sampleRate", (Real)kStyleSampleRate,
                     "warpingFormula", "slaneyMel", "weighting", "linear", "normalize", "unit_tri",
                     "inputSize", kMelFrameSize / 2 + 1, "lowFrequencyBound", 0.0,
                     "highFrequencyBound", (Real)(kStyleSampleRate / 2)));
  std::unique_ptr<Algorithm> shift(factory.create("UnaryOperator", "type", "identity", "shift",
                                                  (Real)1.0, "scale", (Real)10000.0));
  std::unique_ptr<Algorithm> logOp(factory.create("UnaryOperator", "type", "log10"));

  std::vector<Real> frame, windowed, spec, bands, shifted, logBands;
  frameCutter->input("signal").set(resampled);
  frameCutter->output("frame").set(frame);
  windowing->input("frame").set(frame);
  windowing->output("frame").set(windowed);
  spectrum->input("frame").set(windowed);
  spectrum->output("spectrum").set(spec);
  melBands->input("spectrum").set(spec);
  melBands->output("bands").set(bands);
  shift->input("array").set(bands);
  shift->output("array").set(shifted);
  logOp->input("array").set(shifted);
  logOp->output("array").set(logBands);

  for (size_t count = 0;; ++count) {
    frameCutter->compute();
    if (frame.empty()) break;
    if (count % 256 == 0 && isCancelled(token)) {
      cancelled = true;
      return true;
    }
    windowing->compute();
    spectrum->compute();
    melBands->compute();
    shift->compute();
    logOp->compute();
    melFrames.push_back(logBands);
  }
  return true;
}

}  // namespace

SegueStatus analyzeRhythmAndKey(const std::vector<float>& mono, const SegueCancelToken* token,
                                SegueMusicResult& result) {
  std::lock_guard<std::mutex> lock(essentiaMutex());
  AlgorithmFactory& factory = AlgorithmFactory::instance();

  if (isCancelled(token)) return SEGUE_CANCELLED;
  try {
    Real bpm = 0;
    Real confidence = 0;
    std::vector<Real> ticks, estimates, bpmIntervals;
    std::unique_ptr<Algorithm> rhythm(factory.create("RhythmExtractor2013"));
    rhythm->input("signal").set(mono);
    rhythm->output("bpm").set(bpm);
    rhythm->output("ticks").set(ticks);
    rhythm->output("confidence").set(confidence);
    rhythm->output("estimates").set(estimates);
    rhythm->output("bpmIntervals").set(bpmIntervals);
    rhythm->compute();
    result.bpm = bpm;
    result.bpm_confidence = confidence;
  } catch (const std::exception& e) {
    SEGUE_LOGE("Rhythm analysis failed: %s", e.what());
    return SEGUE_ANALYSIS_FAILED;
  }

  if (isCancelled(token)) return SEGUE_CANCELLED;
  try {
    std::string key, scale;
    Real strength = 0;
    std::unique_ptr<Algorithm> keyExtractor(factory.create("KeyExtractor"));
    keyExtractor->input("audio").set(mono);
    keyExtractor->output("key").set(key);
    keyExtractor->output("scale").set(scale);
    keyExtractor->output("strength").set(strength);
    keyExtractor->compute();
    result.key_note = keyToNote(key);
    result.key_scale = scale == "minor" ? 1 : 0;
    result.key_confidence = strength;
  } catch (const std::exception& e) {
    SEGUE_LOGE("Key analysis failed: %s", e.what());
    return SEGUE_ANALYSIS_FAILED;
  }
  return SEGUE_OK;
}

SegueStatus classifyStyle(const std::vector<float>& mono, const SegueModels& models,
                          const SegueCancelToken* token, SegueMusicResult& result) {
  result.style_count = 0;
  if (isCancelled(token)) return SEGUE_CANCELLED;

  std::vector<std::vector<float>> melFrames;
  {
    std::lock_guard<std::mutex> lock(essentiaMutex());
    bool cancelled = false;
    try {
      computeMelFrames(mono, token, melFrames, cancelled);
    } catch (const std::exception& e) {
      SEGUE_LOGE("Mel spectrogram failed: %s", e.what());
      return SEGUE_ANALYSIS_FAILED;
    }
    if (cancelled) return SEGUE_CANCELLED;
  }
  // Essentia is not used from here on, so inference runs without the lock
  if (static_cast<int>(melFrames.size()) < kPatchFrames) return SEGUE_OK;

  const OrtApi* ort = models.ort;
  std::vector<float> average(kStyleClasses, 0.0f);
  std::vector<float> patch(static_cast<size_t>(kPatchFrames) * kMelBands);
  const int64_t shape[] = {1, kPatchFrames, kMelBands};
  const char* inputNames[] = {models.inputName.c_str()};
  const char* outputNames[] = {models.outputName.c_str()};
  int patchCount = 0;

  for (size_t start = 0; start + kPatchFrames <= melFrames.size(); start += kPatchFrames) {
    if (isCancelled(token)) return SEGUE_CANCELLED;
    for (int f = 0; f < kPatchFrames; ++f) {
      std::copy(melFrames[start + f].begin(), melFrames[start + f].end(),
                patch.begin() + static_cast<size_t>(f) * kMelBands);
    }

    OrtValue* input = nullptr;
    OrtStatus* status = ort->CreateTensorWithDataAsOrtValue(
        models.memoryInfo, patch.data(), patch.size() * sizeof(float), shape, 3,
        ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &input);
    if (status != nullptr) {
      SEGUE_LOGE("CreateTensor failed: %s", ort->GetErrorMessage(status));
      ort->ReleaseStatus(status);
      return SEGUE_ANALYSIS_FAILED;
    }
    OrtValue* output = nullptr;
    status = ort->Run(models.session, nullptr, inputNames, &input, 1, outputNames, 1, &output);
    ort->ReleaseValue(input);
    if (status != nullptr) {
      SEGUE_LOGE("Run failed: %s", ort->GetErrorMessage(status));
      ort->ReleaseStatus(status);
      return SEGUE_ANALYSIS_FAILED;
    }
    // The contract was checked at load time, but check that this output is also float × 400
    // before reading it
    OrtTensorTypeAndShapeInfo* outputInfo = nullptr;
    ONNXTensorElementDataType outputType = ONNX_TENSOR_ELEMENT_DATA_TYPE_UNDEFINED;
    size_t elementCount = 0;
    status = ort->GetTensorTypeAndShape(output, &outputInfo);
    if (status == nullptr) status = ort->GetTensorElementType(outputInfo, &outputType);
    if (status == nullptr) status = ort->GetTensorShapeElementCount(outputInfo, &elementCount);
    if (outputInfo != nullptr) ort->ReleaseTensorTypeAndShapeInfo(outputInfo);
    if (status != nullptr || outputType != ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT ||
        elementCount != static_cast<size_t>(kStyleClasses)) {
      if (status != nullptr) {
        SEGUE_LOGE("Output shape query failed: %s", ort->GetErrorMessage(status));
        ort->ReleaseStatus(status);
      } else {
        SEGUE_LOGE("Unexpected output: type=%d elements=%zu", static_cast<int>(outputType),
                   elementCount);
      }
      ort->ReleaseValue(output);
      return SEGUE_ANALYSIS_FAILED;
    }

    float* data = nullptr;
    status = ort->GetTensorMutableData(output, reinterpret_cast<void**>(&data));
    if (status != nullptr) {
      SEGUE_LOGE("GetTensorMutableData failed: %s", ort->GetErrorMessage(status));
      ort->ReleaseStatus(status);
      ort->ReleaseValue(output);
      return SEGUE_ANALYSIS_FAILED;
    }
    for (int c = 0; c < kStyleClasses; ++c) average[c] += data[c];
    ort->ReleaseValue(output);
    ++patchCount;
  }

  for (float& v : average) v /= static_cast<float>(patchCount);
  std::vector<int> indices(kStyleClasses);
  std::iota(indices.begin(), indices.end(), 0);
  std::partial_sort(indices.begin(), indices.begin() + SEGUE_STYLE_MAX, indices.end(),
                    [&average](int a, int b) { return average[a] > average[b]; });
  result.style_count = SEGUE_STYLE_MAX;
  for (int i = 0; i < SEGUE_STYLE_MAX; ++i) {
    result.style_indices[i] = indices[i];
    result.style_confidences[i] = average[indices[i]];
  }
  return SEGUE_OK;
}

}  // namespace segue

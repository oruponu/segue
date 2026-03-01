#include "spectrum_analyzer.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <vector>

#include "audio_decode.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "SpectrumAnalyzer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...)
#define LOGE(...)
#endif

#include <essentia/algorithmfactory.h>
#include <essentia/essentiamath.h>

using namespace essentia;
using namespace essentia::standard;

static const int SPECTRUM_SR = 44100;

static bool is_cancelled(EssentiaCancelFlag* flag) {
  return essentia_cancel_flag_is_set(flag) != 0;
}

extern "C" {

SpectrumData* essentia_compute_spectrum(const char* path, int32_t num_bands, int32_t frame_size,
                                        int32_t hop_size, EssentiaCancelFlag* cancel_flag) {
  SpectrumData* data = (SpectrumData*)malloc(sizeof(SpectrumData));
  if (!data) return nullptr;

  data->bands = nullptr;
  data->num_frames = 0;
  data->num_bands = num_bands;
  data->hop_duration = (float)hop_size / (float)SPECTRUM_SR;
  data->error_code = 0;

  LOGI("Computing spectrum: path=%s, bands=%d, frameSize=%d, hopSize=%d", path, num_bands,
       frame_size, hop_size);

  std::vector<float> audio;
  int decode_ret = decode_audio(path, audio, SPECTRUM_SR, cancel_flag);
  if (decode_ret < 0) {
    data->error_code = 2;
    return data;
  }
  if (decode_ret == 1 || is_cancelled(cancel_flag)) {
    data->error_code = 1;
    return data;
  }

  if (audio.empty()) {
    LOGE("No audio samples decoded");
    data->error_code = 2;
    return data;
  }

  LOGI("Decoded %zu samples (%.1f seconds)", audio.size(), (float)audio.size() / SPECTRUM_SR);

  if (is_cancelled(cancel_flag)) {
    data->error_code = 1;
    return data;
  }

  AlgorithmFactory& factory = AlgorithmFactory::instance();

  // 対数等間隔のバンド境界を FFT ビンのインデックスに変換（20 Hz – 20 kHz）
  int spectrum_size = frame_size / 2 + 1;
  std::vector<float> bin_edges(num_bands + 1);
  for (int i = 0; i <= num_bands; i++) {
    float freq = 20.0f * powf(20000.0f / 20.0f, (float)i / num_bands);
    bin_edges[i] = freq * frame_size / (float)SPECTRUM_SR;
  }

  // コヒーレントゲイン（0.5）を正規化に反映
  float norm_sq = (frame_size * 0.25f) * (frame_size * 0.25f);

  // バンド補正：帯域幅正規化（1 kHz 基準）+ スロープ補正（dB/oct）
  static constexpr float kSlopeDBoct = 4.5f;
  float band_ratio = powf(20000.0f / 20.0f, 1.0f / num_bands);
  float ref_bw = 1000.0f * (band_ratio - 1.0f) * frame_size / (float)SPECTRUM_SR;
  std::vector<float> band_correction(num_bands);
  for (int b = 0; b < num_bands; b++) {
    float bw = bin_edges[b + 1] - bin_edges[b];
    float center_freq = 20.0f * powf(1000.0f, ((float)b + 0.5f) / num_bands);
    band_correction[b] = 10.0f * log10f(ref_bw / bw) + kSlopeDBoct * log2f(center_freq / 1000.0f);
  }

  std::vector<std::vector<float> > all_frames;

  try {
    std::vector<float> current_frame;
    std::vector<float> windowed_frame;
    std::vector<float> spectrum_out;

    Algorithm* frameCutter = factory.create("FrameCutter", "frameSize", frame_size, "hopSize",
                                            hop_size, "startFromZero", false);
    Algorithm* windowing =
        factory.create("Windowing", "type", "hann", "size", frame_size, "normalized", false);
    Algorithm* spec = factory.create("Spectrum", "size", frame_size);

    frameCutter->input("signal").set(audio);
    frameCutter->output("frame").set(current_frame);

    windowing->input("frame").set(current_frame);
    windowing->output("frame").set(windowed_frame);

    spec->input("frame").set(windowed_frame);
    spec->output("spectrum").set(spectrum_out);

    int frame_count = 0;
    while (true) {
      frameCutter->compute();
      if (current_frame.empty()) break;

      if (frame_count % 1000 == 0 && is_cancelled(cancel_flag)) {
        delete frameCutter;
        delete windowing;
        delete spec;
        data->error_code = 1;
        return data;
      }

      windowing->compute();
      spec->compute();

      std::vector<float> bands(num_bands);
      for (int b = 0; b < num_bands; b++) {
        float sum = 0;
        int k_start = (int)bin_edges[b];
        int k_end = (int)std::ceil(bin_edges[b + 1]);
        if (k_end > spectrum_size) k_end = spectrum_size;

        for (int k = std::max(0, k_start); k < k_end; k++) {
          float w_low = std::max((float)k, bin_edges[b]);
          float w_high = std::min((float)(k + 1), bin_edges[b + 1]);
          float weight = w_high - w_low;
          if (weight > 0) {
            sum += spectrum_out[k] * spectrum_out[k] * weight;
          }
        }

        bands[b] = (sum > 1e-14f) ? 10.0f * log10f(sum / norm_sq) + band_correction[b] : -100.0f;
      }

      all_frames.push_back(bands);
      frame_count++;
    }

    delete frameCutter;
    delete windowing;
    delete spec;
  } catch (const std::exception& e) {
    LOGE("Spectrum analysis error: %s", e.what());
    data->error_code = 3;
    return data;
  }

  if (all_frames.empty()) {
    LOGE("No frames computed");
    data->error_code = 3;
    return data;
  }

  if (is_cancelled(cancel_flag)) {
    data->error_code = 1;
    return data;
  }

  int total_frames = (int)all_frames.size();
  data->num_frames = total_frames;
  data->bands = (float*)malloc(sizeof(float) * total_frames * num_bands);
  if (!data->bands) {
    LOGE("Failed to allocate bands array");
    data->error_code = 3;
    return data;
  }

  for (int f = 0; f < total_frames; f++) {
    memcpy(data->bands + f * num_bands, all_frames[f].data(), sizeof(float) * num_bands);
  }

  LOGI("Spectrum computed: %d frames x %d bands", total_frames, num_bands);
  return data;
}

void essentia_free_spectrum(SpectrumData* data) {
  if (data) {
    free(data->bands);
    free(data);
  }
}

}  // extern "C"

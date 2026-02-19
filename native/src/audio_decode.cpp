#include "audio_decode.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "AudioDecode"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...)
#define LOGE(...)
#endif

#include <essentia/algorithmfactory.h>

using namespace essentia;
using namespace essentia::standard;

static bool is_cancelled(EssentiaCancelFlag* flag) {
  return essentia_cancel_flag_is_set(flag) != 0;
}

int decode_audio(const char* path, std::vector<float>& out_samples, int target_sr,
                 EssentiaCancelFlag* cancel_flag) {
  if (is_cancelled(cancel_flag)) {
    return 1;
  }

  try {
    AlgorithmFactory& factory = AlgorithmFactory::instance();
    Algorithm* loader = factory.create("MonoLoader", "filename", std::string(path), "sampleRate",
                                       (Real)target_sr, "resampleQuality", 4);
    loader->output("audio").set(out_samples);
    loader->compute();
    delete loader;
  } catch (const std::exception& e) {
    LOGE("MonoLoader failed: %s", e.what());
    return -1;
  }

  if (out_samples.empty()) {
    LOGE("No audio samples decoded");
    return -1;
  }

  LOGI("Decoded %zu samples (%.1f seconds) at %d Hz", out_samples.size(),
       (float)out_samples.size() / target_sr, target_sr);

  return is_cancelled(cancel_flag) ? 1 : 0;
}

#include "essentia_bridge.h"

#include <atomic>
#include <string>
#include <vector>

#include "audio_decode.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "EssentiaBridge"
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

struct EssentiaCancelFlag {
  std::atomic<bool> cancelled{false};
};

static bool is_cancelled(EssentiaCancelFlag* flag) {
  return flag && flag->cancelled.load(std::memory_order_acquire);
}

static const int TARGET_SAMPLE_RATE = 44100;

extern "C" {

EssentiaCancelFlag* essentia_cancel_flag_create(void) { return new EssentiaCancelFlag(); }

void essentia_cancel_flag_set(EssentiaCancelFlag* flag) {
  if (flag) {
    flag->cancelled.store(true, std::memory_order_release);
  }
}

int essentia_cancel_flag_is_set(EssentiaCancelFlag* flag) { return is_cancelled(flag) ? 1 : 0; }

void essentia_cancel_flag_destroy(EssentiaCancelFlag* flag) { delete flag; }

void essentia_init(void) { essentia::init(); }

void essentia_shutdown(void) { essentia::shutdown(); }

EssentiaResult essentia_analyze(const char* path, EssentiaCancelFlag* cancel_flag) {
  EssentiaResult result = {};
  result.key_note = -1;
  result.key_scale = -1;

  LOGI("Loading: %s", path);
  std::vector<float> audio;
  int decode_ret = decode_audio(path, audio, TARGET_SAMPLE_RATE, cancel_flag);
  if (decode_ret < 0) {
    result.error_code = 2;
    return result;
  }
  if (decode_ret == 1 || is_cancelled(cancel_flag)) {
    result.error_code = 1;
    return result;
  }
  LOGI("Decoded %zu samples (%.1f seconds)", audio.size(),
       (float)audio.size() / TARGET_SAMPLE_RATE);

  if (audio.empty()) {
    LOGE("No audio samples decoded");
    result.error_code = 2;
    return result;
  }

  AlgorithmFactory& factory = AlgorithmFactory::instance();

  if (is_cancelled(cancel_flag)) {
    result.error_code = 1;
    return result;
  }

  try {
    Real bpm = 0;
    std::vector<Real> ticks;
    Real confidence = 0;
    std::vector<Real> estimates;
    std::vector<Real> bpmIntervals;

    Algorithm* rhythm = factory.create("RhythmExtractor2013");
    rhythm->input("signal").set(audio);
    rhythm->output("bpm").set(bpm);
    rhythm->output("ticks").set(ticks);
    rhythm->output("confidence").set(confidence);
    rhythm->output("estimates").set(estimates);
    rhythm->output("bpmIntervals").set(bpmIntervals);
    rhythm->compute();
    delete rhythm;

    result.bpm = bpm;
    result.bpm_confidence = confidence;
    LOGI("BPM: %.1f (confidence: %.2f)", bpm, confidence);
  } catch (const std::exception& e) {
    LOGE("Rhythm analysis error: %s", e.what());
    result.error_code = 3;
    return result;
  }

  if (is_cancelled(cancel_flag)) {
    result.error_code = 1;
    return result;
  }

  try {
    std::string key_str;
    std::string scale_str;
    Real strength = 0;

    Algorithm* keyExtractor = factory.create("KeyExtractor");
    keyExtractor->input("audio").set(audio);
    keyExtractor->output("key").set(key_str);
    keyExtractor->output("scale").set(scale_str);
    keyExtractor->output("strength").set(strength);
    keyExtractor->compute();
    delete keyExtractor;

    static const char* note_names[] = {"C",  "C#", "D",  "Eb", "E",  "F",
                                       "F#", "G",  "Ab", "A",  "Bb", "B"};
    static const char* note_names_alt[] = {"",   "Db", "",   "D#", "",   "",
                                           "Gb", "",   "G#", "",   "A#", "Cb"};

    result.key_note = -1;
    for (int i = 0; i < 12; i++) {
      if (key_str == note_names[i] ||
          (note_names_alt[i][0] != '\0' && key_str == note_names_alt[i])) {
        result.key_note = static_cast<int8_t>(i);
        break;
      }
    }

    result.key_scale = (scale_str == "minor") ? 1 : 0;
    result.key_confidence = strength;
    LOGI("Key: %s %s (strength: %.2f)", key_str.c_str(), scale_str.c_str(), strength);
  } catch (const std::exception& e) {
    LOGE("Key analysis error: %s", e.what());
    result.error_code = 3;
    return result;
  }

  if (is_cancelled(cancel_flag)) {
    result.error_code = 1;
    return result;
  }

  result.error_code = 0;
  return result;
}

}  // extern "C"

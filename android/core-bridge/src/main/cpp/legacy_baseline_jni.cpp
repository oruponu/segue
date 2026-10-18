// JNI that calls the legacy implementation (core/legacy, the Flutter app's API) to generate
// baseline data.
#include <jni.h>

#include <cstddef>
#include <vector>

#include "essentia_bridge.h"
#include "spectrum_analyzer.h"
#include "style_classifier.h"

namespace {

class Utf8 {
 public:
  Utf8(JNIEnv* env, jstring s) : env_(env), s_(s), chars_(env->GetStringUTFChars(s, nullptr)) {}
  ~Utf8() {
    if (chars_ != nullptr) env_->ReleaseStringUTFChars(s_, chars_);
  }
  const char* get() const { return chars_; }

 private:
  JNIEnv* env_;
  jstring s_;
  const char* chars_;
};

jfloatArray toArray(JNIEnv* env, const std::vector<float>& values) {
  jfloatArray array = env->NewFloatArray(static_cast<jsize>(values.size()));
  env->SetFloatArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
  return array;
}

}  // namespace

#define LEGACY_JNI(ret, name) \
  extern "C" JNIEXPORT ret JNICALL Java_com_oruponu_segue_core_LegacyBaseline_##name

LEGACY_JNI(void, nativeInit)(JNIEnv*, jclass) { essentia_init(); }

LEGACY_JNI(jfloatArray, nativeAnalyze)(JNIEnv* env, jclass, jstring path) {
  Utf8 p(env, path);
  const EssentiaResult r = essentia_analyze(p.get(), nullptr);
  return toArray(
      env, {r.bpm, r.bpm_confidence, static_cast<float>(r.key_note),
            static_cast<float>(r.key_scale), r.key_confidence, static_cast<float>(r.error_code)});
}

LEGACY_JNI(jfloatArray, nativeStyle)(JNIEnv* env, jclass, jstring path, jstring modelPath) {
  Utf8 p(env, path);
  Utf8 m(env, modelPath);
  const StyleResult r = essentia_classify_style(p.get(), m.get(), nullptr);
  std::vector<float> v{static_cast<float>(r.error_code), static_cast<float>(r.count)};
  for (int i = 0; i < STYLE_MAX_RESULTS; ++i) v.push_back(static_cast<float>(r.indices[i]));
  for (int i = 0; i < STYLE_MAX_RESULTS; ++i) v.push_back(r.confidences[i]);
  return toArray(env, v);
}

LEGACY_JNI(jfloatArray, nativeSpectrum)(JNIEnv* env, jclass, jstring path) {
  Utf8 p(env, path);
  SpectrumData* d = essentia_compute_spectrum(p.get(), 32, 4096, 1024, nullptr);
  std::vector<float> v{static_cast<float>(d != nullptr ? d->error_code : 3),
                       static_cast<float>(d != nullptr ? d->num_frames : 0),
                       static_cast<float>(d != nullptr ? d->num_bands : 0)};
  if (d != nullptr && d->error_code == 0) {
    v.insert(v.end(), d->bands, d->bands + static_cast<size_t>(d->num_frames) * d->num_bands);
  }
  essentia_free_spectrum(d);
  return toArray(env, v);
}

LEGACY_JNI(jfloatArray, nativePeaks)(JNIEnv* env, jclass, jstring path) {
  Utf8 p(env, path);
  StereoPeakData* d = essentia_compute_stereo_peaks(p.get(), 1024, nullptr);
  const int frames = (d != nullptr && d->error_code == 0) ? d->num_frames : 0;
  std::vector<float> v{static_cast<float>(d != nullptr ? d->error_code : 3),
                       static_cast<float>(frames)};
  for (int i = 0; i < frames; ++i) v.push_back(d->left_peaks[i]);
  for (int i = 0; i < frames; ++i) v.push_back(d->right_peaks[i]);
  for (int i = 0; i < frames; ++i) v.push_back(static_cast<float>(d->clip_flags[i]));
  essentia_free_stereo_peaks(d);
  return toArray(env, v);
}

// JNI shim that exposes the Segue Core C API to Kotlin (com.oruponu.segue.core.NativeLib).
// It only converts types and holds no logic. Handles are C pointers cast to jlong (0 is NULL).
#include <jni.h>

#include <algorithm>
#include <cstdint>

#include "segue/segue_core.h"

namespace {

template <typename T>
T* fromHandle(jlong handle) {
  return reinterpret_cast<T*>(static_cast<intptr_t>(handle));
}

jlong toHandle(const void* ptr) { return static_cast<jlong>(reinterpret_cast<intptr_t>(ptr)); }

void setHandle(JNIEnv* env, jlongArray out, const void* ptr) {
  const jlong value = toHandle(ptr);
  env->SetLongArrayRegion(out, 0, 1, &value);
}

// GetStringUTFChars returns modified UTF-8, so paths with characters outside the BMP (emoji etc.)
// are not passed correctly. Callers pass only /proc/self/fd/N or paths in the app's cache dir.
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

}  // namespace

#define SEGUE_JNI(ret, name) \
  extern "C" JNIEXPORT ret JNICALL Java_com_oruponu_segue_core_NativeLib_##name

SEGUE_JNI(jint, nativeInit)(JNIEnv*, jclass) { return segue_init(); }

SEGUE_JNI(jint, nativeAnalysisVersion)(JNIEnv*, jclass) { return segue_analysis_version(); }

SEGUE_JNI(jlong, nativeCancelCreate)(JNIEnv*, jclass) { return toHandle(segue_cancel_create()); }

SEGUE_JNI(void, nativeCancelRequest)(JNIEnv*, jclass, jlong token) {
  segue_cancel_request(fromHandle<SegueCancelToken>(token));
}

SEGUE_JNI(void, nativeCancelDestroy)(JNIEnv*, jclass, jlong token) {
  segue_cancel_destroy(fromHandle<SegueCancelToken>(token));
}

SEGUE_JNI(jint, nativeDecodeFile)(JNIEnv* env, jclass, jstring path, jlong token, jlongArray out) {
  Utf8 p(env, path);
  if (p.get() == nullptr) return SEGUE_INVALID_ARGUMENT;
  SegueAudio* audio = nullptr;
  const SegueStatus status =
      segue_audio_decode_file(p.get(), fromHandle<SegueCancelToken>(token), &audio);
  setHandle(env, out, audio);
  return status;
}

SEGUE_JNI(void, nativeAudioFree)(JNIEnv*, jclass, jlong audio) {
  segue_audio_free(fromHandle<SegueAudio>(audio));
}

SEGUE_JNI(jint, nativeVisualsWrite)(JNIEnv* env, jclass, jlong audio, jstring path) {
  Utf8 p(env, path);
  if (p.get() == nullptr) return SEGUE_INVALID_ARGUMENT;
  return segue_visuals_write(fromHandle<const SegueAudio>(audio), p.get());
}

SEGUE_JNI(jint, nativeVisualsRead)(JNIEnv* env, jclass, jstring path, jlongArray out) {
  Utf8 p(env, path);
  if (p.get() == nullptr) return SEGUE_INVALID_ARGUMENT;
  SegueVisuals* visuals = nullptr;
  const SegueStatus status = segue_visuals_read(p.get(), &visuals);
  setHandle(env, out, visuals);
  return status;
}

SEGUE_JNI(void, nativeVisualsFree)(JNIEnv*, jclass, jlong visuals) {
  segue_visuals_free(fromHandle<SegueVisuals>(visuals));
}

SEGUE_JNI(void, nativeVisualsInfo)(JNIEnv* env, jclass, jlong handle, jintArray out) {
  const SegueVisuals* v = fromHandle<const SegueVisuals>(handle);
  const jint info[6] = {segue_visuals_sample_rate(v),   segue_visuals_hop(v),
                        segue_visuals_frame_count(v),   segue_visuals_band_count(v),
                        segue_visuals_waveform_rate(v), segue_visuals_waveform_count(v)};
  env->SetIntArrayRegion(out, 0, 6, info);
}

SEGUE_JNI(void, nativeVisualsCopy)(JNIEnv* env, jclass, jlong handle, jint kind, jbyteArray dst) {
  const SegueVisuals* v = fromHandle<const SegueVisuals>(handle);
  const jsize frames = segue_visuals_frame_count(v);
  const void* src = nullptr;
  jsize size = 0;
  switch (kind) {
    case 0:
      src = segue_visuals_spectrum(v);
      size = frames * segue_visuals_band_count(v);
      break;
    case 1:
      src = segue_visuals_peak_left(v);
      size = frames;
      break;
    case 2:
      src = segue_visuals_peak_right(v);
      size = frames;
      break;
    case 3:
      src = segue_visuals_clip_bits(v);
      size = (frames + 7) / 8;
      break;
    case 4:
      src = segue_visuals_waveform(v);
      size = segue_visuals_waveform_count(v) * 2;
      break;
    default:
      return;
  }
  const jsize n = std::min(size, env->GetArrayLength(dst));
  if (src != nullptr && n > 0) env->SetByteArrayRegion(dst, 0, n, static_cast<const jbyte*>(src));
}

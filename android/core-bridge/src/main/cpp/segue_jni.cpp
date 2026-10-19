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

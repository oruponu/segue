#pragma once

#ifdef __ANDROID__
#include <android/log.h>
#define SEGUE_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "SegueCore", __VA_ARGS__)
#define SEGUE_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "SegueCore", __VA_ARGS__)
#else
#define SEGUE_LOGI(...)
#define SEGUE_LOGE(...)
#endif

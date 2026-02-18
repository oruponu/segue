#ifndef STYLE_CLASSIFIER_H
#define STYLE_CLASSIFIER_H

#include "essentia_bridge.h"

#ifdef __cplusplus
extern "C" {
#endif

#define STYLE_MAX_RESULTS 5

typedef struct {
  int32_t count;                       // 0..STYLE_MAX_RESULTS
  int32_t indices[STYLE_MAX_RESULTS];  // label indices (0..399)
  float confidences[STYLE_MAX_RESULTS];
  int32_t error_code;  // 0=success, 1=cancelled, 2=decode error, 3=analysis error, 4=model error
} StyleResult;

StyleResult essentia_classify_style(const char* audio_path, const char* model_path,
                                    EssentiaCancelFlag* cancel_flag);

#ifdef __cplusplus
}
#endif

#endif  // STYLE_CLASSIFIER_H

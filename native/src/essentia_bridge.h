#ifndef ESSENTIA_BRIDGE_H
#define ESSENTIA_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  float bpm;
  float bpm_confidence;
  int8_t key_note;   // 0-11 (C=0...B=11), -1 = unknown
  int8_t key_scale;  // 0=major, 1=minor
  float key_confidence;
  int32_t error_code;  // 0=success, 1=cancelled, 2=decode error, 3=analysis error
} EssentiaResult;

typedef struct EssentiaCancelFlag EssentiaCancelFlag;

EssentiaCancelFlag* essentia_cancel_flag_create(void);
void essentia_cancel_flag_set(EssentiaCancelFlag* flag);
int essentia_cancel_flag_is_set(EssentiaCancelFlag* flag);
void essentia_cancel_flag_destroy(EssentiaCancelFlag* flag);

void essentia_init(void);
void essentia_shutdown(void);

EssentiaResult essentia_analyze(const char* path, EssentiaCancelFlag* cancel_flag);

#ifdef __cplusplus
}
#endif

#endif  // ESSENTIA_BRIDGE_H

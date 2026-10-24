/*
 * Public C API of Segue Core. The only boundary with the apps (Android through JNI, iOS later).
 * The visuals cache format, key notation and style labels are described in core/spec/.
 */
#ifndef SEGUE_CORE_H
#define SEGUE_CORE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
  SEGUE_OK = 0,
  SEGUE_CANCELLED = 1,
  SEGUE_DECODE_FAILED = 2,
  SEGUE_ANALYSIS_FAILED = 3,
  SEGUE_MODEL_LOAD_FAILED = 4,
  SEGUE_IO_FAILED = 5,
  SEGUE_INVALID_ARGUMENT = 6
} SegueStatus;

#define SEGUE_STYLE_MAX 5

typedef struct SegueCancelToken SegueCancelToken;
typedef struct SegueAudio SegueAudio;
typedef struct SegueVisuals SegueVisuals;
typedef struct SegueModels SegueModels;

typedef struct {
  float bpm;
  float bpm_confidence;
  int32_t key_note;  /* 0-11 (C=0 ... B=11), -1 if unknown */
  int32_t key_scale; /* 0=major, 1=minor, -1 if unknown */
  float key_confidence;
  int32_t style_count; /* 0..SEGUE_STYLE_MAX */
  int32_t style_indices[SEGUE_STYLE_MAX];
  float style_confidences[SEGUE_STYLE_MAX];
} SegueMusicResult;

/* Idempotent. The other functions also call it internally when needed */
SegueStatus segue_init(void);
/* Bumped whenever the analysis or visuals output changes */
int32_t segue_analysis_version(void);

/*
 * Cancel token. request may be called from another thread.
 * Call destroy only after the calls that use the token have returned.
 */
SegueCancelToken* segue_cancel_create(void);
void segue_cancel_request(SegueCancelToken* token);
void segue_cancel_destroy(SegueCancelToken* token);

/* (1) Decoding (mono 44.1kHz PCM and the visuals data). token may be NULL */
SegueStatus segue_audio_decode_file(const char* path, SegueCancelToken* token, SegueAudio** out);
void segue_audio_free(SegueAudio* audio);

/* Reading and writing the visuals cache (core/spec/visuals-format.md) */
SegueStatus segue_visuals_write(const SegueAudio* audio, const char* cache_path);
SegueStatus segue_visuals_read(const char* cache_path, SegueVisuals** out);
void segue_visuals_free(SegueVisuals* visuals);

int32_t segue_visuals_sample_rate(const SegueVisuals* visuals);
int32_t segue_visuals_hop(const SegueVisuals* visuals);
int32_t segue_visuals_frame_count(const SegueVisuals* visuals);
int32_t segue_visuals_band_count(const SegueVisuals* visuals);
const uint8_t* segue_visuals_spectrum(const SegueVisuals* visuals);  /* frame_count × band_count */
const uint8_t* segue_visuals_peak_left(const SegueVisuals* visuals); /* frame_count */
const uint8_t* segue_visuals_peak_right(const SegueVisuals* visuals); /* frame_count */
const uint8_t* segue_visuals_clip_bits(const SegueVisuals* visuals);  /* (frame_count + 7) / 8 */
int32_t segue_visuals_waveform_rate(const SegueVisuals* visuals);
int32_t segue_visuals_waveform_count(const SegueVisuals* visuals);
const int8_t* segue_visuals_waveform(
    const SegueVisuals* visuals); /* waveform_count × 2 (min, max) */

/* (2) Music analysis. If models is NULL, style classification is skipped and style_count = 0 */
SegueStatus segue_models_load(const char* effnet_path, SegueModels** out);
void segue_models_free(SegueModels* models);
SegueStatus segue_analyze(const SegueAudio* audio, const SegueModels* models,
                          SegueCancelToken* token, SegueMusicResult* out);

#ifdef __cplusplus
}
#endif

#endif /* SEGUE_CORE_H */

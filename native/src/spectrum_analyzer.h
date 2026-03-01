#ifndef SPECTRUM_ANALYZER_H
#define SPECTRUM_ANALYZER_H

#include "essentia_bridge.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  float* bands;  // numFrames * numBands flat array (heap-allocated)
  int32_t num_frames;
  int32_t num_bands;
  float hop_duration;  // hop_size / sample_rate (seconds)
  int32_t error_code;  // 0=success, 1=cancelled, 2=decode error, 3=analysis error
} SpectrumData;

SpectrumData* essentia_compute_spectrum(const char* path, int32_t num_bands, int32_t frame_size,
                                        int32_t hop_size, EssentiaCancelFlag* cancel_flag);

void essentia_free_spectrum(SpectrumData* data);

typedef struct {
  float* left_peaks;    // numFrames dB values (heap-allocated)
  float* right_peaks;   // numFrames dB values (heap-allocated)
  uint8_t* clip_flags;  // numFrames flags: bit0=left clipped, bit1=right clipped
  int32_t num_frames;
  float hop_duration;  // hop_size / sample_rate (seconds)
  int32_t error_code;  // 0=success, 1=cancelled, 2=decode error, 3=analysis error
} StereoPeakData;

StereoPeakData* essentia_compute_stereo_peaks(const char* path, int32_t frame_size,
                                              int32_t hop_size, EssentiaCancelFlag* cancel_flag);

void essentia_free_stereo_peaks(StereoPeakData* data);

#ifdef __cplusplus
}
#endif

#endif  // SPECTRUM_ANALYZER_H

#pragma once

#include <cmath>
#include <cstdint>
#include <vector>

#include "constants.h"

namespace segue {

struct VisualsData {
  uint32_t frameCount = 0;
  uint32_t waveformCount = 0;
  std::vector<uint8_t> spectrum;   // frameCount × kBandCount
  std::vector<uint8_t> peakLeft;   // frameCount
  std::vector<uint8_t> peakRight;  // frameCount
  std::vector<uint8_t> clipBits;   // (frameCount + 7) / 8. Frame i is bit (i % 8) of byte i / 8
  std::vector<int8_t> waveform;    // waveformCount × 2 (min, then max)
};

// Maps [-50, 0] dB to 0..255. NaN is treated as -50dB
inline uint8_t quantizeDb(float db) {
  const float clamped = std::fmin(std::fmax(db, kDbFloor), 0.0f);
  return static_cast<uint8_t>(std::lround((clamped - kDbFloor) * 255.0f / -kDbFloor));
}

inline int8_t quantizeAmplitude(float x) {
  const float clamped = std::fmin(std::fmax(x, -1.0f), 1.0f);
  return static_cast<int8_t>(std::lround(clamped * 127.0f));
}

}  // namespace segue

struct SegueVisuals {
  segue::VisualsData data;
};

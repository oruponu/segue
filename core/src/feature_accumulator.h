#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

#include "decoder.h"
#include "visuals_data.h"

namespace segue {

// Takes 44.1kHz stereo and builds the mono PCM, peaks (hop 1024), clips and waveform (441 samples
// per point).
// The constructor reserves the mono PCM vector up to the limit (30 minutes, about 317MB) so it is
// never reallocated. Anonymous memory uses physical memory only for the pages that are written, so
// physical memory use is proportional to the track length. Because there is no reallocation, the
// old and new buffers never coexist and double the usage
class FeatureAccumulator final : public StereoSink {
 public:
  FeatureAccumulator();

  bool onFrames(const float* interleaved, size_t frames) override;

  // Call exactly once after decoding. Returns false when there are no samples
  bool finish(std::vector<float>& mono, VisualsData& visuals);

 private:
  void closePeakFrame();
  void closeWaveformPoint();

  std::vector<float> mono_;
  std::vector<uint8_t> peakLeft_;
  std::vector<uint8_t> peakRight_;
  std::vector<uint8_t> clipFlags_;
  std::vector<int8_t> waveform_;
  float maxLeft_ = 0.0f;
  float maxRight_ = 0.0f;
  bool clipped_ = false;
  int inPeakFrame_ = 0;
  float waveMin_ = 0.0f;
  float waveMax_ = 0.0f;
  int inWavePoint_ = 0;
};

}  // namespace segue

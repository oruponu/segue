#include "feature_accumulator.h"

#include <cmath>

#include "constants.h"
#include "log.h"

namespace segue {
namespace {

// Silence is -100dB as in the legacy implementation (it quantizes to 0)
float toDb(float amplitude) { return amplitude > 1e-7f ? 20.0f * std::log10(amplitude) : -100.0f; }

}  // namespace

FeatureAccumulator::FeatureAccumulator() { mono_.reserve(static_cast<size_t>(kMaxDecodeSamples)); }

bool FeatureAccumulator::onFrames(const float* s, size_t frames) {
  // Enforced here whether or not the container reports a duration (this also keeps mono_ within the
  // reserved capacity)
  if (static_cast<int64_t>(mono_.size() + frames) > kMaxDecodeSamples) {
    SEGUE_LOGE("Decoded audio exceeds %lld samples", static_cast<long long>(kMaxDecodeSamples));
    return false;
  }
  for (size_t i = 0; i < frames; ++i) {
    const float left = s[2 * i];
    const float right = s[2 * i + 1];
    const float mono = 0.5f * (left + right);
    mono_.push_back(mono);

    const float absLeft = std::fabs(left);
    const float absRight = std::fabs(right);
    if (absLeft > maxLeft_) maxLeft_ = absLeft;
    if (absRight > maxRight_) maxRight_ = absRight;
    if (absLeft >= 1.0f || absRight >= 1.0f) clipped_ = true;
    if (++inPeakFrame_ == kHop) closePeakFrame();

    if (inWavePoint_ == 0) {
      waveMin_ = mono;
      waveMax_ = mono;
    } else {
      waveMin_ = std::fmin(waveMin_, mono);
      waveMax_ = std::fmax(waveMax_, mono);
    }
    if (++inWavePoint_ == kSamplesPerWaveformPoint) closeWaveformPoint();
  }
  return true;
}

void FeatureAccumulator::closePeakFrame() {
  peakLeft_.push_back(quantizeDb(toDb(maxLeft_)));
  peakRight_.push_back(quantizeDb(toDb(maxRight_)));
  clipFlags_.push_back(clipped_ ? 1 : 0);
  maxLeft_ = 0.0f;
  maxRight_ = 0.0f;
  clipped_ = false;
  inPeakFrame_ = 0;
}

void FeatureAccumulator::closeWaveformPoint() {
  waveform_.push_back(quantizeAmplitude(waveMin_));
  waveform_.push_back(quantizeAmplitude(waveMax_));
  inWavePoint_ = 0;
}

bool FeatureAccumulator::finish(std::vector<float>& mono, VisualsData& visuals) {
  // Close the trailing partial peak frame and waveform point from the samples they have (for peaks
  // this is the same as padding with silence)
  if (inPeakFrame_ > 0) closePeakFrame();
  if (inWavePoint_ > 0) closeWaveformPoint();
  if (mono_.empty()) return false;

  visuals.frameCount = static_cast<uint32_t>(peakLeft_.size());
  visuals.waveformCount = static_cast<uint32_t>(waveform_.size() / 2);
  visuals.clipBits.assign((visuals.frameCount + 7) / 8, 0);
  for (uint32_t i = 0; i < visuals.frameCount; ++i) {
    if (clipFlags_[i] != 0) visuals.clipBits[i / 8] |= static_cast<uint8_t>(1u << (i % 8));
  }
  visuals.peakLeft = std::move(peakLeft_);
  visuals.peakRight = std::move(peakRight_);
  visuals.waveform = std::move(waveform_);
  // The 30-minute capacity remains, but only the written pages use physical memory
  mono = std::move(mono_);
  return true;
}

}  // namespace segue

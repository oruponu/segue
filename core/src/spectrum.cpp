#include "spectrum.h"

#include <essentia/algorithmfactory.h>

#include <algorithm>
#include <cmath>
#include <exception>
#include <memory>
#include <mutex>

#include "cancel.h"
#include "constants.h"
#include "essentia_lock.h"
#include "log.h"
#include "visuals_data.h"

namespace segue {

SegueStatus computeSpectrum(const std::vector<float>& mono, uint32_t frameCount,
                            const SegueCancelToken* token, std::vector<uint8_t>& out) {
  using essentia::Real;
  using essentia::standard::Algorithm;
  using essentia::standard::AlgorithmFactory;

  constexpr int frameSize = kSpectrumFrameSize;
  constexpr int numBands = kBandCount;
  constexpr float sampleRate = static_cast<float>(kSampleRate);

  // Log-spaced band edges (20 Hz - 20 kHz) as fractional FFT bin positions
  const int spectrumSize = frameSize / 2 + 1;
  std::vector<float> binEdges(numBands + 1);
  for (int i = 0; i <= numBands; ++i) {
    const float freq = 20.0f * std::pow(20000.0f / 20.0f, static_cast<float>(i) / numBands);
    binEdges[i] = freq * frameSize / sampleRate;
  }

  // Accounts for the coherent gain (0.5) in the normalization
  const float normSq = (frameSize * 0.25f) * (frameSize * 0.25f);

  // Band correction: bandwidth normalization (relative to 1 kHz) + slope correction (dB/oct)
  constexpr float kSlopeDbPerOctave = 4.5f;
  const float bandRatio = std::pow(20000.0f / 20.0f, 1.0f / numBands);
  const float refBandwidth = 1000.0f * (bandRatio - 1.0f) * frameSize / sampleRate;
  std::vector<float> bandCorrection(numBands);
  for (int b = 0; b < numBands; ++b) {
    const float bandwidth = binEdges[b + 1] - binEdges[b];
    const float centerFreq = 20.0f * std::pow(1000.0f, (static_cast<float>(b) + 0.5f) / numBands);
    bandCorrection[b] = 10.0f * std::log10(refBandwidth / bandwidth) +
                        kSlopeDbPerOctave * std::log2(centerFreq / 1000.0f);
  }

  std::lock_guard<std::mutex> lock(essentiaMutex());
  try {
    AlgorithmFactory& factory = AlgorithmFactory::instance();
    std::unique_ptr<Algorithm> windowing(
        factory.create("Windowing", "type", "hann", "size", frameSize, "normalized", false));
    std::unique_ptr<Algorithm> spectrum(factory.create("Spectrum", "size", frameSize));

    std::vector<Real> frame(frameSize);
    std::vector<Real> windowed;
    std::vector<Real> spec;
    windowing->input("frame").set(frame);
    windowing->output("frame").set(windowed);
    spectrum->input("frame").set(windowed);
    spectrum->output("spectrum").set(spec);

    out.assign(static_cast<size_t>(frameCount) * numBands, 0);
    const int64_t n = static_cast<int64_t>(mono.size());
    for (uint32_t f = 0; f < frameCount; ++f) {
      if (f % 256 == 0 && isCancelled(token)) return SEGUE_CANCELLED;
      const int64_t start = static_cast<int64_t>(f) * kHop - frameSize / 2;
      for (int k = 0; k < frameSize; ++k) {
        const int64_t idx = start + k;
        frame[k] = (idx >= 0 && idx < n) ? mono[static_cast<size_t>(idx)] : 0.0f;
      }
      windowing->compute();
      spectrum->compute();

      for (int b = 0; b < numBands; ++b) {
        float sum = 0.0f;
        const int kStart = static_cast<int>(binEdges[b]);
        const int kEnd = std::min(static_cast<int>(std::ceil(binEdges[b + 1])), spectrumSize);
        for (int k = std::max(0, kStart); k < kEnd; ++k) {
          const float wLow = std::max(static_cast<float>(k), binEdges[b]);
          const float wHigh = std::min(static_cast<float>(k + 1), binEdges[b + 1]);
          const float weight = wHigh - wLow;
          if (weight > 0.0f) sum += spec[k] * spec[k] * weight;
        }
        const float db =
            sum > 1e-14f ? 10.0f * std::log10(sum / normSq) + bandCorrection[b] : -100.0f;
        out[static_cast<size_t>(f) * numBands + b] = quantizeDb(db);
      }
    }
  } catch (const std::exception& e) {
    SEGUE_LOGE("Spectrum failed: %s", e.what());
    return SEGUE_ANALYSIS_FAILED;
  }
  return SEGUE_OK;
}

}  // namespace segue

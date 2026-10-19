#pragma once

#include <cstdint>

namespace segue {

// When changing any of these values, bump kAnalysisVersion and update core/spec/visuals-format.md
constexpr int32_t kAnalysisVersion = 1;
constexpr uint16_t kVisualsFormatVersion = 1;

constexpr int kSampleRate = 44100;
constexpr int kHop = 1024;
constexpr int kSpectrumFrameSize = 4096;
constexpr int kBandCount = 32;
constexpr int kWaveformRate = 100;
constexpr int kSamplesPerWaveformPoint = kSampleRate / kWaveformRate;
constexpr float kDbFloor = -50.0f;

// 30 minutes of mono PCM takes about 317MB. Longer audio is not decoded
constexpr int64_t kMaxDecodeSeconds = 30 * 60;
constexpr int64_t kMaxDecodeSamples = int64_t{kSampleRate} * kMaxDecodeSeconds;

// Upper bounds on the counts read from a visuals cache (the most that 30 minutes of audio
// produces), so that a corrupted file cannot trigger a huge allocation
constexpr uint32_t kMaxVisualFrames = static_cast<uint32_t>(kMaxDecodeSamples / kHop + 1);
constexpr uint32_t kMaxWaveformPoints =
    static_cast<uint32_t>(kMaxDecodeSamples / kSamplesPerWaveformPoint + 1);

}  // namespace segue

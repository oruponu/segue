#pragma once

#include <cstddef>

#include "segue/segue_core.h"

namespace segue {

// Receives 44.1kHz stereo (interleaved float)
class StereoSink {
 public:
  virtual ~StereoSink() = default;
  // Returning false aborts decoding (the result is SEGUE_DECODE_FAILED)
  virtual bool onFrames(const float* interleaved, size_t frames) = 0;
};

// Decodes path with libav, converts it to 44.1kHz stereo and passes it to sink.
// 1ch is copied to L/R; 3ch or more is downmixed to stereo with swresample's default matrix.
// Conversion to 44.1kHz uses libsamplerate's SRC_LINEAR (same as the legacy MonoLoader's
// resampleQuality 4).
SegueStatus decodeStereo44k(const char* path, const SegueCancelToken* token, StereoSink& sink);

}  // namespace segue

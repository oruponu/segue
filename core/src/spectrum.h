#pragma once

#include <cstdint>
#include <vector>

#include "segue/segue_core.h"

namespace segue {

// Computes the 32-band spectrum, quantized, into out (frameCount × kBandCount).
// Frame i is the 4096-sample window centered at i × hop (zero outside the signal). Takes the lock
// because it uses Essentia.
SegueStatus computeSpectrum(const std::vector<float>& mono, uint32_t frameCount,
                            const SegueCancelToken* token, std::vector<uint8_t>& out);

}  // namespace segue

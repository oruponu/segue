#ifndef AUDIO_DECODE_H
#define AUDIO_DECODE_H

#include <vector>

#include "essentia_bridge.h"

int decode_audio(const char* path, std::vector<float>& out_samples, int target_sr,
                 EssentiaCancelFlag* cancel_flag);

#endif  // AUDIO_DECODE_H

#pragma once

#include <vector>

#include "models.h"
#include "segue/segue_core.h"

namespace segue {

// RhythmExtractor2013 and KeyExtractor (same as the legacy essentia_bridge.cpp). Fills bpm and
// key_* in result
SegueStatus analyzeRhythmAndKey(const std::vector<float>& mono, const SegueCancelToken* token,
                                SegueMusicResult& result);

// Runs Discogs-EffNet on 16kHz mel patches and puts the top 5 into result's style_* (same as the
// legacy style_classifier.cpp). Audio too short for a single patch returns SEGUE_OK with
// style_count = 0
SegueStatus classifyStyle(const std::vector<float>& mono, const SegueModels& models,
                          const SegueCancelToken* token, SegueMusicResult& result);

}  // namespace segue

#pragma once

#include <vector>

#include "visuals_data.h"

struct SegueAudio {
  std::vector<float> mono;  // 44.1kHz mono (average of L and R)
  segue::VisualsData visuals;
};

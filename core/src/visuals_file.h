#pragma once

#include "segue/segue_core.h"
#include "visuals_data.h"

namespace segue {

SegueStatus writeVisualsFile(const VisualsData& visuals, const char* path);
// SEGUE_IO_FAILED when the header (magic, versions, fixed parameters, count limits) or the file
// size does not match
SegueStatus readVisualsFile(const char* path, VisualsData& out);

}  // namespace segue

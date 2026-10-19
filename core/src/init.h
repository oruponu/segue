#pragma once

#include "segue/segue_core.h"

namespace segue {

// Runs essentia::init() once and returns SEGUE_OK after it succeeds; a failed attempt is retried
// on the next call
SegueStatus ensureInitialized();

}  // namespace segue

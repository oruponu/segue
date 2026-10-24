#pragma once

#include <atomic>

#include "segue/segue_core.h"

struct SegueCancelToken {
  std::atomic<bool> requested{false};
};

namespace segue {

inline bool isCancelled(const SegueCancelToken* token) {
  return token != nullptr && token->requested.load(std::memory_order_acquire);
}

}  // namespace segue

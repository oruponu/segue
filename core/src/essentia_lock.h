#pragma once

#include <mutex>

namespace segue {

// Essentia is not thread-safe (a data race in its logger caused crashes).
// Every call into Essentia must hold this lock.
inline std::mutex& essentiaMutex() {
  static std::mutex mutex;
  return mutex;
}

}  // namespace segue

#ifndef ESSENTIA_LOCK_H
#define ESSENTIA_LOCK_H

#include <mutex>

inline std::mutex& essentiaGlobalMutex() {
  static std::mutex mutex;
  return mutex;
}

#endif  // ESSENTIA_LOCK_H

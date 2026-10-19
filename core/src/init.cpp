#include "init.h"

#include <essentia/essentia.h>

#include <exception>
#include <mutex>
#include <new>

#include "cancel.h"
#include "constants.h"
#include "essentia_lock.h"
#include "log.h"

namespace segue {
namespace {

std::once_flag gInitOnce;
SegueStatus gInitStatus = SEGUE_ANALYSIS_FAILED;

}  // namespace

// Converts every exception (including the system_error from taking the lock) into a status.
// call_once is not marked done when an exception escapes, so the next call retries.
SegueStatus ensureInitialized() {
  try {
    std::call_once(gInitOnce, [] {
      std::lock_guard<std::mutex> lock(essentiaMutex());
      essentia::init();
      gInitStatus = SEGUE_OK;
    });
  } catch (const std::exception& e) {
    SEGUE_LOGE("essentia::init failed: %s", e.what());
    return SEGUE_ANALYSIS_FAILED;
  } catch (...) {
    SEGUE_LOGE("essentia::init failed: unknown exception");
    return SEGUE_ANALYSIS_FAILED;
  }
  return gInitStatus;
}

}  // namespace segue

extern "C" {

SegueStatus segue_init(void) { return segue::ensureInitialized(); }

int32_t segue_analysis_version(void) { return segue::kAnalysisVersion; }

SegueCancelToken* segue_cancel_create(void) { return new (std::nothrow) SegueCancelToken(); }

void segue_cancel_request(SegueCancelToken* token) {
  if (token != nullptr) token->requested.store(true, std::memory_order_release);
}

void segue_cancel_destroy(SegueCancelToken* token) { delete token; }

}  // extern "C"

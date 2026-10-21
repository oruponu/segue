#include <exception>

#include "analysis.h"
#include "audio.h"
#include "cancel.h"
#include "init.h"
#include "log.h"
#include "models.h"
#include "segue/segue_core.h"

extern "C" {

SegueStatus segue_analyze(const SegueAudio* audio, const SegueModels* models,
                          SegueCancelToken* token, SegueMusicResult* out) {
  if (audio == nullptr || out == nullptr) return SEGUE_INVALID_ARGUMENT;
  *out = SegueMusicResult{};
  out->key_note = -1;
  out->key_scale = -1;
  const SegueStatus init = segue::ensureInitialized();
  if (init != SEGUE_OK) return init;

  try {
    SegueStatus status = segue::analyzeRhythmAndKey(audio->mono, token, *out);
    if (status != SEGUE_OK) return status;
    if (models != nullptr) {
      status = segue::classifyStyle(audio->mono, *models, token, *out);
      if (status != SEGUE_OK) return status;
    }
    return segue::isCancelled(token) ? SEGUE_CANCELLED : SEGUE_OK;
  } catch (const std::exception& e) {
    SEGUE_LOGE("Analyze failed: %s", e.what());
    return SEGUE_ANALYSIS_FAILED;
  } catch (...) {
    return SEGUE_ANALYSIS_FAILED;
  }
}

}  // extern "C"

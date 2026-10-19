#include <exception>
#include <memory>
#include <new>

#include "audio.h"
#include "constants.h"
#include "decoder.h"
#include "feature_accumulator.h"
#include "init.h"
#include "log.h"
#include "segue/segue_core.h"
#include "spectrum.h"
#include "visuals_file.h"

extern "C" {

SegueStatus segue_audio_decode_file(const char* path, SegueCancelToken* token, SegueAudio** out) {
  if (out == nullptr) return SEGUE_INVALID_ARGUMENT;
  *out = nullptr;
  if (path == nullptr) return SEGUE_INVALID_ARGUMENT;
  const SegueStatus init = segue::ensureInitialized();
  if (init != SEGUE_OK) return init;

  try {
    auto audio = std::make_unique<SegueAudio>();
    segue::FeatureAccumulator accumulator;
    SegueStatus status = segue::decodeStereo44k(path, token, accumulator);
    if (status != SEGUE_OK) return status;
    if (!accumulator.finish(audio->mono, audio->visuals)) return SEGUE_DECODE_FAILED;
    status = segue::computeSpectrum(audio->mono, audio->visuals.frameCount, token,
                                    audio->visuals.spectrum);
    if (status != SEGUE_OK) return status;
    *out = audio.release();
    return SEGUE_OK;
  } catch (const std::bad_alloc&) {
    SEGUE_LOGE("Out of memory while decoding: %s", path);
    return SEGUE_DECODE_FAILED;
  } catch (const std::exception& e) {
    SEGUE_LOGE("Decode failed: %s", e.what());
    return SEGUE_DECODE_FAILED;
  } catch (...) {
    return SEGUE_DECODE_FAILED;
  }
}

void segue_audio_free(SegueAudio* audio) { delete audio; }

SegueStatus segue_visuals_write(const SegueAudio* audio, const char* cache_path) {
  if (audio == nullptr || cache_path == nullptr) return SEGUE_INVALID_ARGUMENT;
  try {
    return segue::writeVisualsFile(audio->visuals, cache_path);
  } catch (...) {
    return SEGUE_IO_FAILED;
  }
}

SegueStatus segue_visuals_read(const char* cache_path, SegueVisuals** out) {
  if (out == nullptr) return SEGUE_INVALID_ARGUMENT;
  *out = nullptr;
  if (cache_path == nullptr) return SEGUE_INVALID_ARGUMENT;
  try {
    auto visuals = std::make_unique<SegueVisuals>();
    const SegueStatus status = segue::readVisualsFile(cache_path, visuals->data);
    if (status != SEGUE_OK) return status;
    *out = visuals.release();
    return SEGUE_OK;
  } catch (...) {
    return SEGUE_IO_FAILED;
  }
}

void segue_visuals_free(SegueVisuals* visuals) { delete visuals; }

int32_t segue_visuals_sample_rate(const SegueVisuals* v) { return v ? segue::kSampleRate : 0; }
int32_t segue_visuals_hop(const SegueVisuals* v) { return v ? segue::kHop : 0; }
int32_t segue_visuals_frame_count(const SegueVisuals* v) {
  return v ? static_cast<int32_t>(v->data.frameCount) : 0;
}
int32_t segue_visuals_band_count(const SegueVisuals* v) { return v ? segue::kBandCount : 0; }
const uint8_t* segue_visuals_spectrum(const SegueVisuals* v) {
  return v ? v->data.spectrum.data() : nullptr;
}
const uint8_t* segue_visuals_peak_left(const SegueVisuals* v) {
  return v ? v->data.peakLeft.data() : nullptr;
}
const uint8_t* segue_visuals_peak_right(const SegueVisuals* v) {
  return v ? v->data.peakRight.data() : nullptr;
}
const uint8_t* segue_visuals_clip_bits(const SegueVisuals* v) {
  return v ? v->data.clipBits.data() : nullptr;
}
int32_t segue_visuals_waveform_rate(const SegueVisuals* v) { return v ? segue::kWaveformRate : 0; }
int32_t segue_visuals_waveform_count(const SegueVisuals* v) {
  return v ? static_cast<int32_t>(v->data.waveformCount) : 0;
}
const int8_t* segue_visuals_waveform(const SegueVisuals* v) {
  return v ? v->data.waveform.data() : nullptr;
}

}  // extern "C"

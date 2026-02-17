#include "essentia_bridge.h"

#include <atomic>
#include <string>
#include <vector>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "EssentiaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...)
#define LOGE(...)
#endif

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/channel_layout.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
}

#include <essentia/algorithmfactory.h>
#include <essentia/essentiamath.h>
#include <essentia/pool.h>

using namespace essentia;
using namespace essentia::standard;

struct EssentiaCancelFlag {
  std::atomic<bool> cancelled{false};
};

static bool is_cancelled(EssentiaCancelFlag* flag) {
  return flag && flag->cancelled.load(std::memory_order_acquire);
}

static const int TARGET_SAMPLE_RATE = 44100;

static int decode_audio(const char* path, std::vector<float>& out_samples,
                        EssentiaCancelFlag* cancel_flag) {
  AVFormatContext* fmt_ctx = nullptr;
  int ret = avformat_open_input(&fmt_ctx, path, nullptr, nullptr);
  if (ret < 0) {
    LOGE("avformat_open_input failed: %d", ret);
    return -1;
  }

  ret = avformat_find_stream_info(fmt_ctx, nullptr);
  if (ret < 0) {
    LOGE("avformat_find_stream_info failed: %d", ret);
    avformat_close_input(&fmt_ctx);
    return -1;
  }

  int audio_stream_idx = -1;
  for (unsigned i = 0; i < fmt_ctx->nb_streams; i++) {
    if (fmt_ctx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
      audio_stream_idx = static_cast<int>(i);
      break;
    }
  }
  if (audio_stream_idx < 0) {
    LOGE("No audio stream found");
    avformat_close_input(&fmt_ctx);
    return -1;
  }

  AVCodecParameters* codecpar = fmt_ctx->streams[audio_stream_idx]->codecpar;
  const AVCodec* codec = avcodec_find_decoder(codecpar->codec_id);
  if (!codec) {
    LOGE("Decoder not found for codec_id: %d", codecpar->codec_id);
    avformat_close_input(&fmt_ctx);
    return -1;
  }

  AVCodecContext* codec_ctx = avcodec_alloc_context3(codec);
  avcodec_parameters_to_context(codec_ctx, codecpar);
  ret = avcodec_open2(codec_ctx, codec, nullptr);
  if (ret < 0) {
    LOGE("avcodec_open2 failed: %d", ret);
    avcodec_free_context(&codec_ctx);
    avformat_close_input(&fmt_ctx);
    return -1;
  }

  SwrContext* swr_ctx = swr_alloc();

  AVChannelLayout out_ch_layout = AV_CHANNEL_LAYOUT_MONO;
  AVChannelLayout in_ch_layout;
  if (codec_ctx->ch_layout.nb_channels > 0) {
    av_channel_layout_copy(&in_ch_layout, &codec_ctx->ch_layout);
  } else {
    av_channel_layout_default(&in_ch_layout, 2);
  }

  swr_alloc_set_opts2(&swr_ctx, &out_ch_layout, AV_SAMPLE_FMT_FLT, TARGET_SAMPLE_RATE,
                      &in_ch_layout, codec_ctx->sample_fmt, codec_ctx->sample_rate, 0, nullptr);

  av_channel_layout_uninit(&in_ch_layout);

  ret = swr_init(swr_ctx);
  if (ret < 0) {
    LOGE("swr_init failed: %d", ret);
    swr_free(&swr_ctx);
    avcodec_free_context(&codec_ctx);
    avformat_close_input(&fmt_ctx);
    return -1;
  }

  AVPacket* packet = av_packet_alloc();
  AVFrame* frame = av_frame_alloc();

  while (av_read_frame(fmt_ctx, packet) >= 0) {
    if (is_cancelled(cancel_flag)) {
      av_packet_unref(packet);
      break;
    }

    if (packet->stream_index != audio_stream_idx) {
      av_packet_unref(packet);
      continue;
    }

    ret = avcodec_send_packet(codec_ctx, packet);
    av_packet_unref(packet);
    if (ret < 0) continue;

    while (avcodec_receive_frame(codec_ctx, frame) == 0) {
      int out_samples_count = swr_get_out_samples(swr_ctx, frame->nb_samples);
      if (out_samples_count <= 0) continue;

      size_t prev_size = out_samples.size();
      out_samples.resize(prev_size + out_samples_count);
      uint8_t* out_buf = reinterpret_cast<uint8_t*>(out_samples.data() + prev_size);

      int converted = swr_convert(swr_ctx, &out_buf, out_samples_count,
                                  (const uint8_t**)frame->extended_data, frame->nb_samples);

      if (converted > 0) {
        out_samples.resize(prev_size + converted);
      } else {
        out_samples.resize(prev_size);
      }

      av_frame_unref(frame);
    }
  }

  if (!is_cancelled(cancel_flag)) {
    int flush_count = swr_get_out_samples(swr_ctx, 0);
    if (flush_count > 0) {
      size_t prev_size = out_samples.size();
      out_samples.resize(prev_size + flush_count);
      uint8_t* out_buf = reinterpret_cast<uint8_t*>(out_samples.data() + prev_size);
      int converted = swr_convert(swr_ctx, &out_buf, flush_count, nullptr, 0);
      if (converted > 0) {
        out_samples.resize(prev_size + converted);
      } else {
        out_samples.resize(prev_size);
      }
    }
  }

  av_frame_free(&frame);
  av_packet_free(&packet);
  swr_free(&swr_ctx);
  avcodec_free_context(&codec_ctx);
  avformat_close_input(&fmt_ctx);

  return is_cancelled(cancel_flag) ? 1 : 0;
}

extern "C" {

EssentiaCancelFlag* essentia_cancel_flag_create(void) { return new EssentiaCancelFlag(); }

void essentia_cancel_flag_set(EssentiaCancelFlag* flag) {
  if (flag) {
    flag->cancelled.store(true, std::memory_order_release);
  }
}

void essentia_cancel_flag_destroy(EssentiaCancelFlag* flag) { delete flag; }

void essentia_init(void) { essentia::init(); }

void essentia_shutdown(void) { essentia::shutdown(); }

EssentiaResult essentia_analyze(const char* path, EssentiaCancelFlag* cancel_flag) {
  EssentiaResult result = {};
  result.key_note = -1;
  result.key_scale = -1;

  LOGI("Loading: %s", path);
  std::vector<float> audio;
  int decode_ret = decode_audio(path, audio, cancel_flag);
  if (decode_ret < 0) {
    result.error_code = 2;
    return result;
  }
  if (decode_ret == 1 || is_cancelled(cancel_flag)) {
    result.error_code = 1;
    return result;
  }
  LOGI("Decoded %zu samples (%.1f seconds)", audio.size(),
       (float)audio.size() / TARGET_SAMPLE_RATE);

  if (audio.empty()) {
    LOGE("No audio samples decoded");
    result.error_code = 2;
    return result;
  }

  AlgorithmFactory& factory = AlgorithmFactory::instance();

  if (is_cancelled(cancel_flag)) {
    result.error_code = 1;
    return result;
  }

  try {
    Real bpm = 0;
    std::vector<Real> ticks;
    Real confidence = 0;
    std::vector<Real> estimates;
    std::vector<Real> bpmIntervals;

    Algorithm* rhythm = factory.create("RhythmExtractor2013");
    rhythm->input("signal").set(audio);
    rhythm->output("bpm").set(bpm);
    rhythm->output("ticks").set(ticks);
    rhythm->output("confidence").set(confidence);
    rhythm->output("estimates").set(estimates);
    rhythm->output("bpmIntervals").set(bpmIntervals);
    rhythm->compute();
    delete rhythm;

    result.bpm = bpm;
    result.bpm_confidence = confidence;
    LOGI("BPM: %.1f (confidence: %.2f)", bpm, confidence);
  } catch (const std::exception& e) {
    LOGE("Rhythm analysis error: %s", e.what());
    result.error_code = 3;
    return result;
  }

  if (is_cancelled(cancel_flag)) {
    result.error_code = 1;
    return result;
  }

  try {
    std::string key_str;
    std::string scale_str;
    Real strength = 0;

    Algorithm* keyExtractor = factory.create("KeyExtractor");
    keyExtractor->input("audio").set(audio);
    keyExtractor->output("key").set(key_str);
    keyExtractor->output("scale").set(scale_str);
    keyExtractor->output("strength").set(strength);
    keyExtractor->compute();
    delete keyExtractor;

    static const char* note_names[] = {"C",  "C#", "D",  "Eb", "E",  "F",
                                       "F#", "G",  "Ab", "A",  "Bb", "B"};
    static const char* note_names_alt[] = {"",   "Db", "",   "D#", "",   "",
                                           "Gb", "",   "G#", "",   "A#", "Cb"};

    result.key_note = -1;
    for (int i = 0; i < 12; i++) {
      if (key_str == note_names[i] ||
          (note_names_alt[i][0] != '\0' && key_str == note_names_alt[i])) {
        result.key_note = static_cast<int8_t>(i);
        break;
      }
    }

    result.key_scale = (scale_str == "minor") ? 1 : 0;
    result.key_confidence = strength;
    LOGI("Key: %s %s (strength: %.2f)", key_str.c_str(), scale_str.c_str(), strength);
  } catch (const std::exception& e) {
    LOGE("Key analysis error: %s", e.what());
    result.error_code = 3;
    return result;
  }

  if (is_cancelled(cancel_flag)) {
    result.error_code = 1;
    return result;
  }

  result.error_code = 0;
  return result;
}

}  // extern "C"

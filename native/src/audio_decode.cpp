#include "audio_decode.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "AudioDecode"
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

static bool is_cancelled(EssentiaCancelFlag* flag) {
  return essentia_cancel_flag_is_set(flag) != 0;
}

int decode_audio(const char* path, std::vector<float>& out_samples, int target_sr,
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

  swr_alloc_set_opts2(&swr_ctx, &out_ch_layout, AV_SAMPLE_FMT_FLT, target_sr, &in_ch_layout,
                      codec_ctx->sample_fmt, codec_ctx->sample_rate, 0, nullptr);

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

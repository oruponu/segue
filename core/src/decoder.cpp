#include "decoder.h"

#include <samplerate.h>

#include <memory>
#include <string>
#include <vector>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/channel_layout.h>
#include <libswresample/swresample.h>
}

#include "cancel.h"
#include "constants.h"
#include "log.h"

namespace segue {
namespace {

// av_err2str is a compound literal macro and cannot be used from C++
std::string avErrorString(int err) {
  char buf[AV_ERROR_MAX_STRING_SIZE] = {};
  av_strerror(err, buf, sizeof(buf));
  return buf;
}

struct FormatDeleter {
  void operator()(AVFormatContext* p) const { avformat_close_input(&p); }
};
struct CodecDeleter {
  void operator()(AVCodecContext* p) const { avcodec_free_context(&p); }
};
struct SwrDeleter {
  void operator()(SwrContext* p) const { swr_free(&p); }
};
struct PacketDeleter {
  void operator()(AVPacket* p) const { av_packet_free(&p); }
};
struct FrameDeleter {
  void operator()(AVFrame* p) const { av_frame_free(&p); }
};
struct SrcDeleter {
  void operator()(SRC_STATE* p) const { src_delete(p); }
};

// Converts stereo at the source sample rate to 44.1kHz and passes it to sink
class Resampler {
 public:
  Resampler(int inRate, StereoSink& sink)
      : sink_(sink), ratio_(static_cast<double>(kSampleRate) / inRate) {
    if (inRate != kSampleRate) {
      int error = 0;
      state_.reset(src_new(SRC_LINEAR, 2, &error));
      failed_ = state_ == nullptr;
    }
  }

  bool failed() const { return failed_; }

  // Returning false means abort
  bool push(const float* interleaved, long frames, bool endOfInput) {
    if (!state_) {
      return frames == 0 || sink_.onFrames(interleaved, static_cast<size_t>(frames));
    }
    SRC_DATA data{};
    data.data_in = interleaved;
    data.input_frames = frames;
    data.src_ratio = ratio_;
    data.end_of_input = endOfInput ? 1 : 0;
    out_.resize(static_cast<size_t>(frames * ratio_ + 64) * 2);
    while (true) {
      data.data_out = out_.data();
      data.output_frames = static_cast<long>(out_.size() / 2);
      if (src_process(state_.get(), &data) != 0) {
        failed_ = true;
        return false;
      }
      if (data.output_frames_gen > 0 &&
          !sink_.onFrames(out_.data(), static_cast<size_t>(data.output_frames_gen))) {
        return false;
      }
      if (data.input_frames_used == 0 && data.output_frames_gen == 0 && data.input_frames > 0) {
        failed_ = true;  // No progress (should not happen)
        return false;
      }
      data.data_in += data.input_frames_used * 2;
      data.input_frames -= data.input_frames_used;
      if (data.input_frames > 0) continue;
      if (endOfInput && data.output_frames_gen > 0) continue;
      return true;
    }
  }

 private:
  StereoSink& sink_;
  double ratio_;
  std::unique_ptr<SRC_STATE, SrcDeleter> state_;
  std::vector<float> out_;
  bool failed_ = false;
};

}  // namespace

SegueStatus decodeStereo44k(const char* path, const SegueCancelToken* token, StereoSink& sink) {
  AVFormatContext* rawFormat = nullptr;
  const int openRet = avformat_open_input(&rawFormat, path, nullptr, nullptr);
  if (openRet < 0) {
    SEGUE_LOGE("avformat_open_input failed: %s: %s (%d)", path, avErrorString(openRet).c_str(),
               openRet);
    return SEGUE_DECODE_FAILED;
  }
  std::unique_ptr<AVFormatContext, FormatDeleter> format(rawFormat);
  if (avformat_find_stream_info(format.get(), nullptr) < 0) return SEGUE_DECODE_FAILED;

  const AVCodec* codec = nullptr;
  const int streamIndex = av_find_best_stream(format.get(), AVMEDIA_TYPE_AUDIO, -1, -1, &codec, 0);
  if (streamIndex < 0 || codec == nullptr) return SEGUE_DECODE_FAILED;

  // Reject without decoding when the duration exceeds 30 minutes. Without a duration, the sink
  // (FeatureAccumulator) stops at the sample limit
  if (format->duration != AV_NOPTS_VALUE && format->duration > 0) {
    const int64_t seconds = format->duration / AV_TIME_BASE;
    if (seconds > kMaxDecodeSeconds) {
      SEGUE_LOGE("Too long: %lld seconds", static_cast<long long>(seconds));
      return SEGUE_DECODE_FAILED;
    }
  }

  std::unique_ptr<AVCodecContext, CodecDeleter> decoder(avcodec_alloc_context3(codec));
  if (!decoder) return SEGUE_DECODE_FAILED;
  if (avcodec_parameters_to_context(decoder.get(), format->streams[streamIndex]->codecpar) < 0) {
    return SEGUE_DECODE_FAILED;
  }
  if (avcodec_open2(decoder.get(), codec, nullptr) < 0) return SEGUE_DECODE_FAILED;

  const int inRate = decoder->sample_rate;
  const int inChannels = decoder->ch_layout.nb_channels;
  const AVSampleFormat inFormat = decoder->sample_fmt;
  if (inRate <= 0 || inChannels <= 0) return SEGUE_DECODE_FAILED;

  AVChannelLayout inLayout{};
  if (decoder->ch_layout.order == AV_CHANNEL_ORDER_UNSPEC) {
    av_channel_layout_default(&inLayout, inChannels);
  } else if (av_channel_layout_copy(&inLayout, &decoder->ch_layout) < 0) {
    return SEGUE_DECODE_FAILED;
  }
  // 1ch is converted as is and copied to L/R later (swresample's mono-to-stereo lowers it by 3dB)
  AVChannelLayout outLayout{};
  av_channel_layout_default(&outLayout, inChannels == 1 ? 1 : 2);
  const int outChannels = outLayout.nb_channels;

  SwrContext* rawSwr = nullptr;
  const int swrRet = swr_alloc_set_opts2(&rawSwr, &outLayout, AV_SAMPLE_FMT_FLT, inRate, &inLayout,
                                         inFormat, inRate, 0, nullptr);
  av_channel_layout_uninit(&inLayout);
  av_channel_layout_uninit(&outLayout);
  std::unique_ptr<SwrContext, SwrDeleter> swr(rawSwr);
  if (swrRet < 0 || !swr || swr_init(swr.get()) < 0) return SEGUE_DECODE_FAILED;

  Resampler resampler(inRate, sink);
  if (resampler.failed()) return SEGUE_DECODE_FAILED;

  std::unique_ptr<AVPacket, PacketDeleter> packet(av_packet_alloc());
  std::unique_ptr<AVFrame, FrameDeleter> frame(av_frame_alloc());
  if (!packet || !frame) return SEGUE_DECODE_FAILED;

  std::vector<float> converted;  // Interleaved, outChannels
  std::vector<float> stereo;     // Interleaved 2ch with the 1ch copied

  // When f is nullptr, flushes the samples left in swresample
  auto convertFrame = [&](const AVFrame* f) -> bool {
    // swresample is set up for the first parameters. A stream that changes them mid-stream (an mp3
    // that switches from stereo to mono) would make it read missing planes, so it fails instead
    if (f != nullptr && (f->sample_rate != inRate || f->ch_layout.nb_channels != inChannels ||
                         f->format != inFormat)) {
      SEGUE_LOGE("Stream parameters changed mid-stream: %d Hz, %d channels", f->sample_rate,
                 f->ch_layout.nb_channels);
      return false;
    }
    const int inSamples = f != nullptr ? f->nb_samples : 0;
    const int maxOut = swr_get_out_samples(swr.get(), inSamples);
    if (maxOut < 0) return false;
    if (maxOut == 0) return true;
    converted.resize(static_cast<size_t>(maxOut) * outChannels);
    uint8_t* outPtr = reinterpret_cast<uint8_t*>(converted.data());
    const uint8_t** inPtr = f != nullptr ? const_cast<const uint8_t**>(f->extended_data) : nullptr;
    const int got = swr_convert(swr.get(), &outPtr, maxOut, inPtr, inSamples);
    if (got < 0) return false;
    if (got == 0) return true;
    const float* data = converted.data();
    if (outChannels == 1) {
      stereo.resize(static_cast<size_t>(got) * 2);
      for (int i = 0; i < got; ++i) {
        stereo[2 * i] = data[i];
        stereo[2 * i + 1] = data[i];
      }
      data = stereo.data();
    }
    return resampler.push(data, got, false);
  };

  // AVERROR_INVALIDDATA (corrupted packets or frames) is skipped so that an mp3 that is
  // slightly corrupted in the middle can still be played and analyzed
  auto drainDecoder = [&]() -> bool {
    while (true) {
      const int ret = avcodec_receive_frame(decoder.get(), frame.get());
      if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) return true;
      if (ret == AVERROR_INVALIDDATA) continue;
      if (ret < 0) {
        SEGUE_LOGE("avcodec_receive_frame failed: %s (%d)", avErrorString(ret).c_str(), ret);
        return false;
      }
      const bool ok = convertFrame(frame.get());
      av_frame_unref(frame.get());
      if (!ok) return false;
    }
  };

  while (true) {
    if (isCancelled(token)) return SEGUE_CANCELLED;
    const int readRet = av_read_frame(format.get(), packet.get());
    if (readRet == AVERROR_EOF) break;
    if (readRet < 0) {
      SEGUE_LOGE("av_read_frame failed: %s (%d)", avErrorString(readRet).c_str(), readRet);
      return SEGUE_DECODE_FAILED;
    }
    const bool isTarget = packet->stream_index == streamIndex;
    const int sendRet = isTarget ? avcodec_send_packet(decoder.get(), packet.get()) : 0;
    av_packet_unref(packet.get());
    if (!isTarget || sendRet == AVERROR_INVALIDDATA) continue;
    if (sendRet < 0) {
      SEGUE_LOGE("avcodec_send_packet failed: %s (%d)", avErrorString(sendRet).c_str(), sendRet);
      return SEGUE_DECODE_FAILED;
    }
    if (!drainDecoder()) return SEGUE_DECODE_FAILED;
  }

  // Flush. Any error other than AVERROR_EOF (already flushed) is a failure
  const int flushRet = avcodec_send_packet(decoder.get(), nullptr);
  if (flushRet < 0 && flushRet != AVERROR_EOF) {
    SEGUE_LOGE("avcodec_send_packet (flush) failed: %s (%d)", avErrorString(flushRet).c_str(),
               flushRet);
    return SEGUE_DECODE_FAILED;
  }
  if (!drainDecoder()) return SEGUE_DECODE_FAILED;
  if (!convertFrame(nullptr)) return SEGUE_DECODE_FAILED;
  if (!resampler.push(nullptr, 0, true)) return SEGUE_DECODE_FAILED;
  return isCancelled(token) ? SEGUE_CANCELLED : SEGUE_OK;
}

}  // namespace segue

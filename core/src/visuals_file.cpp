#include "visuals_file.h"

#include <cstdio>
#include <cstring>
#include <memory>
#include <vector>

#include "constants.h"

namespace segue {
namespace {

struct FileCloser {
  void operator()(FILE* f) const { std::fclose(f); }
};

constexpr size_t kHeaderSize = 28;
constexpr char kMagic[4] = {'S', 'G', 'V', 'Z'};

void putU16(std::vector<uint8_t>& b, uint16_t v) {
  b.push_back(static_cast<uint8_t>(v & 0xFF));
  b.push_back(static_cast<uint8_t>(v >> 8));
}

void putU32(std::vector<uint8_t>& b, uint32_t v) {
  for (int i = 0; i < 4; ++i) b.push_back(static_cast<uint8_t>((v >> (8 * i)) & 0xFF));
}

uint16_t getU16(const uint8_t* p) { return static_cast<uint16_t>(p[0] | (p[1] << 8)); }

uint32_t getU32(const uint8_t* p) {
  return static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8) |
         (static_cast<uint32_t>(p[2]) << 16) | (static_cast<uint32_t>(p[3]) << 24);
}

size_t bodySize(uint32_t frames, uint32_t wavePoints) {
  return static_cast<size_t>(frames) * kBandCount + 2 * static_cast<size_t>(frames) +
         (frames + 7) / 8 + 2 * static_cast<size_t>(wavePoints);
}

}  // namespace

SegueStatus writeVisualsFile(const VisualsData& v, const char* path) {
  if (path == nullptr || v.frameCount == 0 || v.waveformCount == 0 ||
      v.spectrum.size() != static_cast<size_t>(v.frameCount) * kBandCount ||
      v.peakLeft.size() != v.frameCount || v.peakRight.size() != v.frameCount ||
      v.clipBits.size() != (v.frameCount + 7) / 8 ||
      v.waveform.size() != static_cast<size_t>(v.waveformCount) * 2) {
    return SEGUE_INVALID_ARGUMENT;
  }

  std::vector<uint8_t> buf;
  buf.reserve(kHeaderSize + bodySize(v.frameCount, v.waveformCount));
  buf.insert(buf.end(), kMagic, kMagic + 4);
  putU16(buf, kVisualsFormatVersion);
  putU16(buf, static_cast<uint16_t>(kAnalysisVersion));
  putU32(buf, kSampleRate);
  putU32(buf, kHop);
  putU32(buf, v.frameCount);
  putU16(buf, kBandCount);
  putU16(buf, kWaveformRate);
  putU32(buf, v.waveformCount);
  buf.insert(buf.end(), v.spectrum.begin(), v.spectrum.end());
  buf.insert(buf.end(), v.peakLeft.begin(), v.peakLeft.end());
  buf.insert(buf.end(), v.peakRight.begin(), v.peakRight.end());
  buf.insert(buf.end(), v.clipBits.begin(), v.clipBits.end());
  for (int8_t w : v.waveform) buf.push_back(static_cast<uint8_t>(w));

  FILE* file = std::fopen(path, "wb");
  if (file == nullptr) return SEGUE_IO_FAILED;
  const size_t written = std::fwrite(buf.data(), 1, buf.size(), file);
  const int closed = std::fclose(file);
  if (written != buf.size() || closed != 0) {
    std::remove(path);
    return SEGUE_IO_FAILED;
  }
  return SEGUE_OK;
}

SegueStatus readVisualsFile(const char* path, VisualsData& out) {
  if (path == nullptr) return SEGUE_INVALID_ARGUMENT;
  std::unique_ptr<FILE, FileCloser> file(std::fopen(path, "rb"));
  if (!file) return SEGUE_IO_FAILED;

  // Check the count limits and the file size before allocating the body
  uint8_t h[kHeaderSize];
  if (std::fread(h, 1, kHeaderSize, file.get()) != kHeaderSize) return SEGUE_IO_FAILED;
  if (std::memcmp(h, kMagic, 4) != 0 || getU16(h + 4) != kVisualsFormatVersion ||
      getU16(h + 6) != kAnalysisVersion || getU32(h + 8) != static_cast<uint32_t>(kSampleRate) ||
      getU32(h + 12) != static_cast<uint32_t>(kHop) || getU16(h + 20) != kBandCount ||
      getU16(h + 22) != kWaveformRate) {
    return SEGUE_IO_FAILED;
  }
  const uint32_t frames = getU32(h + 16);
  const uint32_t wavePoints = getU32(h + 24);
  if (frames == 0 || wavePoints == 0 || frames > kMaxVisualFrames ||
      wavePoints > kMaxWaveformPoints) {
    return SEGUE_IO_FAILED;
  }
  const size_t body = bodySize(frames, wavePoints);
  if (std::fseek(file.get(), 0, SEEK_END) != 0) return SEGUE_IO_FAILED;
  const long fileSize = std::ftell(file.get());
  if (fileSize < 0 || static_cast<size_t>(fileSize) != kHeaderSize + body) return SEGUE_IO_FAILED;
  if (std::fseek(file.get(), static_cast<long>(kHeaderSize), SEEK_SET) != 0) return SEGUE_IO_FAILED;
  std::vector<uint8_t> buf(body);
  if (std::fread(buf.data(), 1, body, file.get()) != body) return SEGUE_IO_FAILED;

  const uint8_t* p = buf.data();
  out.frameCount = frames;
  out.waveformCount = wavePoints;
  out.spectrum.assign(p, p + static_cast<size_t>(frames) * kBandCount);
  p += static_cast<size_t>(frames) * kBandCount;
  out.peakLeft.assign(p, p + frames);
  p += frames;
  out.peakRight.assign(p, p + frames);
  p += frames;
  out.clipBits.assign(p, p + (frames + 7) / 8);
  p += (frames + 7) / 8;
  out.waveform.resize(static_cast<size_t>(wavePoints) * 2);
  std::memcpy(out.waveform.data(), p, out.waveform.size());
  return SEGUE_OK;
}

}  // namespace segue

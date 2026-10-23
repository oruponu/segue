# Visuals cache format (.sgv)

Written by `segue_visuals_write` and read by `segue_visuals_read`. The same format is used on Android and iOS. All numbers are little-endian.

## Header (28 bytes)

| Offset | Type | Field | Value |
|---|---|---|---|
| 0 | char[4] | magic | `SGVZ` |
| 4 | u16 | format_version | 1 |
| 6 | u16 | analysis_version | `segue_analysis_version()` (currently 1) |
| 8 | u32 | sample_rate | 44100 |
| 12 | u32 | hop | 1024 |
| 16 | u32 | frame_count | ≥ 1 |
| 20 | u16 | band_count | 32 |
| 22 | u16 | waveform_rate | 100 |
| 24 | u32 | waveform_count | ≥ 1 |

A file is invalid (`SEGUE_IO_FAILED`) if magic, format_version, sample_rate, hop, band_count or waveform_rate differs from the table, if analysis_version differs from the current one, if frame_count is 0 or above 77520 or waveform_count is 0 or above 180001 (the most that 30 minutes of audio produces), or if the file size is not `28 + body_size`:

```
body_size = frame_count × band_count + 2 × frame_count + (frame_count + 7) / 8 + 2 × waveform_count
```

## Body (in this order)

1. Spectrum: frame_count × band_count u8 values (frame by frame, each frame from the lowest band)
2. Peak L: frame_count u8 values
3. Peak R: frame_count u8 values
4. Clip: (frame_count + 7) / 8 bytes. Frame i is bit (i % 8) of byte i / 8, counting from the LSB. The bit is 1 if L or R clips
5. Waveform: waveform_count × 2 i8 values (min, then max for each point)

## Values

N is the number of mono samples at 44.1kHz (mono = (L + R) / 2).

- `frame_count = ceil(N / 1024)`. Frame i starts at `i × 1024 / 44100` seconds
- Peak: for frame i, the largest absolute sample in `[i·1024, (i+1)·1024)`, for L and R separately, converted to dB and quantized. The last frame treats missing samples as silence. An amplitude ≤ 1e-7 is -100dB
- Clip: 1 if the same range has a sample with |sample| ≥ 1.0
- Spectrum: for frame i, a 4096-sample Hann window centered on sample `i·1024` (samples outside the audio are 0). 20Hz to 20kHz is split into 32 log-spaced bands. Each band is a dB value with bandwidth normalization (relative to 1kHz) and a +4.5dB/oct slope correction. The exact computation is in `core/src/spectrum.cpp`
- dB quantization: `round((clamp(dB, -50, 0) + 50) × 255 / 50)`. The inverse is `level × 50 / 255 - 50`
- Waveform: `waveform_count = ceil(N / 441)`. Point j is the min and max of the mono samples in `[j·441, (j+1)·441)`, each stored as `round(clamp(x, -1, 1) × 127)`

When any of these definitions or parameters changes, bump `kAnalysisVersion` in `core/src/constants.h`.

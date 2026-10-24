#!/usr/bin/env python3
"""Generates the test audio fixtures in core/testdata.

Run with ffmpeg on PATH:
    python3 core/testdata/generate_fixtures.py
Uses only the standard library. With the same ffmpeg, the output is deterministic.
"""
import array
import math
import os
import subprocess
import sys
import tempfile
import wave

OUT_DIR = os.path.dirname(os.path.abspath(__file__))


def midi_freq(midi):
    return 440.0 * 2.0 ** ((midi - 69) / 12.0)


def synth(sr, seconds, bpm, chords):
    """Synthesizes a click track (accented downbeats) over a triad pad that changes every bar."""
    n = int(round(seconds * sr))
    beat_samples = 60.0 / bpm * sr
    click_len = int(0.02 * sr)
    out = [0.0] * n
    for i in range(n):
        t = i / sr
        beat = int(i / beat_samples)
        chord = chords[(beat // 4) % len(chords)]
        pad = 0.0
        for m in chord:
            pad += math.sin(2.0 * math.pi * midi_freq(m) * t)
        pad *= 0.12
        dt = i - int(round(beat * beat_samples))
        click = 0.0
        if 0 <= dt < click_len:
            amp = 0.6 if beat % 4 == 0 else 0.35
            click = amp * math.sin(2.0 * math.pi * 1000.0 * dt / sr) * math.exp(-dt / (0.004 * sr))
        out[i] = pad + click
    return out


def to_int16(x):
    # Decoders convert s16 to float by dividing by 32768, so scale by 32768: -1.0 becomes -32768
    # (exactly -1.0 after decoding), which lets hard-clipped negative samples trip the |x| >= 1.0 check.
    v = int(round(x * 32768.0))
    return max(-32768, min(32767, v))


def write_wav(path, sr, channels):
    n = len(channels[0])
    data = array.array("h", [0] * (n * len(channels)))
    for i in range(n):
        for c, ch in enumerate(channels):
            data[i * len(channels) + c] = to_int16(ch[i])
    if sys.byteorder != "little":
        data.byteswap()
    with wave.open(path, "wb") as w:
        w.setnchannels(len(channels))
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(data.tobytes())


def ffmpeg(*args):
    cmd = ["ffmpeg", "-y", "-loglevel", "error", *args]
    subprocess.run(cmd, check=True)


def encode(src, dst, *codec_args):
    ffmpeg("-i", src, "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
           *codec_args, dst)


def out(name):
    return os.path.join(OUT_DIR, name)


def main():
    a_minor = [[57, 60, 64], [62, 65, 69], [64, 68, 71], [57, 60, 64]]
    c_major = [[60, 64, 67], [65, 69, 72], [67, 71, 74], [60, 64, 67]]
    d_minor = [[62, 65, 69], [67, 70, 74], [69, 73, 76], [62, 65, 69]]

    base = synth(44100, 30.0, 120, a_minor)
    base_wav = out("click120_aminor_44k_stereo.wav")
    write_wav(base_wav, 44100, [base, [x * 0.8 for x in base]])
    encode(base_wav, out("click120_aminor_44k_stereo.mp3"), "-c:a", "libmp3lame", "-b:a", "192k")
    encode(base_wav, out("click120_aminor_44k_stereo.ogg"), "-c:a", "libvorbis", "-q:a", "5")
    encode(base_wav, out("click120_aminor_44k_stereo.opus"), "-c:a", "libopus", "-b:a", "128k")
    ffmpeg("-i", base_wav, "-t", "10", "-af",
           "pan=5.1|FL=c0|FR=c1|FC=0.5*c0+0.5*c1|LFE=0*c0|BL=c0|BR=c1",
           "-map_metadata", "-1", "-fflags", "+bitexact", "-c:a", "flac", out("surround51_44k.flac"))

    with tempfile.TemporaryDirectory() as tmp:
        chords = synth(48000, 30.0, 96, c_major)
        chords_wav = os.path.join(tmp, "chords.wav")
        write_wav(chords_wav, 48000, [chords, [x * 0.8 for x in chords]])
        encode(chords_wav, out("chords96_cmajor_48k_stereo.flac"), "-c:a", "flac")

        mono = synth(44100, 30.0, 100, d_minor)
        mono_wav = os.path.join(tmp, "mono.wav")
        write_wav(mono_wav, 44100, [mono])
        encode(mono_wav, out("click100_dminor_44k_mono.m4a"), "-c:a", "aac", "-b:a", "160k")

    sr = 44100
    left, right = [], []
    for i in range(10 * sr):
        t = i / sr
        s = math.sin(2.0 * math.pi * 440.0 * t)
        clipped = (2.0 <= t < 4.0) or (6.0 <= t < 7.0)
        left.append(max(-1.0, min(1.0, 1.3 * s)) if clipped else 0.5 * s)
        right.append(0.5 * s)
    write_wav(out("clip_lr_44k_stereo.wav"), sr, [left, right])

    tiny = [0.5 * math.sin(2.0 * math.pi * 440.0 * i / sr) for i in range(2000)]
    write_wav(out("tiny_2000_mono.wav"), sr, [tiny])

    odd_n = 1024 * 100 + 517
    odd = [0.5 * math.sin(2.0 * math.pi * 330.0 * i / sr) for i in range(odd_n)]
    write_wav(out("odd_length_stereo.wav"), sr, [odd, odd])

    write_wav(out("empty_stereo.wav"), sr, [[], []])

    with open(out("not_audio.mp3"), "w", encoding="ascii") as f:
        f.write("this is not an audio file\n" * 100)

    with open(out("click120_aminor_44k_stereo.mp3"), "rb") as f:
        data = bytearray(f.read())
    mid = len(data) // 2
    data[mid:mid + 4096] = bytes(4096)
    with open(out("corrupt_middle.mp3"), "wb") as f:
        f.write(data)

    # mp3 streams whose parameters change mid-stream: two 2-second encodes concatenated byte by byte
    with tempfile.TemporaryDirectory() as tmp:
        def mp3_part(name, part_sr, channel_count):
            tone = [0.5 * math.sin(2.0 * math.pi * 440.0 * i / part_sr) for i in range(2 * part_sr)]
            part_wav = os.path.join(tmp, name + ".wav")
            write_wav(part_wav, part_sr, [tone] * channel_count)
            part_mp3 = os.path.join(tmp, name + ".mp3")
            encode(part_wav, part_mp3, "-c:a", "libmp3lame", "-b:a", "64k", "-id3v2_version", "0",
                   "-write_xing", "0")
            with open(part_mp3, "rb") as f:
                return f.read()

        with open(out("stereo_then_mono_44k.mp3"), "wb") as f:
            f.write(mp3_part("stereo", 44100, 2) + mp3_part("mono", 44100, 1))
        with open(out("rate_change_mono.mp3"), "wb") as f:
            f.write(mp3_part("rate44", 44100, 1) + mp3_part("rate48", 48000, 1))

    ffmpeg("-f", "lavfi", "-i", "anullsrc=r=48000:cl=mono", "-t", "1860",
           "-map_metadata", "-1", "-fflags", "+bitexact", "-c:a", "libopus", "-b:a", "6k",
           out("silence_31min.opus"))


if __name__ == "__main__":
    main()

use std::fs::File;
use std::path::Path;
use std::sync::atomic::{AtomicU64, Ordering};

use anyhow::Context;
use stratum_dsp::{AnalysisConfig, Key};
use symphonia::core::audio::{Channels, SampleBuffer};
use symphonia::core::codecs::DecoderOptions;
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

static ANALYZE_GENERATION: AtomicU64 = AtomicU64::new(0);

pub struct AnalysisResult {
    pub bpm: f32,
    pub bpm_confidence: f32,
    pub key: String,
    pub key_confidence: f32,
}

struct AudioData {
    samples: Vec<f32>,
    sample_rate: u32,
}

pub fn analyze(path_str: String) -> anyhow::Result<Option<AnalysisResult>> {
    let generation = ANALYZE_GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    let path = Path::new(&path_str);
    let audio = match decode_audio(path, generation)
        .with_context(|| format!("failed to decode audio: {}", path.display()))?
    {
        Some(audio) => audio,
        None => return Ok(None),
    };
    if ANALYZE_GENERATION.load(Ordering::SeqCst) != generation {
        return Ok(None);
    }

    let result =
        stratum_dsp::analyze_audio(&audio.samples, audio.sample_rate, AnalysisConfig::default())
            .with_context(|| format!("failed to analyze audio: {}", path.display()))?;
    if ANALYZE_GENERATION.load(Ordering::SeqCst) != generation {
        return Ok(None);
    }

    Ok(Some(AnalysisResult {
        bpm: result.bpm,
        bpm_confidence: result.bpm_confidence,
        key: key_to_string(result.key),
        key_confidence: result.key_confidence,
    }))
}

pub fn cancel_analyze() {
    ANALYZE_GENERATION.fetch_add(1, Ordering::SeqCst);
}

fn decode_audio(path: &Path, generation: u64) -> anyhow::Result<Option<AudioData>> {
    let file = File::open(path)
        .with_context(|| format!("failed to open audio file: {}", path.display()))?;
    let source = MediaSourceStream::new(Box::new(file), Default::default());

    let mut hint = Hint::new();
    if let Some(extension) = path.extension().and_then(|e| e.to_str()) {
        hint.with_extension(extension);
    }

    let probe_result = symphonia::default::get_probe()
        .format(
            &hint,
            source,
            &FormatOptions::default(),
            &MetadataOptions::default(),
        )
        .with_context(|| format!("failed to probe audio format: {}", path.display()))?;
    let mut reader = probe_result.format;

    let track = reader
        .default_track()
        .ok_or_else(|| anyhow::anyhow!("no supported audio track found: '{}'", path.display()))?;
    let sample_rate = track
        .codec_params
        .sample_rate
        .ok_or_else(|| anyhow::anyhow!("missing sample rate: '{}'", path.display()))?;
    let channel_count = track
        .codec_params
        .channels
        .map(|ch: Channels| ch.count())
        .unwrap_or(1);
    let track_id = track.id;

    let mut decoder = symphonia::default::get_codecs()
        .make(&track.codec_params, &DecoderOptions::default())
        .with_context(|| "failed to create decoder")?;
    let mut all_samples: Vec<f32> = Vec::new();

    loop {
        if ANALYZE_GENERATION.load(Ordering::SeqCst) != generation {
            return Ok(None);
        }

        let packet = match reader.next_packet() {
            Ok(packet) => packet,
            Err(SymphoniaError::IoError(_)) => break,
            Err(e) => return Err(e.into()),
        };
        if packet.track_id() != track_id {
            continue;
        }

        let audio_frames = match decoder.decode(&packet) {
            Ok(frames) => frames,
            Err(SymphoniaError::DecodeError(_)) => continue,
            Err(e) => return Err(e).context("failed to decode packet"),
        };
        let spec = *audio_frames.spec();
        let mut sample_buf = SampleBuffer::<f32>::new(audio_frames.capacity() as u64, spec);
        sample_buf.copy_interleaved_ref(audio_frames);
        let raw_samples = sample_buf.samples();

        if channel_count == 1 {
            all_samples.extend_from_slice(raw_samples);
        } else {
            for frame in raw_samples.chunks(channel_count) {
                let mono = frame.iter().sum::<f32>() / channel_count as f32;
                all_samples.push(mono);
            }
        }
    }

    Ok(Some(AudioData {
        samples: all_samples,
        sample_rate,
    }))
}

fn key_to_string(key: stratum_dsp::Key) -> String {
    const NOTE_NAMES: [&str; 12] = [
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
    ];
    let (note_index, mode_str) = match key {
        Key::Major(n) => (n as usize, "Major"),
        Key::Minor(n) => (n as usize, "Minor"),
    };
    let note_name = NOTE_NAMES.get(note_index).copied().unwrap_or("Unknown");
    format!("{} {}", note_name, mode_str)
}

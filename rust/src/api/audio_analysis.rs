use std::fs::File;
use std::path::Path;

use anyhow::Context;
use symphonia::core::audio::{Channels, SampleBuffer};
use symphonia::core::codecs::DecoderOptions;
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

pub struct AudioData {
    pub samples: Vec<f32>,
    pub sample_rate: u32,
}

pub fn decode_audio(path_str: String) -> anyhow::Result<AudioData> {
    let path = Path::new(&path_str);
    let file =
        File::open(path).with_context(|| format!("failed to open audio file: {}", path_str))?;
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
        .with_context(|| format!("failed to probe audio format: {}", path_str))?;
    let mut reader = probe_result.format;

    let track = reader
        .default_track()
        .ok_or_else(|| anyhow::anyhow!("no supported audio track found: '{}'", path_str))?;
    let sample_rate = track
        .codec_params
        .sample_rate
        .ok_or_else(|| anyhow::anyhow!("missing sample rate: '{}'", path_str))?;
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
        let packet = match reader.next_packet() {
            Ok(packet) => packet,
            Err(SymphoniaError::IoError(_)) => break,
            Err(e) => return Err(e.into()),
        };
        if packet.track_id() != track_id {
            continue;
        }

        let audio_frames = decoder.decode(&packet).context("failed to decode packet")?;
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

    Ok(AudioData {
        samples: all_samples,
        sample_rate,
    })
}

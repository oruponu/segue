import 'dart:io';
import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_waveform/just_waveform.dart';
import 'package:segue/providers/audio_handler_provider.dart';

final waveformProvider = FutureProvider.autoDispose<Waveform?>((ref) async {
  final item = ref.watch(_mediaItemProvider).value;
  if (item == null) return null;

  final wavePath = item.extras?['wavePath'] as String?;
  if (wavePath == null || wavePath.isEmpty) return null;

  final file = File(wavePath);
  if (!await file.exists()) {
    try {
      await JustWaveform.extract(
        audioInFile: File(item.id),
        waveOutFile: file,
      ).drain();
    } catch (_) {
      return null;
    }
  }

  return await JustWaveform.parse(file);
});

final _mediaItemProvider = StreamProvider<MediaItem?>((ref) {
  final handler = ref.watch(audioHandlerProvider);
  return handler.mediaItem;
});

import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_waveform/just_waveform.dart';
import 'package:segue/providers/audio_handler_provider.dart';

final waveformProvider = StreamProvider.autoDispose<Waveform?>((ref) async* {
  final handler = ref.watch(audioHandlerProvider);

  await for (final item in handler.mediaItem) {
    final wavePath = item?.extras?['wavePath'] as String?;
    if (wavePath == null || wavePath.isEmpty) {
      yield null;
      continue;
    }

    final file = File(wavePath);
    if (!await file.exists()) {
      yield null;
      continue;
    }

    yield await JustWaveform.parse(file);
  }
});

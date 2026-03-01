import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/src/native/audio_analysis.dart';

final stereoPeakProvider = FutureProvider.autoDispose<StereoPeakResult?>((
  ref,
) async {
  final item = ref.watch(_mediaItemProvider).value;
  if (item == null) return null;

  final path = item.id;
  if (path.isEmpty) return null;

  ref.onDispose(() {
    AudioAnalysis.cancelComputeStereoPeaks();
  });

  return await AudioAnalysis.computeStereoPeaks(pathStr: path);
});

final _mediaItemProvider = StreamProvider<MediaItem?>((ref) {
  final handler = ref.watch(audioHandlerProvider);
  return handler.mediaItem;
});

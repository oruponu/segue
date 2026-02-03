import 'package:audio_video_progress_bar/audio_video_progress_bar.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/providers/position_provider.dart';

class ProgressBarWidget extends ConsumerWidget {
  const ProgressBarWidget({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final positionDataAsync = ref.watch(positionProvider);
    final handler = ref.read(audioHandlerProvider);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: positionDataAsync.when(
        data: (data) => ProgressBar(
          progress: data.position,
          buffered: data.bufferedPosition,
          total: data.duration,
          progressBarColor: Colors.white,
          baseBarColor: Colors.white.withValues(alpha: .24),
          bufferedBarColor: Colors.white.withValues(alpha: .24),
          thumbColor: Colors.white,
          barHeight: 3.0,
          thumbRadius: 6.0,
          onSeek: (duration) {
            handler.seek(duration);
          },
        ),
        error: (_, _) => const SizedBox(),
        loading: () =>
            const ProgressBar(progress: Duration.zero, total: Duration.zero),
      ),
    );
  }
}

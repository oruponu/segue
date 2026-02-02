import 'dart:io';
import 'package:audio_service/audio_service.dart' hide AudioHandler;
import 'package:audio_video_progress_bar/audio_video_progress_bar.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import '../../providers/audio_handler_provider.dart';
import '../../providers/position_provider.dart';

class MiniPlayer extends ConsumerWidget {
  const MiniPlayer({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final handler = ref.watch(audioHandlerProvider);

    return StreamBuilder<MediaItem?>(
      stream: handler.mediaItem,
      builder: (context, snapshot) {
        final mediaItem = snapshot.data;
        if (mediaItem == null) {
          return const SizedBox.shrink();
        }

        return Container(
          color: Theme.of(
            context,
          ).colorScheme.surfaceContainerHighest.withValues(alpha: 0.95),
          child: SafeArea(
            top: false,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildProgressBar(context, ref),
                SizedBox(
                  height: 72,
                  child: Row(
                    children: [
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: _buildArtwork(mediaItem.artUri),
                      ),

                      Expanded(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              mediaItem.title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: Theme.of(context).textTheme.titleSmall,
                            ),
                            Text(
                              mediaItem.artist ?? "Unknown Artist",
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                          ],
                        ),
                      ),

                      StreamBuilder<PlaybackState>(
                        stream: handler.playbackState,
                        builder: (context, snapshot) {
                          final playing = snapshot.data?.playing ?? false;
                          return IconButton(
                            onPressed: playing ? handler.pause : handler.play,
                            icon: Icon(
                              playing ? Icons.pause : Icons.play_arrow,
                            ),
                          );
                        },
                      ),

                      IconButton(
                        icon: const Icon(Icons.skip_next),
                        onPressed: handler.skipToNext,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    ); // Placeholder
  }

  Widget _buildProgressBar(BuildContext context, WidgetRef ref) {
    final positionDataAsync = ref.watch(positionProvider);
    return positionDataAsync.when(
      data: (data) => ProgressBar(
        progress: data.position,
        buffered: data.bufferedPosition,
        total: data.duration,
        progressBarColor: Theme.of(context).colorScheme.primary,
        baseBarColor: Colors.transparent,
        bufferedBarColor: Colors.white.withValues(alpha: 0.1),
        thumbColor: Colors.transparent,
        thumbRadius: 0,
        barHeight: 2.0,
        timeLabelLocation: TimeLabelLocation.none,
        onSeek: null,
      ),
      error: (_, _) => const SizedBox(height: 2.0),
      loading: () => const SizedBox(height: 2.0),
    );
  }

  Widget _buildArtwork(Uri? artUri) {
    if (artUri != null) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: Image.file(
          File.fromUri(artUri),
          width: 48,
          height: 48,
          fit: BoxFit.cover,
        ),
      );
    }
    return Container(
      width: 48,
      height: 48,
      color: Colors.grey[800],
      child: const Icon(Icons.music_note),
    );
  }
}

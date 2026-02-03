import 'package:audio_service/audio_service.dart' hide AudioHandler;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';
import 'package:segue/providers/audio_handler_provider.dart';

class PlaybackControls extends ConsumerWidget {
  const PlaybackControls({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final handler = ref.watch(audioHandlerProvider);

    return StreamBuilder<PlayerState>(
      stream: handler.playerStateStream,
      builder: (context, snapshot) {
        final playerState = snapshot.data;
        final processingState = playerState?.processingState;
        final playing = playerState?.playing;

        return Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            StreamBuilder<bool>(
              stream: handler.shuffleModeEnabledStream,
              builder: (context, snapshot) {
                final enabled = snapshot.data ?? false;
                return IconButton(
                  icon: Icon(
                    Icons.shuffle,
                    color: enabled
                        ? Theme.of(context).colorScheme.primary
                        : null,
                  ),
                  onPressed: () async {
                    final newMode = enabled
                        ? AudioServiceShuffleMode.none
                        : AudioServiceShuffleMode.all;
                    await handler.setShuffleMode(newMode);
                  },
                );
              },
            ),

            IconButton(
              icon: const Icon(Icons.skip_previous),
              iconSize: 48,
              onPressed: () {
                if (handler.hasPrevious) {
                  handler.skipToPrevious();
                } else {
                  handler.seek(Duration.zero);
                }
              },
            ),

            _buildPlayPauseButton(processingState, playing, handler),

            IconButton(
              icon: const Icon(Icons.skip_next),
              iconSize: 48,
              onPressed: handler.hasNext ? handler.skipToNext : null,
            ),

            StreamBuilder<LoopMode>(
              stream: handler.loopModeStream,
              builder: (context, snapshot) {
                final loopMode = snapshot.data ?? LoopMode.off;
                IconData icon;
                Color? color;
                switch (loopMode) {
                  case LoopMode.off:
                    icon = Icons.repeat;
                    color = null;
                    break;
                  case LoopMode.one:
                    icon = Icons.repeat_one;
                    color = Theme.of(context).colorScheme.primary;
                    break;
                  case LoopMode.all:
                    icon = Icons.repeat;
                    color = Theme.of(context).colorScheme.primary;
                    break;
                }

                return IconButton(
                  icon: Icon(icon, color: color),
                  onPressed: () {
                    final newMode = switch (loopMode) {
                      LoopMode.off => AudioServiceRepeatMode.all,
                      LoopMode.all => AudioServiceRepeatMode.one,
                      LoopMode.one => AudioServiceRepeatMode.none,
                    };
                    handler.setRepeatMode(newMode);
                  },
                );
              },
            ),
          ],
        );
      },
    );
  }

  Widget _buildPlayPauseButton(
    ProcessingState? state,
    bool? playing,
    AudioHandler handler,
  ) {
    if (playing != true) {
      return IconButton(
        icon: const Icon(Icons.play_circle_fill),
        iconSize: 64,
        onPressed: handler.play,
      );
    } else if (state != ProcessingState.completed) {
      return IconButton(
        icon: const Icon(Icons.pause_circle_filled),
        iconSize: 64,
        onPressed: handler.pause,
      );
    } else {
      return IconButton(
        icon: const Icon(Icons.replay_circle_filled),
        iconSize: 64,
        onPressed: () => handler.seek(Duration.zero),
      );
    }
  }
}

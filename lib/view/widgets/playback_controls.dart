import 'dart:math' as math;
import 'package:audio_service/audio_service.dart' hide AudioHandler;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:just_audio/just_audio.dart';
import 'package:segue/providers/audio_handler_provider.dart';

const _speedPresets = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
const _semitonePresets = [-6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6];

double _semitonesToPitch(int semitones) =>
    math.pow(2, semitones / 12).toDouble();

int _pitchToSemitones(double pitch) =>
    (12 * math.log(pitch) / math.ln2).round();

String _semitoneLabel(int semitones) {
  if (semitones == 0) return '0';
  if (semitones > 0) return '+$semitones';
  return '$semitones';
}

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

        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
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
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              spacing: 32,
              children: [
                StreamBuilder<double>(
                  stream: handler.speedStream,
                  builder: (context, snapshot) {
                    final speed = snapshot.data ?? 1.0;
                    final isDefault = speed == 1.0;
                    return GestureDetector(
                      onLongPress: isDefault
                          ? null
                          : () {
                              HapticFeedback.mediumImpact();
                              handler.setSpeed(1.0);
                            },
                      child: Material(
                        color: Colors.transparent,
                        borderRadius: BorderRadius.circular(8),
                        clipBehavior: Clip.antiAlias,
                        child: PopupMenuButton<double>(
                          tooltip: '',
                          itemBuilder: (context) => _speedPresets
                              .map(
                                (s) => PopupMenuItem(
                                  value: s,
                                  child: Text(
                                    '${s}x',
                                    style: TextStyle(
                                      color: s == speed
                                          ? Theme.of(
                                              context,
                                            ).colorScheme.primary
                                          : null,
                                      fontWeight: s == speed
                                          ? FontWeight.bold
                                          : null,
                                    ),
                                  ),
                                ),
                              )
                              .toList(),
                          onSelected: (value) => handler.setSpeed(value),
                          child: SizedBox(
                            width: 72,
                            child: Padding(
                              padding: const EdgeInsets.symmetric(vertical: 8),
                              child: Text(
                                '${speed}x',
                                style: TextStyle(
                                  color: isDefault
                                      ? null
                                      : Theme.of(context).colorScheme.primary,
                                ),
                                textAlign: TextAlign.center,
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),
                StreamBuilder<double>(
                  stream: handler.pitchStream,
                  builder: (context, snapshot) {
                    final pitch = snapshot.data ?? 1.0;
                    final semitones = _pitchToSemitones(pitch);
                    final isDefault = semitones == 0;
                    return GestureDetector(
                      onLongPress: isDefault
                          ? null
                          : () {
                              HapticFeedback.mediumImpact();
                              handler.setPitch(1.0);
                            },
                      child: Material(
                        color: Colors.transparent,
                        borderRadius: BorderRadius.circular(8),
                        clipBehavior: Clip.antiAlias,
                        child: PopupMenuButton<int>(
                          tooltip: '',
                          itemBuilder: (context) => _semitonePresets
                              .map(
                                (s) => PopupMenuItem(
                                  value: s,
                                  child: Text(
                                    _semitoneLabel(s),
                                    style: TextStyle(
                                      color: s == semitones
                                          ? Theme.of(
                                              context,
                                            ).colorScheme.primary
                                          : null,
                                      fontWeight: s == semitones
                                          ? FontWeight.bold
                                          : null,
                                    ),
                                  ),
                                ),
                              )
                              .toList(),
                          onSelected: (value) =>
                              handler.setPitch(_semitonesToPitch(value)),
                          child: SizedBox(
                            width: 72,
                            child: Padding(
                              padding: const EdgeInsets.symmetric(vertical: 8),
                              child: Text(
                                'Key ${_semitoneLabel(semitones)}',
                                style: TextStyle(
                                  color: isDefault
                                      ? null
                                      : Theme.of(context).colorScheme.primary,
                                ),
                                textAlign: TextAlign.center,
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ],
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

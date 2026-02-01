import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';
import 'audio_player_provider.dart';

final audioHandlerProvider = Provider<AudioHandler>((ref) {
  return ref.watch(_audioHandlerInternalProvider).requireValue;
});

final _audioHandlerInternalProvider = FutureProvider<AudioHandler>((ref) async {
  final player = ref.watch(audioPlayerProvider);
  return await AudioService.init(
    builder: () => AudioHandler(player),
    config: const AudioServiceConfig(
      androidNotificationChannelId: "com.oruponu.segue.playback",
      androidNotificationChannelName: "Audio Playback",
      androidNotificationOngoing: true,
      androidStopForegroundOnPause: true,
    ),
  );
});

final mediaItemProvider = StreamProvider<MediaItem?>((ref) {
  final handler = ref.watch(audioHandlerProvider);
  return handler.mediaItem;
});

class AudioHandler extends BaseAudioHandler {
  final AudioPlayer _player;

  AudioHandler(this._player) {
    _player.playerStateStream.listen((state) {
      playbackState.add(
        playbackState.value.copyWith(
          processingState:
              const {
                ProcessingState.idle: AudioProcessingState.idle,
                ProcessingState.loading: AudioProcessingState.loading,
                ProcessingState.buffering: AudioProcessingState.buffering,
                ProcessingState.ready: AudioProcessingState.ready,
                ProcessingState.completed: AudioProcessingState.completed,
              }[state.processingState] ??
              AudioProcessingState.idle,
          playing: state.playing,
          controls: [
            MediaControl.skipToPrevious,
            state.playing ? MediaControl.pause : MediaControl.play,
            MediaControl.stop,
            MediaControl.skipToNext,
          ],
          androidCompactActionIndices: const [1, 3],
          systemActions: {MediaAction.seek},
          updatePosition: _player.position,
          bufferedPosition: _player.bufferedPosition,
          speed: _player.speed,
        ),
      );
    });

    _player.sequenceStateStream.listen((sequenceState) {
      final currentItem = sequenceState.currentSource?.tag as MediaItem?;
      if (currentItem != null) {
        mediaItem.add(currentItem);
      }
    });
  }

  @override
  Future<void> play() async => await _player.play();

  @override
  Future<void> pause() async => await _player.pause();

  @override
  Future<void> updateQueue(List<MediaItem> queue) async {
    this.queue.add(queue);
    final audioSource = queue
        .map((item) => AudioSource.uri(Uri.parse(item.id), tag: item))
        .toList();
    await _player.setAudioSources(audioSource);
  }

  @override
  Future<void> skipToQueueItem(int index) async =>
      await _player.seek(Duration.zero, index: index);

  @override
  Future<void> seek(Duration position) async => await _player.seek(position);
}

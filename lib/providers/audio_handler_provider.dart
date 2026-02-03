import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';

final audioHandlerProvider = Provider<AudioHandler>((ref) {
  return ref.watch(audioHandlerFutureProvider).requireValue;
});

final audioHandlerFutureProvider = FutureProvider<AudioHandler>((ref) async {
  final player = AudioPlayer();
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

class AudioHandler extends BaseAudioHandler {
  final AudioPlayer _player;

  AudioHandler(this._player) {
    _player.playbackEventStream.listen((event) {
      playbackState.add(
        playbackState.value.copyWith(
          processingState:
              const {
                ProcessingState.idle: AudioProcessingState.idle,
                ProcessingState.loading: AudioProcessingState.loading,
                ProcessingState.buffering: AudioProcessingState.buffering,
                ProcessingState.ready: AudioProcessingState.ready,
                ProcessingState.completed: AudioProcessingState.completed,
              }[_player.processingState] ??
              AudioProcessingState.idle,
          playing: _player.playing,
          controls: [
            MediaControl.skipToPrevious,
            _player.playing ? MediaControl.pause : MediaControl.play,
            MediaControl.skipToNext,
          ],
          androidCompactActionIndices: const [1, 2],
          systemActions: {MediaAction.seek},
          updatePosition: _player.position,
          bufferedPosition: _player.bufferedPosition,
          speed: _player.speed,
          repeatMode:
              const {
                LoopMode.off: AudioServiceRepeatMode.none,
                LoopMode.one: AudioServiceRepeatMode.one,
                LoopMode.all: AudioServiceRepeatMode.all,
              }[_player.loopMode] ??
              AudioServiceRepeatMode.none,
          shuffleMode: _player.shuffleModeEnabled
              ? AudioServiceShuffleMode.all
              : AudioServiceShuffleMode.none,
          queueIndex: event.currentIndex,
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

  Stream<Duration?> get durationStream => _player.durationStream;

  Stream<Duration> get bufferedPositionStream => _player.bufferedPositionStream;

  Stream<PlayerState> get playerStateStream => _player.playerStateStream;

  bool get hasNext => _player.hasNext;

  bool get hasPrevious => _player.hasPrevious;

  Stream<LoopMode> get loopModeStream => _player.loopModeStream;

  Stream<bool> get shuffleModeEnabledStream => _player.shuffleModeEnabledStream;

  Stream<Duration> get positionStream => _player.positionStream;

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
  Future<void> skipToNext() async => await _player.seekToNext();

  @override
  Future<void> skipToPrevious() async => await _player.seekToPrevious();

  @override
  Future<void> skipToQueueItem(int index) async =>
      await _player.seek(Duration.zero, index: index);

  @override
  Future<void> seek(Duration position) async => await _player.seek(position);

  @override
  Future<void> setRepeatMode(AudioServiceRepeatMode repeatMode) async {
    final loopMode =
        const {
          AudioServiceRepeatMode.none: LoopMode.off,
          AudioServiceRepeatMode.one: LoopMode.one,
          AudioServiceRepeatMode.all: LoopMode.all,
        }[repeatMode] ??
        LoopMode.off;
    await _player.setLoopMode(loopMode);
  }

  @override
  Future<void> setShuffleMode(AudioServiceShuffleMode shuffleMode) async {
    final enabled = shuffleMode == AudioServiceShuffleMode.all;
    await _player.setShuffleModeEnabled(enabled);
  }
}

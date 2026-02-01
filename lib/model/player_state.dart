import 'package:audio_service/audio_service.dart';

class PlayerState {
  final String? selectedDirectory;
  final bool isLoading;
  final MediaItem? playingMediaItem;

  PlayerState({
    this.selectedDirectory,
    this.isLoading = false,
    this.playingMediaItem,
  });

  PlayerState copyWith({
    String? selectedDirectory,
    bool? isLoading,
    MediaItem? playingMediaItem,
  }) {
    return PlayerState(
      selectedDirectory: selectedDirectory ?? this.selectedDirectory,
      isLoading: isLoading ?? this.isLoading,
      playingMediaItem: playingMediaItem ?? this.playingMediaItem,
    );
  }
}

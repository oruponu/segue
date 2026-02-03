import 'package:audio_service/audio_service.dart';

class LibraryState {
  final List<MediaItem> playlist;
  final bool isLoading;
  final String? selectedDirectory;
  final MediaItem? playingMediaItem;
  final bool isPlaying;

  LibraryState({
    this.playlist = const [],
    this.isLoading = false,
    this.selectedDirectory,
    this.playingMediaItem,
    this.isPlaying = false,
  });

  LibraryState copyWith({
    List<MediaItem>? playlist,
    bool? isLoading,
    String? selectedDirectory,
    MediaItem? playingMediaItem,
    bool? isPlaying,
  }) {
    return LibraryState(
      playlist: playlist ?? this.playlist,
      isLoading: isLoading ?? this.isLoading,
      selectedDirectory: selectedDirectory ?? this.selectedDirectory,
      playingMediaItem: playingMediaItem ?? this.playingMediaItem,
      isPlaying: isPlaying ?? this.isPlaying,
    );
  }
}

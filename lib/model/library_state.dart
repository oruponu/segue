import 'package:audio_service/audio_service.dart';

class LibraryState {
  final List<MediaItem> playlist;
  final bool isLoading;
  final String? selectedDirectory;
  final MediaItem? playingMediaItem;

  LibraryState({
    this.playlist = const [],
    this.isLoading = false,
    this.selectedDirectory,
    this.playingMediaItem,
  });

  LibraryState copyWith({
    List<MediaItem>? playlist,
    bool? isLoading,
    String? selectedDirectory,
    MediaItem? playingMediaItem,
  }) {
    return LibraryState(
      playlist: playlist ?? this.playlist,
      isLoading: isLoading ?? this.isLoading,
      selectedDirectory: selectedDirectory ?? this.selectedDirectory,
      playingMediaItem: playingMediaItem ?? this.playingMediaItem,
    );
  }
}

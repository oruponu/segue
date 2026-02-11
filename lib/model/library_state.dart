import 'package:audio_service/audio_service.dart';
import 'package:segue/model/album.dart';

class LibraryState {
  final List<MediaItem> playlist;
  final bool isLoading;
  final List<Album> albums;
  final Album? selectedAlbum;
  final String? selectedDirectory;
  final MediaItem? playingMediaItem;
  final bool isPlaying;

  LibraryState({
    this.playlist = const [],
    this.isLoading = false,
    this.albums = const [],
    this.selectedAlbum,
    this.selectedDirectory,
    this.playingMediaItem,
    this.isPlaying = false,
  });

  LibraryState copyWith({
    List<MediaItem>? playlist,
    bool? isLoading,
    List<Album>? albums,
    Album? selectedAlbum,
    bool clearSelectedAlbum = false,
    String? selectedDirectory,
    MediaItem? playingMediaItem,
    bool? isPlaying,
  }) {
    return LibraryState(
      playlist: playlist ?? this.playlist,
      isLoading: isLoading ?? this.isLoading,
      albums: albums ?? this.albums,
      selectedAlbum: clearSelectedAlbum
          ? null
          : (selectedAlbum ?? this.selectedAlbum),
      selectedDirectory: selectedDirectory ?? this.selectedDirectory,
      playingMediaItem: playingMediaItem ?? this.playingMediaItem,
      isPlaying: isPlaying ?? this.isPlaying,
    );
  }
}

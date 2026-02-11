import 'package:audio_service/audio_service.dart';
import 'package:segue/model/album.dart';

class LibraryState {
  final List<MediaItem> playlist;
  final bool isLoading;
  final int scanTotal;
  final int scanProcessed;
  final List<Album> albums;
  final Album? selectedAlbum;
  final String? selectedDirectory;
  final MediaItem? playingMediaItem;
  final bool isPlaying;

  LibraryState({
    this.playlist = const [],
    this.isLoading = false,
    this.scanTotal = 0,
    this.scanProcessed = 0,
    this.albums = const [],
    this.selectedAlbum,
    this.selectedDirectory,
    this.playingMediaItem,
    this.isPlaying = false,
  });

  LibraryState copyWith({
    List<MediaItem>? playlist,
    bool? isLoading,
    int? scanTotal,
    int? scanProcessed,
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
      scanTotal: scanTotal ?? this.scanTotal,
      scanProcessed: scanProcessed ?? this.scanProcessed,
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

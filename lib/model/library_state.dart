import 'package:audio_metadata_reader/audio_metadata_reader.dart';

class LibraryState {
  final List<AudioMetadata> playlist;
  final bool isLoading;
  final String? selectedDirectory;
  final AudioMetadata? playingMetadata;

  LibraryState({
    this.playlist = const [],
    this.isLoading = false,
    this.selectedDirectory,
    this.playingMetadata,
  });

  LibraryState copyWith({
    List<AudioMetadata>? playlist,
    bool? isLoading,
    String? selectedDirectory,
    AudioMetadata? playingMetadata,
  }) {
    return LibraryState(
      playlist: playlist ?? this.playlist,
      isLoading: isLoading ?? this.isLoading,
      selectedDirectory: selectedDirectory ?? this.selectedDirectory,
      playingMetadata: playingMetadata ?? this.playingMetadata,
    );
  }
}

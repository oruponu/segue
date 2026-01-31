import 'package:audio_metadata_reader/audio_metadata_reader.dart';

class MusicPlayerState {
  final String? selectedDirectory;
  final bool isLoading;
  final AudioMetadata? currentMetadata;

  MusicPlayerState({
    this.selectedDirectory,
    this.isLoading = false,
    this.currentMetadata,
  });

  MusicPlayerState copyWith({
    String? selectedDirectory,
    bool? isLoading,
    AudioMetadata? currentMetadata,
  }) {
    return MusicPlayerState(
      selectedDirectory: selectedDirectory ?? this.selectedDirectory,
      isLoading: isLoading ?? this.isLoading,
      currentMetadata: currentMetadata ?? this.currentMetadata,
    );
  }
}

import 'package:audio_metadata_reader/audio_metadata_reader.dart';

class PlayerState {
  final String? selectedDirectory;
  final bool isLoading;
  final AudioMetadata? currentMetadata;

  PlayerState({
    this.selectedDirectory,
    this.isLoading = false,
    this.currentMetadata,
  });

  PlayerState copyWith({
    String? selectedDirectory,
    bool? isLoading,
    AudioMetadata? currentMetadata,
  }) {
    return PlayerState(
      selectedDirectory: selectedDirectory ?? this.selectedDirectory,
      isLoading: isLoading ?? this.isLoading,
      currentMetadata: currentMetadata ?? this.currentMetadata,
    );
  }
}

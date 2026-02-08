import 'package:audio_service/audio_service.dart';

class PlayerState {
  final String? selectedDirectory;
  final bool isLoading;
  final MediaItem? playingMediaItem;
  final bool isAnalyzing;
  final double? bpm;
  final String? key;

  PlayerState({
    this.selectedDirectory,
    this.isLoading = false,
    this.playingMediaItem,
    this.isAnalyzing = false,
    this.bpm,
    this.key,
  });

  PlayerState copyWith({
    String? selectedDirectory,
    bool? isLoading,
    MediaItem? playingMediaItem,
    bool? isAnalyzing,
    double? bpm,
    String? key,
  }) {
    return PlayerState(
      selectedDirectory: selectedDirectory ?? this.selectedDirectory,
      isLoading: isLoading ?? this.isLoading,
      playingMediaItem: playingMediaItem ?? this.playingMediaItem,
      isAnalyzing: isAnalyzing ?? this.isAnalyzing,
      bpm: bpm ?? this.bpm,
      key: key ?? this.key,
    );
  }
}

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:segue/src/native/audio_analysis.dart';
import 'package:segue/view_model/player_view_model.dart';

class AnalysisState {
  final bool isAnalyzing;
  final double? bpm;
  final String? key;
  final List<StylePrediction>? styles;

  const AnalysisState({
    this.isAnalyzing = false,
    this.bpm,
    this.key,
    this.styles,
  });
}

final analysisViewModelProvider = Provider<AnalysisState>((ref) {
  // TODO: player_view_model への依存を解消する
  final player = ref.watch(playerViewModelProvider);
  return AnalysisState(
    isAnalyzing: player.isAnalyzing,
    bpm: player.bpm,
    key: player.key,
    styles: player.styles,
  );
});

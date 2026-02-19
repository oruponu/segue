import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:rxdart/rxdart.dart';
import 'package:segue/model/player_state.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/providers/database_provider.dart';
import 'package:segue/src/native/audio_analysis.dart';
import 'package:segue/src/native/model_manager.dart';

final playerViewModelProvider = NotifierProvider<PlayerViewModel, PlayerState>(
  () {
    return PlayerViewModel();
  },
);

class PlayerViewModel extends Notifier<PlayerState> {
  @override
  PlayerState build() {
    final handler = ref.watch(audioHandlerProvider);
    handler.mediaItem
        .distinct((previous, next) => previous?.id == next?.id)
        .whereType<MediaItem>()
        .listen((item) async {
          state = PlayerState(playingMediaItem: item, isAnalyzing: true);

          final dao = ref.read(trackDaoProvider);
          final cached = await dao.getTrackByPath(item.id);
          if (state.playingMediaItem?.id != item.id) return;

          if (cached != null && cached.analyzedAt != null) {
            final styles = cached.stylesJson != null
                ? StylePrediction.listFromJson(cached.stylesJson!)
                : null;
            state = state.copyWith(
              bpm: cached.bpm,
              key: cached.musicalKey,
              styles: styles,
              isAnalyzing: false,
            );
            return;
          }

          final result = await AudioAnalysis.analyze(pathStr: item.id);
          if (state.playingMediaItem?.id != item.id) return;
          if (result == null) {
            state = state.copyWith(isAnalyzing: false);
            return;
          }

          state = state.copyWith(bpm: result.bpm, key: result.key);

          await dao.saveAnalysisResult(
            filePath: item.id,
            bpm: result.bpm,
            bpmConfidence: result.bpmConfidence,
            musicalKey: result.key,
            keyConfidence: result.keyConfidence,
          );
          if (state.playingMediaItem?.id != item.id) return;

          await _classifyStyle(item.id);
          if (state.playingMediaItem?.id != item.id) return;
          state = state.copyWith(isAnalyzing: false);
        });

    return PlayerState(playingMediaItem: null);
  }

  Future<void> _classifyStyle(String audioPath) async {
    try {
      final modelPath = await ModelManager.ensureModel(
        'models/discogs-effnet-bsdynamic-1.onnx',
      );
      if (state.playingMediaItem?.id != audioPath) return;

      final styles = await AudioAnalysis.classifyStyle(
        pathStr: audioPath,
        modelPath: modelPath,
      );
      if (state.playingMediaItem?.id != audioPath) return;

      if (styles == null) return;

      state = state.copyWith(styles: styles);

      final dao = ref.read(trackDaoProvider);
      await dao.saveStylePredictions(
        filePath: audioPath,
        stylesJson: StylePrediction.listToJson(styles),
      );
    } catch (_) {
      // 分類失敗時は styles を null のままにする
    }
  }
}

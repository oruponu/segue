import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:rxdart/rxdart.dart';
import 'package:segue/model/player_state.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/src/native/audio_analysis.dart';

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
          final result = await AudioAnalysis.analyze(pathStr: item.id);
          if (result == null) {
            return;
          }

          state = state.copyWith(
            bpm: result.bpm,
            key: result.key,
            isAnalyzing: false,
          );
        });

    return PlayerState(playingMediaItem: null);
  }
}

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:segue/model/player_state.dart';
import 'package:segue/providers/audio_handler_provider.dart';

final playerViewModelProvider = NotifierProvider<PlayerViewModel, PlayerState>(
  () {
    return PlayerViewModel();
  },
);

class PlayerViewModel extends Notifier<PlayerState> {
  @override
  PlayerState build() {
    final handler = ref.watch(audioHandlerProvider);
    handler.mediaItem.listen((item) {
      state = state.copyWith(playingMediaItem: item);
    });

    return PlayerState(playingMediaItem: null);
  }
}

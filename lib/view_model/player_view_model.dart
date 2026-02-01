import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/player_state.dart';
import '../providers/audio_handler_provider.dart';

final playerViewModelProvider = NotifierProvider<PlayerViewModel, PlayerState>(
  () {
    return PlayerViewModel();
  },
);

class PlayerViewModel extends Notifier<PlayerState> {
  @override
  PlayerState build() {
    ref.listen(mediaItemProvider, (previous, next) {
      next.whenData((item) {
        state = state.copyWith(playingMediaItem: item);
      });
    });

    final initialMediaItem = ref.read(mediaItemProvider);
    return PlayerState(
      playingMediaItem: initialMediaItem.maybeWhen(
        orElse: () => null,
        data: (item) => item,
      ),
    );
  }
}

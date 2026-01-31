import 'package:audio_metadata_reader/audio_metadata_reader.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/player_state.dart';
import '../providers/audio_player_provider.dart';

final playerViewModelProvider = NotifierProvider<PlayerViewModel, PlayerState>(
  () {
    return PlayerViewModel();
  },
);

class PlayerViewModel extends Notifier<PlayerState> {
  @override
  PlayerState build() {
    final player = ref.read(audioPlayerProvider);
    player.sequenceStateStream.listen((sequenceState) {
      if (sequenceState.currentSource != null) {
        final metadata = sequenceState.currentSource?.tag as AudioMetadata?;
        state = state.copyWith(currentMetadata: metadata);
      }
    });
    return PlayerState();
  }
}

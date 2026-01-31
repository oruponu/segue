import 'package:audio_metadata_reader/audio_metadata_reader.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../model/music_player_state.dart';
import '../providers/audio_player_provider.dart';

final playerViewModelProvider =
    NotifierProvider<PlayerViewModel, MusicPlayerState>(() {
      return PlayerViewModel();
    });

class PlayerViewModel extends Notifier<MusicPlayerState> {
  @override
  MusicPlayerState build() {
    final player = ref.read(audioPlayerProvider);
    player.sequenceStateStream.listen((state) {
      if (state.currentSource != null) {
        final metadata = state.currentSource?.tag as AudioMetadata?;
        this.state = this.state.copyWith(currentMetadata: metadata);
      }
    });
    return MusicPlayerState();
  }
}

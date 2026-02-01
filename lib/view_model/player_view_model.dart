import 'dart:io';
import 'package:audio_metadata_reader/audio_metadata_reader.dart';
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
        state = state.copyWith(
          currentMetadata: item != null
              ? AudioMetadata(
                  file: File(item.id),
                  title: item.title,
                  album: item.album,
                  artist: item.artist,
                  duration: item.duration,
                )
              : null,
        );
      });
    });

    final initialMediaItem = ref.read(mediaItemProvider);
    return PlayerState(
      currentMetadata: initialMediaItem.maybeWhen(
        orElse: () => null,
        data: (item) => item != null
            ? AudioMetadata(
                file: File(item.id),
                title: item.title,
                album: item.album,
                artist: item.artist,
                duration: item.duration,
              )
            : null,
      ),
    );
  }
}

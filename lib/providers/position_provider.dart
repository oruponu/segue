import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:rxdart/rxdart.dart';
import '../model/position_data.dart';
import 'audio_player_provider.dart';

final positionProvider = StreamProvider<PositionData>((ref) {
  final player = ref.watch(audioPlayerProvider);
  return Rx.combineLatest3<Duration, Duration, Duration?, PositionData>(
    player.positionStream,
    player.bufferedPositionStream,
    player.durationStream,
    (position, bufferedPosition, duration) =>
        PositionData(position, bufferedPosition, duration ?? Duration.zero),
  );
});

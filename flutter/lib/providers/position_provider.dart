import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:rxdart/rxdart.dart';
import 'package:segue/model/position_data.dart';
import 'package:segue/providers/audio_handler_provider.dart';

final positionProvider = StreamProvider<PositionData>((ref) {
  final handler = ref.watch(audioHandlerProvider);
  return Rx.combineLatest3<Duration, Duration, Duration?, PositionData>(
    handler.positionStream,
    handler.bufferedPositionStream,
    handler.durationStream,
    (position, bufferedPosition, duration) =>
        PositionData(position, bufferedPosition, duration ?? Duration.zero),
  );
});

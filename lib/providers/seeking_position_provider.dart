import 'package:flutter_riverpod/flutter_riverpod.dart';

final seekingPositionProvider =
    NotifierProvider<SeekingPositionNotifier, double?>(() {
      return SeekingPositionNotifier();
    });

class SeekingPositionNotifier extends Notifier<double?> {
  @override
  double? build() => null;

  void update(double? value) => state = value;
}

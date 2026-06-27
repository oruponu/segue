import 'package:flutter_test/flutter_test.dart';
import 'package:segue/view/formatters/duration_formatter.dart';

void main() {
  group('formatDuration', () {
    test('returns placeholder for null', () {
      expect(formatDuration(null), '--:--');
    });

    test('formats sub-minute durations', () {
      expect(formatDuration(const Duration(seconds: 5)), '0:05');
    });

    test('formats minutes and seconds without hours', () {
      expect(formatDuration(const Duration(minutes: 5, seconds: 30)), '5:30');
    });

    test('includes hours for durations of an hour or more', () {
      expect(
        formatDuration(const Duration(hours: 1, minutes: 5, seconds: 30)),
        '1:05:30',
      );
    });

    test('zero-pads minutes when hours are shown', () {
      expect(
        formatDuration(const Duration(hours: 2, minutes: 3, seconds: 9)),
        '2:03:09',
      );
    });
  });
}

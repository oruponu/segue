import 'package:flutter/material.dart';
import 'package:just_waveform/just_waveform.dart';

class WaveformPainter extends CustomPainter {
  final Waveform waveform;
  final double displayPercent;

  WaveformPainter({required this.waveform, required this.displayPercent});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..style = PaintingStyle.fill;

    const double barWidth = 2.0;
    const double spacing = 1.0;
    final double totalBarWidth = barWidth + spacing;
    final double maxAmplitude = waveform.flags == 0 ? 32768 : 128;
    final int barCount = (size.width / totalBarWidth).floor();

    for (int i = 0; i < barCount; i++) {
      final index = (i / barCount * waveform.length).floor();
      final min = waveform.getPixelMin(index);
      final max = waveform.getPixelMax(index);
      final amplitude = (max - min).abs() / (maxAmplitude * 2);

      paint.color = (i / barCount < displayPercent)
          ? Colors.white
          : Colors.white.withValues(alpha: 0.24);

      final double barHeight = (size.height * amplitude).clamp(2, size.height);
      final double x = i * totalBarWidth;

      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromCenter(
            center: Offset(x + barWidth / 2, size.height / 2),
            width: barWidth,
            height: barHeight,
          ),
          const Radius.circular(2),
        ),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant WaveformPainter oldDelegate) {
    return oldDelegate.displayPercent != displayPercent ||
        oldDelegate.waveform != waveform;
  }
}

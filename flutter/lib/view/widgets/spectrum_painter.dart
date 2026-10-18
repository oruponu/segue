import 'dart:math' as math;
import 'dart:typed_data';
import 'package:flutter/material.dart';

class SpectrumPainter extends CustomPainter {
  final Float32List? frame;

  SpectrumPainter({required this.frame});

  static const _minDb = -50.0;
  static const _maxDb = 0.0;
  static const _dbRange = _maxDb - _minDb;

  static const _barGap = 3.0;
  static const _barRadius = Radius.circular(2);

  static const _leftPadding = 36.0;
  static const _bottomPadding = 20.0;

  static const _gradient = [
    Color(0xFF1D4ED8), // blue-700
    Color(0xFF3B82F6), // blue-500
    Color(0xFF60A5FA), // blue-400
  ];

  static final double _logMin = math.log(20.0) / math.ln10;
  static final double _logMax = math.log(20000.0) / math.ln10;
  static final double _logRange = _logMax - _logMin;

  static const _freqLabels = [
    (freq: 20.0, label: '20'),
    (freq: 100.0, label: '100'),
    (freq: 1000.0, label: '1k'),
    (freq: 5000.0, label: '5k'),
    (freq: 20000.0, label: '20k'),
  ];

  static const _levelLabels = [
    (value: 0.0, label: '0'),
    (value: -10.0, label: '-10'),
    (value: -20.0, label: '-20'),
    (value: -30.0, label: '-30'),
    (value: -40.0, label: '-40'),
    (value: -50.0, label: '-50'),
  ];

  static const _labelColor = Color(0x88FFFFFF);
  static const _labelStyle = TextStyle(color: _labelColor, fontSize: 10);
  static const _unitStyle = TextStyle(color: _labelColor, fontSize: 9);

  double _freqToX(double freq, Rect barArea) {
    final logF = math.log(freq.clamp(20.0, 20000.0)) / math.ln10;
    return barArea.left + (logF - _logMin) / _logRange * barArea.width;
  }

  @override
  void paint(Canvas canvas, Size size) {
    final barArea = Rect.fromLTRB(
      _leftPadding,
      0,
      size.width,
      size.height - _bottomPadding,
    );

    _drawLabels(canvas, barArea);

    final bands = frame;
    if (bands != null && bands.isNotEmpty) {
      _drawBars(canvas, barArea, bands);
    }
  }

  void _drawLabels(Canvas canvas, Rect barArea) {
    final gridPaint = Paint()
      ..color = const Color(0x22FFFFFF)
      ..strokeWidth = 0.5;

    for (final level in _levelLabels) {
      final normalized = (level.value - _minDb) / _dbRange;
      final y = barArea.bottom - normalized * barArea.height;

      canvas.drawLine(
        Offset(barArea.left, y),
        Offset(barArea.right, y),
        gridPaint,
      );

      final tp = TextPainter(
        text: TextSpan(text: level.label, style: _labelStyle),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, Offset(_leftPadding - tp.width - 4, y - tp.height / 2));
    }

    final dbTp = TextPainter(
      text: const TextSpan(text: 'dB', style: _unitStyle),
      textDirection: TextDirection.ltr,
    )..layout();
    dbTp.paint(canvas, Offset(0, barArea.top - 2));

    final labelY = barArea.bottom + 4;
    for (int i = 0; i < _freqLabels.length; i++) {
      final freq = _freqLabels[i];
      final x = _freqToX(freq.freq, barArea);

      final tp = TextPainter(
        text: TextSpan(text: freq.label, style: _labelStyle),
        textDirection: TextDirection.ltr,
      )..layout();

      final double labelX;
      if (i == 0) {
        labelX = barArea.left;
      } else if (i == _freqLabels.length - 1) {
        labelX = barArea.right - tp.width;
      } else {
        labelX = x - tp.width / 2;
      }
      tp.paint(canvas, Offset(labelX, labelY));
    }

    final hzTp = TextPainter(
      text: const TextSpan(text: 'Hz', style: _unitStyle),
      textDirection: TextDirection.ltr,
    )..layout();
    hzTp.paint(canvas, Offset(0, labelY));
  }

  void _drawBars(Canvas canvas, Rect barArea, Float32List bands) {
    final numBands = bands.length;
    final barWidth = (barArea.width - _barGap * (numBands - 1)) / numBands;
    if (barWidth <= 0) return;

    final paint = Paint()..style = PaintingStyle.fill;

    for (int i = 0; i < numBands; i++) {
      final normalized = ((bands[i] - _minDb) / _dbRange).clamp(0.0, 1.0);
      final barHeight = normalized * barArea.height;
      if (barHeight < 1) continue;

      final x = barArea.left + i * (barWidth + _barGap);
      final rect = RRect.fromRectAndRadius(
        Rect.fromLTWH(x, barArea.bottom - barHeight, barWidth, barHeight),
        _barRadius,
      );

      final Color color;
      if (normalized < 0.5) {
        color = Color.lerp(_gradient[0], _gradient[1], normalized * 2)!;
      } else {
        color = Color.lerp(_gradient[1], _gradient[2], (normalized - 0.5) * 2)!;
      }
      paint.color = color.withAlpha(192);

      canvas.drawRRect(rect, paint);
    }
  }

  @override
  bool shouldRepaint(SpectrumPainter oldDelegate) {
    return !identical(frame, oldDelegate.frame);
  }
}

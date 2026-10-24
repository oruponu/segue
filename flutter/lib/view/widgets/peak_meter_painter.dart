import 'package:flutter/material.dart';

class PeakMeterPainter extends CustomPainter {
  final double peakLevelL;
  final double peakLevelR;
  final double peakHoldL;
  final double peakHoldR;
  final bool clippingL;
  final bool clippingR;

  PeakMeterPainter({
    required this.peakLevelL,
    required this.peakLevelR,
    required this.peakHoldL,
    required this.peakHoldR,
    this.clippingL = false,
    this.clippingR = false,
  });

  static const _leftPadding = 36.0;
  static const _rightPadding = 4.0;
  static const _barHeight = 10.0;
  static const _barGap = 8.0;
  static const _barRadius = Radius.circular(2);
  static const _clipWidth = 4.0;
  static const _clipGap = 4.0;
  static const _dbTextWidth = 16.0;
  static const _dbTextGap = 4.0;

  static const _labelColor = Color(0x88FFFFFF);
  static const _labelStyle = TextStyle(color: _labelColor, fontSize: 10);
  static const _channelLabelStyle = TextStyle(color: _labelColor, fontSize: 9);

  static const _scaleLabels = [
    (value: -50.0, label: '-50'),
    (value: -30.0, label: '-30'),
    (value: -20.0, label: '-20'),
    (value: -10.0, label: '-10'),
    (value: -5.0, label: '-5'),
    (value: 0.0, label: '0'),
  ];

  static const _gradient = LinearGradient(
    colors: [
      Color(0xFF22C55E), // green
      Color(0xFFEAB308), // yellow
      Color(0xFFEF4444), // red
    ],
    stops: [0.0, 0.6, 1.0],
  );

  /// 区分線形スケール：-10 dB を中央（0.5）に配置
  ///   -50..-30 → 0.00..0.15
  ///   -30..-20 → 0.15..0.30
  ///   -20..-10 → 0.30..0.50
  ///   -10..  0 → 0.50..1.00
  static double _dbToPosition(double db) {
    if (db <= -50) return 0.0;
    if (db >= 0) return 1.0;
    if (db < -30) return (db + 50) / 20 * 0.15;
    if (db < -20) return 0.15 + (db + 30) / 10 * 0.15;
    if (db < -10) return 0.30 + (db + 20) / 10 * 0.20;
    return 0.50 + (db + 10) / 10 * 0.50;
  }

  @override
  void paint(Canvas canvas, Size size) {
    final barRight =
        size.width -
        _rightPadding -
        _dbTextWidth -
        _dbTextGap -
        _clipWidth -
        _clipGap;

    final barAreaL = Rect.fromLTRB(_leftPadding, 0, barRight, _barHeight);
    final barAreaR = Rect.fromLTRB(
      _leftPadding,
      _barHeight + _barGap,
      barRight,
      _barHeight + _barGap + _barHeight,
    );

    _drawBar(canvas, barAreaL, peakLevelL, peakHoldL);
    _drawBar(canvas, barAreaR, peakLevelR, peakHoldR);
    _drawClipIndicator(canvas, barAreaL, clippingL);
    _drawClipIndicator(canvas, barAreaR, clippingR);

    final textLeft = barAreaL.right + _clipGap + _clipWidth + _dbTextGap;
    _drawDbText(canvas, textLeft, barAreaL, peakHoldL, clippingL);
    _drawDbText(canvas, textLeft, barAreaR, peakHoldR, clippingR);

    _drawChannelLabel(canvas, 'L', barAreaL);
    _drawChannelLabel(canvas, 'R', barAreaR);
    _drawScaleLabels(canvas, barAreaR);
  }

  void _drawBar(Canvas canvas, Rect barArea, double level, double hold) {
    final bgPaint = Paint()
      ..color = const Color(0x22FFFFFF)
      ..style = PaintingStyle.fill;
    canvas.drawRRect(RRect.fromRectAndRadius(barArea, _barRadius), bgPaint);

    final normalized = _dbToPosition(level);
    if (normalized > 0) {
      final levelRect = Rect.fromLTWH(
        barArea.left,
        barArea.top,
        normalized * barArea.width,
        barArea.height,
      );

      final paint = Paint()
        ..shader = _gradient.createShader(barArea)
        ..style = PaintingStyle.fill;

      canvas.save();
      canvas.clipRRect(RRect.fromRectAndRadius(levelRect, _barRadius));
      canvas.drawRect(barArea, paint);
      canvas.restore();
    }

    final holdNorm = _dbToPosition(hold);
    if (holdNorm > 0) {
      final x = barArea.left + holdNorm * barArea.width;
      final holdPaint = Paint()
        ..color = const Color(0xDDFFFFFF)
        ..strokeWidth = 2.0;
      canvas.drawLine(
        Offset(x, barArea.top),
        Offset(x, barArea.bottom),
        holdPaint,
      );
    }
  }

  void _drawDbText(
    Canvas canvas,
    double left,
    Rect barArea,
    double peakHold,
    bool clipping,
  ) {
    final dbStr = peakHold <= -50.0 ? '-inf' : peakHold.toStringAsFixed(1);
    final style = TextStyle(
      color: clipping ? const Color(0xFFEF4444) : const Color(0xAAFFFFFF),
      fontSize: 9,
      fontFeatures: const [FontFeature.tabularFigures()],
    );
    final tp = TextPainter(
      text: TextSpan(text: dbStr, style: style),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(
      canvas,
      Offset(
        left + _dbTextWidth - tp.width,
        barArea.top + (barArea.height - tp.height) / 2,
      ),
    );
  }

  void _drawClipIndicator(Canvas canvas, Rect barArea, bool clipping) {
    final clipRect = RRect.fromRectAndRadius(
      Rect.fromLTWH(
        barArea.right + _clipGap,
        barArea.top,
        _clipWidth,
        barArea.height,
      ),
      _barRadius,
    );
    final paint = Paint()
      ..color = clipping ? const Color(0xFFEF4444) : const Color(0x22FFFFFF)
      ..style = PaintingStyle.fill;
    canvas.drawRRect(clipRect, paint);
  }

  void _drawChannelLabel(Canvas canvas, String label, Rect barArea) {
    final tp = TextPainter(
      text: TextSpan(text: label, style: _channelLabelStyle),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(
      canvas,
      Offset(
        _leftPadding - tp.width - 4,
        barArea.top + (barArea.height - tp.height) / 2,
      ),
    );
  }

  void _drawScaleLabels(Canvas canvas, Rect bottomBarArea) {
    final labelY = bottomBarArea.bottom + 2;
    for (int i = 0; i < _scaleLabels.length; i++) {
      final label = _scaleLabels[i];
      final pos = _dbToPosition(label.value);
      final x = bottomBarArea.left + pos * bottomBarArea.width;

      final tp = TextPainter(
        text: TextSpan(text: label.label, style: _labelStyle),
        textDirection: TextDirection.ltr,
      )..layout();

      final double labelX;
      if (i == 0) {
        labelX = bottomBarArea.left;
      } else if (i == _scaleLabels.length - 1) {
        labelX = x - tp.width;
      } else {
        labelX = x - tp.width / 2;
      }
      tp.paint(canvas, Offset(labelX, labelY));
    }

    final dbTp = TextPainter(
      text: const TextSpan(text: 'dB', style: _labelStyle),
      textDirection: TextDirection.ltr,
    )..layout();
    dbTp.paint(canvas, Offset(0, labelY));
  }

  @override
  bool shouldRepaint(PeakMeterPainter oldDelegate) {
    return peakLevelL != oldDelegate.peakLevelL ||
        peakLevelR != oldDelegate.peakLevelR ||
        peakHoldL != oldDelegate.peakHoldL ||
        peakHoldR != oldDelegate.peakHoldR ||
        clippingL != oldDelegate.clippingL ||
        clippingR != oldDelegate.clippingR;
  }
}

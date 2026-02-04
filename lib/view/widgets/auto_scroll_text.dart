import 'package:flutter/material.dart';
import 'package:text_scroll/text_scroll.dart';

class AutoScrollText extends StatelessWidget {
  final String text;
  final TextStyle style;
  final double velocity;

  const AutoScrollText({
    super.key,
    required this.text,
    required this.style,
    this.velocity = 50.0,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final textPainter = TextPainter(
          text: TextSpan(text: text, style: style),
          textDirection: TextDirection.ltr,
          maxLines: 1,
        )..layout();

        final isOverflowing = textPainter.width > constraints.maxWidth;
        if (isOverflowing) {
          return SizedBox(
            height: textPainter.height,
            child: TextScroll(
              text,
              style: style,
              delayBefore: const Duration(seconds: 2),
              pauseBetween: const Duration(seconds: 1),
              velocity: Velocity(pixelsPerSecond: Offset(velocity, 0)),
              intervalSpaces: 16,
            ),
          );
        } else {
          return Text(
            text,
            style: style,
            overflow: TextOverflow.ellipsis,
            maxLines: 1,
          );
        }
      },
    );
  }
}

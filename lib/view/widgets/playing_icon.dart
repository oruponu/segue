import 'dart:math';
import 'package:flutter/material.dart';

class PlayingIcon extends StatefulWidget {
  const PlayingIcon({super.key});

  @override
  State<PlayingIcon> createState() => _PlayingIconState();
}

class _PlayingIconState extends State<PlayingIcon>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    )..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const double maxHeight = 12.0;
    return SizedBox(
      height: maxHeight,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: List.generate(3, (index) {
          return AnimatedBuilder(
            animation: _controller,
            builder: (context, child) {
              final double value =
                  0.5 + 0.5 * (sin(_controller.value * 2 * pi + index).abs());
              return Container(
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primary,
                  borderRadius: BorderRadius.circular(1),
                ),
                width: 3,
                height: maxHeight * value,
                margin: const EdgeInsets.symmetric(horizontal: 1),
              );
            },
          );
        }),
      ),
    );
  }
}

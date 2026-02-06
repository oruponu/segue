import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/providers/position_provider.dart';
import 'package:segue/providers/seeking_position_provider.dart';
import 'package:segue/providers/waveform_provider.dart';
import 'package:segue/view/widgets/waveform_painter.dart';

class ProgressBarWidget extends ConsumerWidget {
  const ProgressBarWidget({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final positionDataAsync = ref.watch(positionProvider);
    final seekingPosition = ref.watch(seekingPositionProvider);
    final handler = ref.watch(audioHandlerProvider);
    final waveformAsync = ref.watch(waveformProvider);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: positionDataAsync.when(
        data: (data) => LayoutBuilder(
          builder: (context, constraints) {
            final double displayPercent =
                seekingPosition ??
                (data.duration.inMilliseconds > 0
                    ? data.position.inMilliseconds /
                          data.duration.inMilliseconds
                    : 0);
            final displayPosition = seekingPosition != null
                ? data.duration * seekingPosition
                : data.position;
            return Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                GestureDetector(
                  onTapDown: (details) {
                    final double percent =
                        (details.localPosition.dx / constraints.maxWidth).clamp(
                          0,
                          1,
                        );
                    ref.read(seekingPositionProvider.notifier).update(percent);
                    _handleSeek(percent, data.duration, handler);
                  },
                  onTapUp: (_) =>
                      ref.read(seekingPositionProvider.notifier).update(null),
                  onTapCancel: () =>
                      ref.read(seekingPositionProvider.notifier).update(null),
                  onHorizontalDragUpdate: (details) {
                    final double percent =
                        (details.localPosition.dx / constraints.maxWidth).clamp(
                          0,
                          1,
                        );
                    ref.read(seekingPositionProvider.notifier).update(percent);
                  },
                  onHorizontalDragEnd: (_) {
                    final percent = ref.read(seekingPositionProvider);
                    if (percent != null) {
                      _handleSeek(percent, data.duration, handler);
                    }
                    ref.read(seekingPositionProvider.notifier).update(null);
                  },
                  behavior: HitTestBehavior.opaque,
                  child: SizedBox(
                    height: 60,
                    width: double.infinity,
                    child: waveformAsync.when(
                      data: (waveform) {
                        if (waveform == null) return _buildFallbackLine();
                        return CustomPaint(
                          painter: WaveformPainter(
                            waveform: waveform,
                            displayPercent: displayPercent,
                          ),
                        );
                      },
                      error: (_, _) => _buildFallbackLine(),
                      loading: () => _buildFallbackLine(),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      _formatDuration(displayPosition),
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 14,
                      ),
                    ),
                    Text(
                      _formatDuration(data.duration),
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 14,
                      ),
                    ),
                  ],
                ),
              ],
            );
          },
        ),
        error: (_, _) => const SizedBox(height: 60),
        loading: () => const SizedBox(height: 60),
      ),
    );
  }

  void _handleSeek(double percent, Duration duration, AudioHandler handler) {
    final Duration seekTarget = duration * percent;
    handler.seek(seekTarget);
  }

  Widget _buildFallbackLine() {
    return Center(
      child: Container(height: 2, color: Colors.white.withValues(alpha: 0.24)),
    );
  }

  String _formatDuration(Duration duration) {
    final minutes = duration.inMinutes.remainder(60).toString();
    final seconds = duration.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }
}

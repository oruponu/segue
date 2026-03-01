import 'dart:math' as math;
import 'dart:typed_data';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:segue/model/analysis_tab.dart';
import 'package:segue/providers/analysis_sheet_controller_provider.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/providers/spectrum_provider.dart';
import 'package:segue/view_model/analysis_view_model.dart';
import 'package:segue/view/widgets/mini_player.dart';
import 'package:segue/view/widgets/spectrum_painter.dart';

class AnalysisScreen extends ConsumerStatefulWidget {
  const AnalysisScreen({super.key});

  @override
  ConsumerState<AnalysisScreen> createState() => _AnalysisScreenState();
}

class _AnalysisScreenState extends ConsumerState<AnalysisScreen>
    with SingleTickerProviderStateMixin {
  static const _minDb = -50.0;
  static const _decayRate = 25.0; // dB per second (full range in 2 seconds)

  AnalysisTab _selectedTab = AnalysisTab.realtime;
  late final Ticker _ticker;
  Float32List? _currentFrame;
  Float32List? _displayFrame;
  Duration? _lastTickTime;

  @override
  void initState() {
    super.initState();
    _ticker = createTicker(_onTick);
    _ticker.start();
  }

  @override
  void dispose() {
    _ticker.dispose();
    super.dispose();
  }

  void _onTick(Duration elapsed) {
    final dt = _lastTickTime != null
        ? (elapsed - _lastTickTime!).inMicroseconds / 1000000.0
        : 1.0 / 60.0;
    _lastTickTime = elapsed;
    final clampedDt = dt.clamp(0.001, 0.1);

    Float32List? targetFrame;
    final spectrumData = ref.read(spectrumProvider).value;
    if (spectrumData != null && spectrumData.numFrames > 0) {
      final handler = ref.read(audioHandlerProvider);
      final playbackState = handler.playbackState.value;

      if (playbackState.playing) {
        Duration currentPosition = playbackState.updatePosition;
        final sinceUpdate = DateTime.now().difference(playbackState.updateTime);
        currentPosition += sinceUpdate * playbackState.speed;
        final seconds = currentPosition.inMicroseconds / 1000000.0;

        final exactIndex = seconds / spectrumData.hopDuration;
        final index0 = exactIndex.floor().clamp(0, spectrumData.numFrames - 1);
        final index1 = (index0 + 1).clamp(0, spectrumData.numFrames - 1);
        final t = (exactIndex - index0).clamp(0.0, 1.0);

        final frame0 = spectrumData.getFrame(index0);
        final frame1 = spectrumData.getFrame(index1);
        targetFrame = Float32List(spectrumData.numBands);
        for (int i = 0; i < spectrumData.numBands; i++) {
          targetFrame[i] = frame0[i] + (frame1[i] - frame0[i]) * t;
        }
      }
    }

    final numBands = targetFrame?.length ?? _displayFrame?.length ?? 0;
    if (numBands == 0) {
      if (_currentFrame != null) {
        setState(() => _currentFrame = null);
      }
      return;
    }

    final prev = _displayFrame;
    final result = Float32List(numBands);
    bool anyActive = false;

    for (int i = 0; i < numBands; i++) {
      final target = (targetFrame != null && i < targetFrame.length)
          ? targetFrame[i]
          : _minDb;
      final current = (prev != null && i < prev.length) ? prev[i] : _minDb;

      if (target >= current) {
        result[i] = target;
      } else {
        result[i] = math.max(target, current - _decayRate * clampedDt);
      }
      if (result[i] > _minDb) anyActive = true;
    }

    _displayFrame = result;
    if (anyActive) {
      setState(() => _currentFrame = result);
    } else if (_currentFrame != null) {
      _displayFrame = null;
      setState(() => _currentFrame = null);
    }
  }

  void _onTabChanged(AnalysisTab tab) {
    setState(() {
      _selectedTab = tab;
    });
    if (tab == AnalysisTab.realtime) {
      _lastTickTime = null;
      if (!_ticker.isActive) _ticker.start();
    } else {
      _ticker.stop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(analysisViewModelProvider);

    return Column(
      children: [
        MiniPlayer(
          topPosition: true,
          onTap: () {
            ref.read(analysisSheetControllerProvider.notifier).collapse();
          },
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: SizedBox(
            width: double.infinity,
            child: SegmentedButton<AnalysisTab>(
              segments: const [
                ButtonSegment(
                  value: AnalysisTab.realtime,
                  label: Text('リアルタイム'),
                ),
                ButtonSegment(value: AnalysisTab.results, label: Text('解析結果')),
              ],
              selected: {_selectedTab},
              onSelectionChanged: (selected) {
                _onTabChanged(selected.first);
              },
            ),
          ),
        ),
        Expanded(
          child: _selectedTab == AnalysisTab.results
              ? (state.isAnalyzing
                    ? const Center(child: CircularProgressIndicator())
                    : _buildContent(context, state))
              : _buildRealtimeTab(),
        ),
      ],
    );
  }

  Widget _buildRealtimeTab() {
    ref.watch(spectrumProvider);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Center(
        child: SizedBox(
          width: double.infinity,
          height: 220,
          child: CustomPaint(painter: SpectrumPainter(frame: _currentFrame)),
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context, playerState) {
    final theme = Theme.of(context);

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      children: [
        if (playerState.bpm != null) ...[
          Text(
            'BPM',
            style: theme.textTheme.labelLarge?.copyWith(color: Colors.white54),
          ),
          const SizedBox(height: 4),
          Text(
            '${playerState.bpm!.round()}',
            style: theme.textTheme.displayMedium?.copyWith(
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 24),
        ],
        if (playerState.key != null) ...[
          Text(
            'Key',
            style: theme.textTheme.labelLarge?.copyWith(color: Colors.white54),
          ),
          const SizedBox(height: 4),
          Text(
            playerState.key!,
            style: theme.textTheme.headlineMedium?.copyWith(
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 24),
        ],
        if (playerState.styles != null && playerState.styles!.isNotEmpty) ...[
          Text(
            'Styles',
            style: theme.textTheme.labelLarge?.copyWith(color: Colors.white54),
          ),
          const SizedBox(height: 8),
          ...playerState.styles!.map(
            (style) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _buildStyleRow(context, style),
            ),
          ),
        ],
        if (playerState.bpm == null &&
            playerState.key == null &&
            (playerState.styles == null || playerState.styles!.isEmpty))
          Center(
            child: Text(
              '解析データなし',
              style: theme.textTheme.bodyLarge?.copyWith(color: Colors.white38),
            ),
          ),
      ],
    );
  }

  Widget _buildStyleRow(BuildContext context, style) {
    final theme = Theme.of(context);
    final percentage = (style.confidence * 100).toStringAsFixed(1);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(style.displayName, style: theme.textTheme.bodyLarge),
            ),
            Text(
              '$percentage%',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: Colors.white54,
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        ClipRRect(
          borderRadius: BorderRadius.circular(2),
          child: LinearProgressIndicator(
            value: style.confidence,
            backgroundColor: Colors.white.withValues(alpha: 0.1),
            valueColor: AlwaysStoppedAnimation<Color>(
              Colors.purple.withValues(alpha: 0.7),
            ),
            minHeight: 4,
          ),
        ),
      ],
    );
  }
}

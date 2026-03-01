import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/model/analysis_tab.dart';
import 'package:segue/providers/analysis_sheet_controller_provider.dart';
import 'package:segue/view_model/analysis_view_model.dart';
import 'package:segue/view/widgets/mini_player.dart';

class AnalysisScreen extends ConsumerStatefulWidget {
  const AnalysisScreen({super.key});

  @override
  ConsumerState<AnalysisScreen> createState() => _AnalysisScreenState();
}

class _AnalysisScreenState extends ConsumerState<AnalysisScreen> {
  AnalysisTab _selectedTab = AnalysisTab.realtime;

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
                setState(() {
                  _selectedTab = selected.first;
                });
              },
            ),
          ),
        ),
        Expanded(
          child: _selectedTab == AnalysisTab.results
              ? (state.isAnalyzing
                    ? const Center(child: CircularProgressIndicator())
                    : _buildContent(context, state))
              : const Center(
                  child: Text(
                    'リアルタイム表示',
                    style: TextStyle(color: Colors.white54),
                  ),
                ),
        ),
      ],
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

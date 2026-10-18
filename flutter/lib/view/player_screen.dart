import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/model/analysis_sheet_state.dart';
import 'package:segue/model/player_sheet_state.dart';
import 'package:segue/providers/analysis_sheet_controller_provider.dart';
import 'package:segue/providers/player_sheet_controller_provider.dart';
import 'package:segue/view/analysis_screen.dart';
import 'package:segue/view/widgets/playback_controls.dart';
import 'package:segue/view/widgets/progress_bar_widget.dart';
import 'package:segue/view/widgets/track_metadata_widget.dart';

class PlayerScreen extends ConsumerStatefulWidget {
  const PlayerScreen({super.key});

  @override
  ConsumerState<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends ConsumerState<PlayerScreen> {
  final DraggableScrollableController _analysisController =
      DraggableScrollableController();

  @override
  void initState() {
    super.initState();
    _analysisController.addListener(_onAnalysisSheetChanged);
  }

  @override
  void dispose() {
    _analysisController.removeListener(_onAnalysisSheetChanged);
    _analysisController.dispose();
    super.dispose();
  }

  void _onAnalysisSheetChanged() {
    if (!_analysisController.isAttached) return;
    ref
        .read(analysisSheetControllerProvider.notifier)
        .setExpanded(_analysisController.size > 0.5);
  }

  @override
  Widget build(BuildContext context) {
    const double buttonHeight = 56.0;
    final double screenHeight = MediaQuery.of(context).size.height;
    final double minSize =
        (buttonHeight + MediaQuery.of(context).padding.bottom) / screenHeight;

    ref.listen<AnalysisSheetState>(analysisSheetControllerProvider, (
      previous,
      next,
    ) {
      if (!_analysisController.isAttached) return;

      switch (next.action) {
        case AnalysisSheetAction.expand:
          _analysisController.animateTo(
            1.0,
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOutCubic,
          );
        case AnalysisSheetAction.collapse:
          _analysisController.animateTo(
            minSize,
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOutCubic,
          );
        case AnalysisSheetAction.none:
          break;
      }
    });

    // Auto-collapse analysis when player is collapsed
    ref.listen<PlayerSheetState>(playerSheetControllerProvider, (
      previous,
      next,
    ) {
      if (next.action == PlayerSheetAction.collapse) {
        ref.read(analysisSheetControllerProvider.notifier).collapse();
      }
    });

    return Stack(
      children: [
        Scaffold(
          appBar: AppBar(
            leading: IconButton(
              icon: const Icon(Icons.keyboard_arrow_down, size: 32),
              onPressed: () {
                ref.read(playerSheetControllerProvider.notifier).collapse();
              },
            ),
          ),
          body: SafeArea(
            child: SizedBox(
              width: double.infinity,
              child: Column(
                children: [
                  const Padding(padding: EdgeInsets.all(8)),
                  const Expanded(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        TrackMetadataWidget(),
                        SizedBox(height: 20),
                        ProgressBarWidget(),
                        PlaybackControls(),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                ],
              ),
            ),
          ),
        ),
        DraggableScrollableSheet(
          initialChildSize: minSize,
          minChildSize: minSize,
          maxChildSize: 1.0,
          snap: true,
          snapAnimationDuration: const Duration(milliseconds: 150),
          controller: _analysisController,
          builder: (context, scrollController) {
            return AnimatedBuilder(
              animation: _analysisController,
              builder: (context, child) {
                double selection = 0.0;
                if (_analysisController.isAttached) {
                  selection =
                      ((_analysisController.size - minSize) / (1.0 - minSize))
                          .clamp(0.0, 1.0);
                }

                return Container(
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.surface,
                  ),
                  child: SingleChildScrollView(
                    controller: scrollController,
                    child: SizedBox(
                      height: screenHeight,
                      child: Stack(
                        children: [
                          IgnorePointer(
                            ignoring: selection < 0.5,
                            child: Opacity(
                              opacity: selection,
                              child: const AnalysisScreen(),
                            ),
                          ),
                          IgnorePointer(
                            ignoring: selection > 0.5,
                            child: Opacity(
                              opacity: (1.0 - selection * 2).clamp(0.0, 1.0),
                              child: SizedBox(
                                width: double.infinity,
                                height: buttonHeight,
                                child: TextButton.icon(
                                  onPressed: () {
                                    ref
                                        .read(
                                          analysisSheetControllerProvider
                                              .notifier,
                                        )
                                        .expand();
                                  },
                                  icon: const Icon(Icons.analytics_outlined),
                                  label: const Text('解析'),
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              },
            );
          },
        ),
      ],
    );
  }
}

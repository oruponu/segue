import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/model/player_sheet_state.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/providers/player_sheet_controller_provider.dart';
import 'package:segue/view/library_screen.dart';
import 'package:segue/view/player_screen.dart';
import 'package:segue/view/widgets/mini_player.dart';

class MainScreen extends ConsumerStatefulWidget {
  const MainScreen({super.key});

  @override
  ConsumerState<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends ConsumerState<MainScreen> {
  final DraggableScrollableController _controller =
      DraggableScrollableController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const double miniPlayerHeight = 72.0;
    final double screenHeight = MediaQuery.of(context).size.height;
    final double minSize =
        (miniPlayerHeight + MediaQuery.of(context).padding.bottom) /
        screenHeight;
    final handler = ref.watch(audioHandlerProvider);

    ref.listen<PlayerSheetState>(playerSheetControllerProvider, (
      previous,
      next,
    ) {
      if (!_controller.isAttached) return;

      switch (next.action) {
        case PlayerSheetAction.expand:
          _controller.animateTo(
            1.0,
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOutCubic,
          );
        case PlayerSheetAction.collapse:
          _controller.animateTo(
            minSize,
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOutCubic,
          );
        case PlayerSheetAction.none:
          break;
      }
    });

    return Scaffold(
      body: StreamBuilder<MediaItem?>(
        stream: handler.mediaItem,
        builder: (context, snapshot) {
          final showMiniPlayer = snapshot.data != null;
          final double bottomPadding = showMiniPlayer
              ? miniPlayerHeight + MediaQuery.of(context).padding.bottom
              : 0.0;

          return Stack(
            children: [
              Positioned.fill(
                bottom: bottomPadding,
                child: const LibraryScreen(),
              ),
              if (showMiniPlayer)
                DraggableScrollableSheet(
                  initialChildSize: minSize,
                  minChildSize: minSize,
                  maxChildSize: 1.0,
                  snap: true,
                  snapAnimationDuration: const Duration(milliseconds: 150),
                  controller: _controller,
                  builder: (context, scrollController) {
                    return AnimatedBuilder(
                      animation: _controller,
                      builder: (context, child) {
                        double selection = 0.0;
                        if (_controller.isAttached) {
                          selection =
                              ((_controller.size - minSize) / (1.0 - minSize))
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
                                      child: const PlayerScreen(),
                                    ),
                                  ),
                                  IgnorePointer(
                                    ignoring: selection > 0.5,
                                    child: Opacity(
                                      opacity: (1.0 - selection * 2).clamp(
                                        0.0,
                                        1.0,
                                      ),
                                      child: const MiniPlayer(),
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
        },
      ),
    );
  }
}

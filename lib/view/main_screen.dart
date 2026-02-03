import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'player_screen.dart';
import 'library_screen.dart';
import 'widgets/mini_player.dart';
import '../model/player_sheet_state.dart';
import '../providers/player_sheet_controller_provider.dart';

class MainScreen extends ConsumerStatefulWidget {
  const MainScreen({super.key});

  @override
  ConsumerState<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends ConsumerState<MainScreen> {
  final DraggableScrollableController _controller =
      DraggableScrollableController();

  @override
  Widget build(BuildContext context) {
    const double miniPlayerHeight = 72.0;
    final double screenHeight = MediaQuery.of(context).size.height;
    final double minSize =
        (miniPlayerHeight + MediaQuery.of(context).padding.bottom) /
        screenHeight;

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
      body: Stack(
        children: [
          const LibraryScreen(),

          DraggableScrollableSheet(
            initialChildSize: minSize,
            minChildSize: minSize,
            maxChildSize: 1.0,
            snap: true,
            controller: _controller,
            builder: (context, scrollController) {
              return AnimatedBuilder(
                animation: _controller,
                builder: (context, child) {
                  double selection = 0.0;
                  if (_controller.isAttached) {
                    selection = ((_controller.size - minSize) / (1.0 - minSize))
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
                                child: const PlayerScreen(title: "Now Playing"),
                              ),
                            ),
                            IgnorePointer(
                              ignoring: selection > 0.5,
                              child: Opacity(
                                opacity: (1.0 - selection * 2).clamp(0.0, 1.0),
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
      ),
    );
  }
}

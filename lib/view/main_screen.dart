import 'package:flutter/material.dart';
import 'player_screen.dart';
import 'library_screen.dart';
import 'widgets/mini_player.dart';

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  final DraggableScrollableController _controller =
      DraggableScrollableController();

  @override
  Widget build(BuildContext context) {
    const double miniPlayerHeight = 72.0;
    final double screenHeight = MediaQuery.of(context).size.height;
    final double minSize =
        (miniPlayerHeight + MediaQuery.of(context).padding.bottom) /
        screenHeight;

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

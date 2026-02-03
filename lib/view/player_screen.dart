import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/providers/player_sheet_controller_provider.dart';
import 'package:segue/view/widgets/playback_controls.dart';
import 'package:segue/view/widgets/progress_bar_widget.dart';
import 'package:segue/view/widgets/track_metadata_widget.dart';

class PlayerScreen extends ConsumerWidget {
  const PlayerScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
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
              Padding(padding: const EdgeInsets.all(8)),
              const Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    TrackMetadataWidget(),
                    SizedBox(height: 20),
                    ProgressBarWidget(),
                    SizedBox(height: 20),
                    PlaybackControls(),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'widgets/playback_controls.dart';
import 'widgets/progress_bar_widget.dart';
import 'widgets/track_metadata_widget.dart';

class PlayerScreen extends ConsumerWidget {
  const PlayerScreen({super.key, required this.title});

  final String title;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
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

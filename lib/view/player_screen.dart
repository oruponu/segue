import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../view_model/player_view_model.dart';
import 'widgets/playback_controls.dart';
import 'widgets/progress_bar_widget.dart';
import 'widgets/track_metadata_widget.dart';

class PlayerScreen extends ConsumerWidget {
  const PlayerScreen({super.key, required this.title});

  final String title;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(playerViewModelProvider);
    final viewModel = ref.read(playerViewModelProvider.notifier);

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      floatingActionButton: FloatingActionButton(
        onPressed: state.isLoading ? null : viewModel.selectDirectory,
        child: state.isLoading
            ? const CircularProgressIndicator(color: Colors.white)
            : const Icon(Icons.folder_open),
      ),
      body: SafeArea(
        child: SizedBox(
          width: double.infinity,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(8),
                child: Text(
                  state.selectedDirectory ?? "フォルダを選択してください",
                  style: const TextStyle(fontSize: 12, color: Colors.grey),
                ),
              ),
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

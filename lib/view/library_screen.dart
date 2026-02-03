import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/providers/player_sheet_controller_provider.dart';
import 'package:segue/view/widgets/playing_icon.dart';
import 'package:segue/view_model/library_view_model.dart';

class LibraryScreen extends ConsumerWidget {
  const LibraryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(libraryViewModelProvider);
    final viewModel = ref.read(libraryViewModelProvider.notifier);
    final playingMediaItem = state.playingMediaItem;

    return Scaffold(
      appBar: AppBar(
        title: const Text("ライブラリ"),
        actions: [
          IconButton(
            icon: const Icon(Icons.folder_open),
            onPressed: viewModel.selectDirectory,
          ),
        ],
      ),
      body: state.isLoading
          ? const Center(child: CircularProgressIndicator())
          : state.playlist.isEmpty
          ? const Center(child: Text("フォルダを選択してください"))
          : ListView.builder(
              itemBuilder: (context, index) {
                final metadata = state.playlist[index];
                final isPlaying =
                    playingMediaItem != null &&
                    playingMediaItem.title == metadata.title &&
                    playingMediaItem.artist == metadata.artist;
                return ListTile(
                  leading: _buildThumbnail(metadata.artUri),
                  title: Row(
                    children: [
                      Expanded(
                        child: Text(
                          metadata.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (isPlaying && state.isPlaying) ...[
                        const SizedBox(width: 8),
                        const PlayingIcon(),
                      ],
                    ],
                  ),
                  subtitle: Row(
                    children: [
                      Expanded(
                        child: Text(
                          metadata.artist ?? "Unknown Artist",
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Text(
                        _formatDuration(metadata.duration),
                        style: const TextStyle(
                          fontSize: 12,
                          color: Colors.grey,
                        ),
                      ),
                    ],
                  ),
                  onTap: () async {
                    await viewModel.playItem(index);
                    ref.read(playerSheetControllerProvider.notifier).expand();
                  },
                  tileColor: isPlaying
                      ? Colors.blue.withValues(alpha: 0.1)
                      : null,
                );
              },
              itemCount: state.playlist.length,
            ),
    );
  }

  Widget _buildThumbnail(Uri? artUri) {
    if (artUri != null) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: Image.file(
          File.fromUri(artUri),
          width: 48,
          height: 48,
          fit: BoxFit.cover,
        ),
      );
    }
    return Container(
      width: 48,
      height: 48,
      color: Colors.grey[800],
      child: const Icon(Icons.music_note),
    );
  }

  String _formatDuration(Duration? duration) {
    if (duration == null) return "--:--";

    String twoDigits(int n) => n.toString().padLeft(2, '0');
    final minutes = twoDigits(duration.inMinutes.remainder(60));
    final seconds = twoDigits(duration.inSeconds.remainder(60));
    return "$minutes:$seconds";
  }
}

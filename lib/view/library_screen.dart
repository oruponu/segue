import 'package:audio_metadata_reader/audio_metadata_reader.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import '../view_model/library_view_model.dart';
import 'player_screen.dart';

class LibraryScreen extends ConsumerWidget {
  const LibraryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(libraryViewModelProvider);
    final viewModel = ref.read(libraryViewModelProvider.notifier);
    final currentMetadata = state.playingMetadata;

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
                    currentMetadata != null &&
                    currentMetadata.title == metadata.title &&
                    currentMetadata.artist == metadata.artist;
                return ListTile(
                  leading: _buildThumbnail(metadata),
                  title: Text(
                    metadata.title ?? "Unknown Title",
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
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
                    showModalBottomSheet(
                      context: context,
                      builder: (context) =>
                          const PlayerScreen(title: "Now Playing"),
                      backgroundColor: Colors.transparent,
                      isScrollControlled: true,
                      useSafeArea: true,
                    );
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

  Widget _buildThumbnail(AudioMetadata metadata) {
    if (metadata.pictures.isNotEmpty) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: Image.memory(
          metadata.pictures.first.bytes,
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

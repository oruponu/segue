import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/model/album.dart';
import 'package:segue/providers/player_sheet_controller_provider.dart';
import 'package:segue/view/widgets/playing_icon.dart';
import 'package:segue/view_model/library_view_model.dart';

class LibraryScreen extends ConsumerWidget {
  const LibraryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(libraryViewModelProvider);
    final viewModel = ref.read(libraryViewModelProvider.notifier);
    final selectedAlbum = state.selectedAlbum;

    return Scaffold(
      appBar: AppBar(
        leading: selectedAlbum != null
            ? IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: viewModel.goBackToAlbums,
              )
            : null,
        title: Text(selectedAlbum?.name ?? "ライブラリ"),
        actions: [
          if (selectedAlbum == null)
            IconButton(
              icon: const Icon(Icons.folder_open),
              onPressed: viewModel.selectDirectory,
            ),
        ],
      ),
      body: state.isLoading
          ? _buildScanningView(context, ref)
          : selectedAlbum != null
          ? _buildTrackList(context, ref, selectedAlbum)
          : state.albums.isEmpty
          ? const Center(child: Text("フォルダを選択してください"))
          : _buildAlbumList(context, ref),
    );
  }

  Widget _buildScanningView(BuildContext context, WidgetRef ref) {
    final state = ref.watch(libraryViewModelProvider);
    final total = state.scanTotal;
    final processed = state.scanProcessed;
    final progress = total > 0 ? processed / total : null;

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            children: [
              LinearProgressIndicator(value: progress),
              const SizedBox(height: 8),
              Text(
                total > 0 ? 'スキャン中... $processed/$total' : 'スキャン中...',
                style: const TextStyle(fontSize: 14, color: Colors.grey),
              ),
            ],
          ),
        ),
        Expanded(
          child: state.albums.isEmpty
              ? const SizedBox.shrink()
              : ListView.builder(
                  itemCount: state.albums.length,
                  itemBuilder: (context, index) {
                    final album = state.albums[index];
                    return ListTile(
                      leading: _buildThumbnail(album.artUri),
                      title: Text(
                        album.name,
                        style: const TextStyle(height: 1.2),
                        overflow: TextOverflow.ellipsis,
                        maxLines: 2,
                      ),
                      subtitle: Row(
                        children: [
                          Expanded(
                            child: Text(
                              album.artist ?? "Unknown Artist",
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          Text(
                            '${album.trackCount}曲・${_formatDuration(album.totalDuration)}',
                            style: const TextStyle(
                              fontSize: 12,
                              color: Colors.grey,
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _buildAlbumList(BuildContext context, WidgetRef ref) {
    final state = ref.watch(libraryViewModelProvider);
    final viewModel = ref.read(libraryViewModelProvider.notifier);

    return ListView.builder(
      key: const PageStorageKey('album_list'),
      itemCount: state.albums.length,
      itemBuilder: (context, index) {
        final album = state.albums[index];
        return ListTile(
          leading: _buildThumbnail(album.artUri),
          title: Text(
            album.name,
            style: const TextStyle(height: 1.2),
            overflow: TextOverflow.ellipsis,
            maxLines: 2,
          ),
          subtitle: Row(
            children: [
              Expanded(
                child: Text(
                  album.artist ?? "Unknown Artist",
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              Text(
                '${album.trackCount}曲・${_formatDuration(album.totalDuration)}',
                style: const TextStyle(fontSize: 12, color: Colors.grey),
              ),
            ],
          ),
          onTap: () => viewModel.selectAlbum(album),
        );
      },
    );
  }

  Widget _buildTrackList(BuildContext context, WidgetRef ref, Album album) {
    final state = ref.watch(libraryViewModelProvider);
    final viewModel = ref.read(libraryViewModelProvider.notifier);
    final playingMediaItem = state.playingMediaItem;

    return ListView.builder(
      itemCount: album.tracks.length,
      itemBuilder: (context, index) {
        final track = album.tracks[index];
        final isPlaying =
            playingMediaItem != null && playingMediaItem.id == track.id;
        return ListTile(
          leading: _buildThumbnail(track.artUri),
          title: Row(
            children: [
              Expanded(
                child: Text(
                  track.title,
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
                  track.artist ?? "Unknown Artist",
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              Text(
                _formatDuration(track.duration),
                style: const TextStyle(fontSize: 12, color: Colors.grey),
              ),
            ],
          ),
          onTap: () async {
            await viewModel.playItem(index);
            ref.read(playerSheetControllerProvider.notifier).expand();
          },
          tileColor: isPlaying ? Colors.blue.withValues(alpha: 0.1) : null,
        );
      },
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
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = twoDigits(duration.inSeconds.remainder(60));
    if (hours > 0) {
      return "$hours:${twoDigits(minutes)}:$seconds";
    }
    return "$minutes:$seconds";
  }
}

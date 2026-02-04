import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/view_model/player_view_model.dart';
import 'package:segue/view/widgets/auto_scroll_text.dart';

class TrackMetadataWidget extends ConsumerWidget {
  const TrackMetadataWidget({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(playerViewModelProvider);
    final mediaItem = state.playingMediaItem;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        children: [
          Container(
            width: 250,
            height: 250,
            decoration: BoxDecoration(
              color: Colors.grey[800],
              borderRadius: BorderRadius.circular(12),
              boxShadow: const [
                BoxShadow(
                  color: Colors.black45,
                  offset: Offset(0, 5),
                  blurRadius: 10,
                ),
              ],
            ),
            child: _buildThumbnail(mediaItem?.artUri),
          ),
          const SizedBox(height: 30),
          SizedBox(
            height: 36,
            child: AutoScrollText(
              text: mediaItem?.title ?? "Unknown Title",
              style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
            ),
          ),
          const SizedBox(height: 8),
          SizedBox(
            height: 24,
            child: AutoScrollText(
              text: mediaItem?.artist ?? "Unknown Artist",
              style: const TextStyle(fontSize: 18, color: Colors.white70),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildThumbnail(Uri? artUri) {
    if (artUri != null) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Image.file(File.fromUri(artUri), fit: BoxFit.cover),
      );
    }
    return const Icon(Icons.music_note, size: 100, color: Colors.white24);
  }
}

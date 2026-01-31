import 'dart:typed_data';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import '../../view_model/player_view_model.dart';

class TrackMetadataWidget extends ConsumerWidget {
  const TrackMetadataWidget({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(playerViewModelProvider);
    final metadata = state.currentMetadata;
    Uint8List? albumArt;
    if (metadata != null && metadata.pictures.isNotEmpty) {
      albumArt = metadata.pictures.first.bytes;
    }

    return Column(
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
                blurRadius: 10,
                offset: Offset(0, 5),
              ),
            ],
          ),

          child: albumArt != null
              ? ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Image.memory(albumArt, fit: BoxFit.cover),
                )
              : const Icon(Icons.music_note, size: 100, color: Colors.white24),
        ),
        const SizedBox(height: 30),
        Text(
          metadata?.title ?? "Unknown Title",
          style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          metadata?.artist ?? "Unknown Artist",
          style: const TextStyle(fontSize: 18, color: Colors.white70),
          textAlign: TextAlign.center,
        ),
      ],
    );
  }
}

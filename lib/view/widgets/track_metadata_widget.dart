import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/model/player_state.dart';
import 'package:segue/view_model/player_view_model.dart';
import 'package:segue/view/widgets/auto_scroll_text.dart';

class TrackMetadataWidget extends ConsumerWidget {
  const TrackMetadataWidget({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(playerViewModelProvider);
    final mediaItem = state.playingMediaItem;

    return Column(
      children: [
        Padding(
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
                  style: const TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                  ),
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
        ),
        const SizedBox(height: 8),
        _buildAnalysisChips(state),
      ],
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

  Widget _buildAnalysisChips(PlayerState state) {
    if (state.isAnalyzing) {
      return const SizedBox(
        height: 48,
        child: Center(
          child: SizedBox(
            width: 16,
            height: 16,
            child: CircularProgressIndicator(
              color: Colors.white38,
              strokeWidth: 2,
            ),
          ),
        ),
      );
    }

    final hasAnalysis = state.bpm != null || state.key != null;
    final hasStyles = state.styles != null && state.styles!.isNotEmpty;

    if (!hasAnalysis && !hasStyles) {
      return const SizedBox(height: 48);
    }

    return SizedBox(
      height: 48,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 24),
        child: Row(
          spacing: 8,
          children: [
            if (state.bpm != null) _chip("BPM ${state.bpm!.round()}"),
            if (state.key != null) _chip(state.key!),
            if (hasStyles)
              ...state.styles!.take(3).map((s) => _styleChip(s.displayName)),
          ],
        ),
      ),
    );
  }

  Widget _chip(String label) {
    return Chip(
      label: Text(label),
      labelStyle: const TextStyle(fontSize: 13, color: Colors.white70),
      side: BorderSide.none,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      backgroundColor: Colors.white.withValues(alpha: 0.1),
    );
  }

  Widget _styleChip(String label) {
    return Chip(
      label: Text(label),
      labelStyle: const TextStyle(fontSize: 13, color: Colors.white70),
      side: BorderSide.none,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      backgroundColor: Colors.purple.withValues(alpha: 0.25),
    );
  }
}

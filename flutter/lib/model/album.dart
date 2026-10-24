import 'package:audio_service/audio_service.dart';

class Album {
  final String name;
  final String? artist;
  final Uri? artUri;
  final List<MediaItem> tracks;

  Album({required this.name, this.artist, this.artUri, required this.tracks});

  int get trackCount => tracks.length;

  Duration get totalDuration => tracks.fold(
    Duration.zero,
    (sum, track) => sum + (track.duration ?? Duration.zero),
  );
}

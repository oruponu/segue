import 'dart:io';
import 'package:audio_metadata_reader/audio_metadata_reader.dart';
import 'package:audio_service/audio_service.dart';
import 'package:drift/drift.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_waveform/just_waveform.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:segue/database/database.dart';
import 'package:segue/model/album.dart';
import 'package:segue/model/library_state.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/providers/database_provider.dart';

final libraryViewModelProvider =
    NotifierProvider<LibraryViewModel, LibraryState>(() {
      return LibraryViewModel();
    });

class LibraryViewModel extends Notifier<LibraryState> {
  @override
  LibraryState build() {
    final handler = ref.watch(audioHandlerProvider);
    handler.playerStateStream.listen((playerState) {
      final isPlaying = playerState.playing;
      state = state.copyWith(isPlaying: isPlaying);
    });

    handler.mediaItem.listen((item) {
      state = state.copyWith(playingMediaItem: item);
    });

    return LibraryState(playingMediaItem: null);
  }

  Future<void> selectDirectory() async {
    String? selectedDirectory = await FilePicker.platform.getDirectoryPath();
    if (selectedDirectory == null) {
      return;
    }

    state = state.copyWith(
      selectedDirectory: selectedDirectory,
      clearSelectedAlbum: true,
      isLoading: true,
    );
    await _loadAudioSources(selectedDirectory);
    state = state.copyWith(isLoading: false, scanTotal: 0, scanProcessed: 0);
  }

  void selectAlbum(Album album) {
    state = state.copyWith(selectedAlbum: album);
  }

  void goBackToAlbums() {
    state = state.copyWith(clearSelectedAlbum: true);
  }

  Future<void> playItem(int index) async {
    final handler = ref.read(audioHandlerProvider);
    final album = state.selectedAlbum;
    if (album != null) {
      await handler.updateQueue(album.tracks);
    }
    await handler.skipToQueueItem(index);
    handler.play();
  }

  List<Album> _groupByAlbum(List<MediaItem> items) {
    final map = <String, List<MediaItem>>{};
    for (final item in items) {
      final albumName = item.album ?? 'Unknown Album';
      map.putIfAbsent(albumName, () => []).add(item);
    }

    for (final tracks in map.values) {
      tracks.sort((a, b) {
        final aDisc = a.extras?['discNumber'] as int?;
        final bDisc = b.extras?['discNumber'] as int?;
        if (aDisc != null || bDisc != null) {
          final discCmp = (aDisc ?? 0).compareTo(bDisc ?? 0);
          if (discCmp != 0) return discCmp;
        }
        final aTrack = a.extras?['trackNumber'] as int?;
        final bTrack = b.extras?['trackNumber'] as int?;
        if (aTrack != null || bTrack != null) {
          return (aTrack ?? 0).compareTo(bTrack ?? 0);
        }
        return a.id.compareTo(b.id);
      });
    }

    final albums = map.entries.map((entry) {
      final tracks = entry.value;
      final artUri = tracks
          .cast<MediaItem?>()
          .firstWhere((track) => track!.artUri != null, orElse: () => null)
          ?.artUri;
      return Album(
        name: entry.key,
        artist: tracks.first.artist,
        artUri: artUri,
        tracks: tracks,
      );
    }).toList();

    albums.sort((a, b) {
      if (a.name == 'Unknown Album') return 1;
      if (b.name == 'Unknown Album') return -1;
      return a.name.compareTo(b.name);
    });

    return albums;
  }

  Future<void> _loadAudioSources(String path) async {
    var audioStatus = await Permission.audio.status;
    if (!audioStatus.isGranted) {
      audioStatus = await Permission.audio.request();
    }

    if (!audioStatus.isGranted) {
      var storageStatus = await Permission.storage.request();
      if (!storageStatus.isGranted) {
        return;
      }
    }

    final directory = Directory(path);
    if (!await directory.exists()) {
      return;
    }

    final files = directory
        .listSync(recursive: true)
        .whereType<File>()
        .toList();

    state = state.copyWith(scanTotal: files.length, scanProcessed: 0);

    final dao = ref.read(trackDaoProvider);
    final cachedTracks = await dao.getTracksByDirectory(path);
    final cacheMap = {for (final track in cachedTracks) track.filePath: track};

    final mediaItems = <MediaItem>[];
    final tempDir = await getTemporaryDirectory();
    for (final file in files) {
      final cached = cacheMap[file.path];
      if (cached != null) {
        final artUri = cached.artCachePath != null
            ? Uri.file(cached.artCachePath!)
            : null;
        mediaItems.add(
          MediaItem(
            id: cached.filePath,
            title: cached.title,
            album: cached.album,
            artist: cached.artist,
            duration: cached.durationMs != null
                ? Duration(milliseconds: cached.durationMs!)
                : null,
            artUri: artUri,
            extras: {
              'discNumber': cached.discNumber,
              'trackNumber': cached.trackNumber,
              'wavePath': cached.waveCachePath,
            },
          ),
        );
      } else {
        final AudioMetadata metadata;
        try {
          metadata = readMetadata(file, getImage: true);
        } catch (_) {
          state = state.copyWith(scanProcessed: state.scanProcessed + 1);
          continue;
        }

        Uri? artUri;
        String? artCachePath;
        if (metadata.pictures.isNotEmpty) {
          final picture = metadata.pictures.first;
          final artFile = File(
            '${tempDir.path}/${metadata.file.path.hashCode}.jpg',
          );
          await artFile.writeAsBytes(picture.bytes);
          artUri = Uri.file(artFile.path);
          artCachePath = artFile.path;
        }

        final waveFile = File(
          '${tempDir.path}/${metadata.file.path.hashCode}.wave',
        );
        if (!await waveFile.exists()) {
          try {
            await JustWaveform.extract(
              audioInFile: metadata.file,
              waveOutFile: waveFile,
            ).drain();
          } catch (_) {
            // デコード非対応フォーマットはスキップ
          }
        }

        final title = metadata.title ?? "Unknown Title";
        final durationMs = metadata.duration?.inMilliseconds;

        mediaItems.add(
          MediaItem(
            id: metadata.file.path,
            title: title,
            album: metadata.album,
            artist: metadata.artist,
            duration: metadata.duration,
            artUri: artUri,
            extras: {
              'discNumber': metadata.discNumber,
              'trackNumber': metadata.trackNumber,
              'wavePath': waveFile.path,
            },
          ),
        );

        await dao.upsertTrack(
          TracksCompanion(
            filePath: Value(file.path),
            title: Value(title),
            album: Value(metadata.album),
            artist: Value(metadata.artist),
            discNumber: Value(metadata.discNumber),
            trackNumber: Value(metadata.trackNumber),
            durationMs: Value(durationMs),
            artCachePath: Value(artCachePath),
            waveCachePath: Value(waveFile.path),
            scannedAt: Value(DateTime.now()),
          ),
        );
      }

      final albums = _groupByAlbum(mediaItems);
      state = state.copyWith(
        playlist: List.of(mediaItems),
        albums: albums,
        scanProcessed: state.scanProcessed + 1,
      );
    }
  }
}

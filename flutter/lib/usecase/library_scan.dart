import 'dart:convert';
import 'dart:io';

import 'package:audio_metadata_reader/audio_metadata_reader.dart';
import 'package:audio_service/audio_service.dart';
import 'package:crypto/crypto.dart';
import 'package:path/path.dart' as p;
import 'package:segue/database/database.dart';
import 'package:segue/model/album.dart';

enum ScanKind { uncached, artMissing }

enum ArtStatus { extracted, noImage, readFailed }

enum DbActionType { none, upsert, updateArt }

class ScanRequest {
  final String filePath;
  final ScanKind kind;
  const ScanRequest(this.filePath, this.kind);
}

class ScanResult {
  final String filePath;
  final ScanKind kind;
  final bool failed;
  final String? title;
  final String? album;
  final String? artist;
  final int? discNumber;
  final int? trackNumber;
  final int? durationMs;
  final String? artCachePath;
  final String? wavePath;
  final ArtStatus? artStatus;

  const ScanResult({
    required this.filePath,
    required this.kind,
    this.failed = false,
    this.title,
    this.album,
    this.artist,
    this.discNumber,
    this.trackNumber,
    this.durationMs,
    this.artCachePath,
    this.wavePath,
    this.artStatus,
  });
}

class DbAction {
  final DbActionType type;
  final String? artCachePath;
  const DbAction.none() : type = DbActionType.none, artCachePath = null;
  const DbAction.upsert() : type = DbActionType.upsert, artCachePath = null;
  const DbAction.updateArt(this.artCachePath) : type = DbActionType.updateArt;
}

class ScanOutcome {
  final MediaItem? mediaItem;
  final DbAction dbAction;
  const ScanOutcome(this.mediaItem, this.dbAction);
}

List<List<T>> chunk<T>(List<T> items, int size) {
  if (size <= 0) {
    throw ArgumentError.value(size, 'size', 'must be greater than 0');
  }
  final out = <List<T>>[];
  for (var i = 0; i < items.length; i += size) {
    final end = (i + size < items.length) ? i + size : items.length;
    out.add(items.sublist(i, end));
  }
  return out;
}

String cacheFilePath(String tempDirPath, String filePath, String extension) {
  final hash = sha1.convert(utf8.encode(filePath));
  return p.join(tempDirPath, '$hash.$extension');
}

List<Album> groupByAlbum(List<MediaItem> items) {
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

class Partition {
  final List<Track> cachedReady;
  final List<ScanRequest> toScan;
  const Partition(this.cachedReady, this.toScan);
}

MediaItem mediaItemFromTrack(
  Track track, {
  String? artCachePathOverride,
  bool clearArt = false,
}) {
  final artPath = clearArt
      ? null
      : (artCachePathOverride ?? track.artCachePath);
  return MediaItem(
    id: track.filePath,
    title: track.title,
    album: track.album,
    artist: track.artist,
    duration: track.durationMs != null
        ? Duration(milliseconds: track.durationMs!)
        : null,
    artUri: artPath != null ? Uri.file(artPath) : null,
    extras: {
      'discNumber': track.discNumber,
      'trackNumber': track.trackNumber,
      'wavePath': track.waveCachePath,
    },
  );
}

ScanResult failedResultFor(ScanRequest req) {
  switch (req.kind) {
    case ScanKind.uncached:
      return ScanResult(
        filePath: req.filePath,
        kind: ScanKind.uncached,
        failed: true,
      );
    case ScanKind.artMissing:
      return ScanResult(
        filePath: req.filePath,
        kind: ScanKind.artMissing,
        artStatus: ArtStatus.readFailed,
      );
  }
}

ScanOutcome applyScanResult(ScanResult r, Track? cached) {
  switch (r.kind) {
    case ScanKind.uncached:
      if (r.failed) {
        return const ScanOutcome(null, DbAction.none());
      }
      final item = MediaItem(
        id: r.filePath,
        title: r.title ?? p.basenameWithoutExtension(r.filePath),
        album: r.album,
        artist: r.artist,
        duration: r.durationMs != null
            ? Duration(milliseconds: r.durationMs!)
            : null,
        artUri: r.artCachePath != null ? Uri.file(r.artCachePath!) : null,
        extras: {
          'discNumber': r.discNumber,
          'trackNumber': r.trackNumber,
          'wavePath': r.wavePath,
        },
      );
      return ScanOutcome(item, const DbAction.upsert());
    case ScanKind.artMissing:
      switch (r.artStatus!) {
        case ArtStatus.extracted:
          return ScanOutcome(
            mediaItemFromTrack(cached!, artCachePathOverride: r.artCachePath),
            DbAction.updateArt(r.artCachePath),
          );
        case ArtStatus.noImage:
          return ScanOutcome(
            mediaItemFromTrack(cached!, clearArt: true),
            const DbAction.updateArt(null),
          );
        case ArtStatus.readFailed:
          return ScanOutcome(
            mediaItemFromTrack(cached!, clearArt: true),
            const DbAction.none(),
          );
      }
  }
}

Partition partitionFiles(
  List<String> filePaths,
  Map<String, Track> cacheMap,
  Set<String> existingArtPaths,
) {
  final cachedReady = <Track>[];
  final toScan = <ScanRequest>[];
  for (final path in filePaths) {
    final cached = cacheMap[path];
    if (cached == null) {
      toScan.add(ScanRequest(path, ScanKind.uncached));
    } else if (cached.artCachePath == null ||
        existingArtPaths.contains(cached.artCachePath)) {
      cachedReady.add(cached);
    } else {
      toScan.add(ScanRequest(path, ScanKind.artMissing));
    }
  }
  return Partition(cachedReady, toScan);
}

List<ScanResult> scanBatch(List<ScanRequest> requests, String tempDirPath) {
  final results = <ScanResult>[];
  for (final req in requests) {
    switch (req.kind) {
      case ScanKind.uncached:
        results.add(_scanUncached(req.filePath, tempDirPath));
      case ScanKind.artMissing:
        results.add(_scanArtMissing(req.filePath, tempDirPath));
    }
  }
  return results;
}

ScanResult _scanUncached(String filePath, String tempDirPath) {
  try {
    final metadata = readMetadata(File(filePath), getImage: true);
    String? artCachePath;
    if (metadata.pictures.isNotEmpty) {
      artCachePath = cacheFilePath(tempDirPath, filePath, 'jpg');
      File(artCachePath).writeAsBytesSync(metadata.pictures.first.bytes);
    }
    return ScanResult(
      filePath: filePath,
      kind: ScanKind.uncached,
      title: metadata.title ?? p.basenameWithoutExtension(filePath),
      album: metadata.album,
      artist: metadata.artist,
      discNumber: metadata.discNumber,
      trackNumber: metadata.trackNumber,
      durationMs: metadata.duration?.inMilliseconds,
      artCachePath: artCachePath,
      wavePath: cacheFilePath(tempDirPath, filePath, 'wave'),
    );
  } catch (_) {
    return ScanResult(
      filePath: filePath,
      kind: ScanKind.uncached,
      failed: true,
    );
  }
}

ScanResult _scanArtMissing(String filePath, String tempDirPath) {
  try {
    final metadata = readMetadata(File(filePath), getImage: true);
    if (metadata.pictures.isEmpty) {
      return ScanResult(
        filePath: filePath,
        kind: ScanKind.artMissing,
        artStatus: ArtStatus.noImage,
      );
    }
    final artCachePath = cacheFilePath(tempDirPath, filePath, 'jpg');
    File(artCachePath).writeAsBytesSync(metadata.pictures.first.bytes);
    return ScanResult(
      filePath: filePath,
      kind: ScanKind.artMissing,
      artStatus: ArtStatus.extracted,
      artCachePath: artCachePath,
    );
  } catch (_) {
    return ScanResult(
      filePath: filePath,
      kind: ScanKind.artMissing,
      artStatus: ArtStatus.readFailed,
    );
  }
}

abstract class ScanWriter {
  Future<void> runInTransaction(Future<void> Function() action);
  Future<void> upsertTrack(TracksCompanion entry);
  Future<void> updateArtCachePath(String filePath, String? artCachePath);
}

import 'package:audio_service/audio_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:segue/database/database.dart';
import 'package:segue/usecase/library_scan.dart';

void main() {
  group('chunk', () {
    test('splits into fixed-size chunks with remainder', () {
      expect(chunk([1, 2, 3, 4, 5], 2), [
        [1, 2],
        [3, 4],
        [5],
      ]);
    });

    test('empty input yields empty list', () {
      expect(chunk(<int>[], 3), isEmpty);
    });

    test('throws ArgumentError for non-positive size', () {
      expect(() => chunk([1, 2], 0), throwsArgumentError);
      expect(() => chunk([1, 2], -1), throwsArgumentError);
    });
  });

  group('cacheFilePath', () {
    test('is deterministic SHA-1 based and joins temp dir', () {
      final a = cacheFilePath('/tmp', '/music/song.mp3', 'jpg');
      final b = cacheFilePath('/tmp', '/music/song.mp3', 'jpg');
      expect(a, b);
      expect(a.endsWith('.jpg'), isTrue);
      expect(a.startsWith('/tmp'), isTrue);
    });

    test('different paths produce different names', () {
      final a = cacheFilePath('/tmp', '/music/a.mp3', 'jpg');
      final b = cacheFilePath('/tmp', '/music/b.mp3', 'jpg');
      expect(a, isNot(b));
    });
  });

  group('partitionFiles', () {
    test('classifies uncached / cachedReady / artMissing', () {
      final cacheMap = {
        '/m/cached_no_art.mp3': _track('/m/cached_no_art.mp3'),
        '/m/cached_art_ok.mp3': _track(
          '/m/cached_art_ok.mp3',
          artCachePath: '/tmp/ok.jpg',
        ),
        '/m/cached_art_gone.mp3': _track(
          '/m/cached_art_gone.mp3',
          artCachePath: '/tmp/gone.jpg',
        ),
      };
      final result = partitionFiles(
        [
          '/m/new.mp3',
          '/m/cached_no_art.mp3',
          '/m/cached_art_ok.mp3',
          '/m/cached_art_gone.mp3',
        ],
        cacheMap,
        {'/tmp/ok.jpg'}, // ok exists, gone does not
      );

      expect(result.cachedReady.map((t) => t.filePath).toList(), [
        '/m/cached_no_art.mp3',
        '/m/cached_art_ok.mp3',
      ]);
      expect(
        result.toScan.map((r) => '${r.filePath}:${r.kind.name}').toList(),
        ['/m/new.mp3:uncached', '/m/cached_art_gone.mp3:artMissing'],
      );
    });
  });

  group('mediaItemFromTrack', () {
    test('builds MediaItem with art uri from track', () {
      final item = mediaItemFromTrack(
        _track('/m/x.mp3', artCachePath: '/tmp/x.jpg'),
      );
      expect(item.id, '/m/x.mp3');
      expect(item.artUri, Uri.file('/tmp/x.jpg'));
      expect(item.extras?['wavePath'], '/tmp//m/x.mp3.wave');
    });

    test('clearArt drops the art uri', () {
      final item = mediaItemFromTrack(
        _track('/m/x.mp3', artCachePath: '/tmp/x.jpg'),
        clearArt: true,
      );
      expect(item.artUri, isNull);
    });

    test('override replaces the art path', () {
      final item = mediaItemFromTrack(
        _track('/m/x.mp3', artCachePath: '/tmp/old.jpg'),
        artCachePathOverride: '/tmp/new.jpg',
      );
      expect(item.artUri, Uri.file('/tmp/new.jpg'));
    });
  });

  group('applyScanResult', () {
    test('uncached success -> MediaItem + upsert', () {
      final r = ScanResult(
        filePath: '/m/new.mp3',
        kind: ScanKind.uncached,
        title: 'Song',
        album: 'Al',
        artCachePath: '/tmp/new.jpg',
        wavePath: '/tmp/new.wave',
      );
      final o = applyScanResult(r, null);
      expect(o.mediaItem!.id, '/m/new.mp3');
      expect(o.mediaItem!.artUri, Uri.file('/tmp/new.jpg'));
      expect(o.dbAction.type, DbActionType.upsert);
    });

    test('uncached failed -> skip, no db', () {
      final o = applyScanResult(
        const ScanResult(
          filePath: '/m/bad.mp3',
          kind: ScanKind.uncached,
          failed: true,
        ),
        null,
      );
      expect(o.mediaItem, isNull);
      expect(o.dbAction.type, DbActionType.none);
    });

    test('artMissing extracted -> new art + updateArt(path)', () {
      final cached = _track('/m/c.mp3', artCachePath: '/tmp/old.jpg');
      final o = applyScanResult(
        const ScanResult(
          filePath: '/m/c.mp3',
          kind: ScanKind.artMissing,
          artStatus: ArtStatus.extracted,
          artCachePath: '/tmp/c.jpg',
        ),
        cached,
      );
      expect(o.mediaItem!.artUri, Uri.file('/tmp/c.jpg'));
      expect(o.dbAction.type, DbActionType.updateArt);
      expect(o.dbAction.artCachePath, '/tmp/c.jpg');
    });

    test('artMissing noImage -> art cleared + updateArt(null)', () {
      final cached = _track('/m/c.mp3', artCachePath: '/tmp/old.jpg');
      final o = applyScanResult(
        const ScanResult(
          filePath: '/m/c.mp3',
          kind: ScanKind.artMissing,
          artStatus: ArtStatus.noImage,
        ),
        cached,
      );
      expect(o.mediaItem!.artUri, isNull);
      expect(o.dbAction.type, DbActionType.updateArt);
      expect(o.dbAction.artCachePath, isNull);
    });

    test('artMissing readFailed -> art cleared, db untouched', () {
      final cached = _track('/m/c.mp3', artCachePath: '/tmp/old.jpg');
      final o = applyScanResult(
        const ScanResult(
          filePath: '/m/c.mp3',
          kind: ScanKind.artMissing,
          artStatus: ArtStatus.readFailed,
        ),
        cached,
      );
      expect(o.mediaItem!.id, '/m/c.mp3');
      expect(o.mediaItem!.artUri, isNull);
      expect(o.dbAction.type, DbActionType.none);
    });

    test('failedResultFor maps kind to failure result', () {
      expect(
        failedResultFor(const ScanRequest('/a', ScanKind.uncached)).failed,
        isTrue,
      );
      expect(
        failedResultFor(const ScanRequest('/b', ScanKind.artMissing)).artStatus,
        ArtStatus.readFailed,
      );
    });
  });

  group('groupByAlbum', () {
    MediaItem item(String id, String? album, {int? disc, int? track}) =>
        MediaItem(
          id: id,
          title: id,
          album: album,
          extras: {'discNumber': disc, 'trackNumber': track},
        );

    test('sorts albums by name with Unknown Album last', () {
      final albums = groupByAlbum([
        item('a', 'Zebra'),
        item('b', null),
        item('c', 'Apple'),
      ]);
      expect(albums.map((a) => a.name).toList(), [
        'Apple',
        'Zebra',
        'Unknown Album',
      ]);
    });

    test('orders tracks within an album by disc then track number', () {
      final albums = groupByAlbum([
        item('a', 'X', disc: 1, track: 2),
        item('b', 'X', disc: 2, track: 1),
        item('c', 'X', disc: 1, track: 1),
      ]);
      final tracks = albums.single.tracks.map((t) => t.id).toList();
      expect(tracks, ['c', 'a', 'b']);
    });
  });
}

Track _track(String filePath, {String? artCachePath}) => Track(
  filePath: filePath,
  title: 'T',
  album: 'AL',
  artist: 'AR',
  discNumber: null,
  trackNumber: null,
  durationMs: null,
  artCachePath: artCachePath,
  waveCachePath: '/tmp/$filePath.wave',
  bpm: null,
  bpmConfidence: null,
  musicalKey: null,
  keyConfidence: null,
  stylesJson: null,
  lufsCachePath: null,
  integratedLufs: null,
  scannedAt: DateTime(2026),
  analyzedAt: null,
);

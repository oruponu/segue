import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:just_audio/just_audio.dart';
import 'package:segue/database/database.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/usecase/library_scan.dart';
import 'package:segue/view_model/library_view_model.dart';

class _FakeWriter implements ScanWriter {
  final upserts = <String>[];
  final artUpdates = <String, String?>{};
  int transactions = 0;

  @override
  Future<void> runInTransaction(Future<void> Function() action) async {
    transactions++;
    await action();
  }

  @override
  Future<void> upsertTrack(TracksCompanion entry) async {
    upserts.add(entry.filePath.value);
  }

  @override
  Future<void> updateArtCachePath(String filePath, String? artCachePath) async {
    artUpdates[filePath] = artCachePath;
  }
}

Track _track(String filePath, {String? artCachePath}) => Track(
  filePath: filePath,
  title: 'T-$filePath',
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

ScanResult _ok(String filePath) => ScanResult(
  filePath: filePath,
  kind: ScanKind.uncached,
  title: 'New-$filePath',
  album: 'AL',
  wavePath: '/tmp/$filePath.wave',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late ProviderContainer container;
  late LibraryViewModel vm;

  setUp(() {
    container = ProviderContainer(
      overrides: [
        audioHandlerProvider.overrideWithValue(AudioHandler(AudioPlayer())),
      ],
    );
    vm = container.read(libraryViewModelProvider.notifier);
  });
  tearDown(() => container.dispose());

  test('cached only: initial reflection, no batches', () async {
    final writer = _FakeWriter();
    await vm.runScan(
      gen: vm.debugCurrentGeneration,
      partition: Partition([_track('/m/a.mp3'), _track('/m/b.mp3')], []),
      cacheMap: {},
      writer: writer,
      runBatch: (_) async => <ScanResult>[],
    );
    final s = container.read(libraryViewModelProvider);
    expect(s.scanProcessed, 2);
    expect(s.albums.single.tracks.length, 2);
    expect(writer.transactions, 0);
  });

  test(
    'mixed: uncached success added, failure skipped, one transaction',
    () async {
      final writer = _FakeWriter();
      await vm.runScan(
        gen: vm.debugCurrentGeneration,
        partition: Partition([], [
          const ScanRequest('/m/x.mp3', ScanKind.uncached),
          const ScanRequest('/m/y.mp3', ScanKind.uncached),
        ]),
        cacheMap: {},
        writer: writer,
        runBatch: (batch) async => [
          _ok('/m/x.mp3'),
          const ScanResult(
            filePath: '/m/y.mp3',
            kind: ScanKind.uncached,
            failed: true,
          ),
        ],
      );
      final s = container.read(libraryViewModelProvider);
      expect(s.scanProcessed, 2);
      expect(s.albums.single.tracks.map((t) => t.id), ['/m/x.mp3']);
      expect(writer.upserts, ['/m/x.mp3']);
      expect(writer.transactions, 1);
    },
  );

  test('batch failure: artMissing kept from cache, uncached skipped', () async {
    final writer = _FakeWriter();
    final cached = _track('/m/c.mp3', artCachePath: '/tmp/old.jpg');
    await vm.runScan(
      gen: vm.debugCurrentGeneration,
      partition: Partition([], [
        const ScanRequest('/m/new.mp3', ScanKind.uncached),
        const ScanRequest('/m/c.mp3', ScanKind.artMissing),
      ]),
      cacheMap: {'/m/c.mp3': cached},
      writer: writer,
      runBatch: (_) async => throw StateError('isolate boom'),
    );
    final s = container.read(libraryViewModelProvider);
    expect(s.albums.single.tracks.map((t) => t.id), ['/m/c.mp3']);
    expect(s.albums.single.tracks.single.artUri, isNull);
    expect(writer.upserts, isEmpty);
    expect(writer.artUpdates, isEmpty);
  });

  test('re-entry: stale generation drops batch updates', () async {
    final writer = _FakeWriter();
    final gen = vm.debugCurrentGeneration;
    await vm.runScan(
      gen: gen,
      partition: Partition([], [
        const ScanRequest('/m/x.mp3', ScanKind.uncached),
      ]),
      cacheMap: {},
      writer: writer,
      runBatch: (_) async {
        vm.debugBumpGeneration();
        return [_ok('/m/x.mp3')];
      },
    );
    final s = container.read(libraryViewModelProvider);
    expect(writer.transactions, 0);
    expect(s.albums, isEmpty);
  });
}

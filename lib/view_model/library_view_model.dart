import 'dart:io';
import 'dart:isolate';
import 'package:audio_service/audio_service.dart';
import 'package:drift/drift.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
import 'package:permission_handler/permission_handler.dart';
import 'package:segue/database/database.dart';
import 'package:segue/database/track_dao.dart';
import 'package:segue/model/album.dart';
import 'package:segue/model/library_state.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/providers/database_provider.dart';
import 'package:segue/usecase/library_scan.dart';

final libraryViewModelProvider =
    NotifierProvider<LibraryViewModel, LibraryState>(() {
      return LibraryViewModel();
    });

class LibraryViewModel extends Notifier<LibraryState> {
  static const _scanBatchSize = 50;
  int _scanGeneration = 0;

  @override
  LibraryState build() {
    final handler = ref.watch(audioHandlerProvider);
    final playerSub = handler.playerStateStream.listen((playerState) {
      final isPlaying = playerState.playing;
      state = state.copyWith(isPlaying: isPlaying);
    });

    final mediaSub = handler.mediaItem.listen((item) {
      state = state.copyWith(playingMediaItem: item);
    });

    ref.onDispose(() {
      playerSub.cancel();
      mediaSub.cancel();
    });

    return LibraryState(playingMediaItem: null);
  }

  Future<void> selectDirectory() async {
    String? selectedDirectory = await FilePicker.getDirectoryPath();
    if (selectedDirectory == null) {
      return;
    }

    final gen = ++_scanGeneration;
    // 新しいスキャン開始時に旧フォルダの一覧・進捗をクリア
    // （列挙中に前回の表示が残らないようにする）
    state = state.copyWith(
      selectedDirectory: selectedDirectory,
      clearSelectedAlbum: true,
      isLoading: true,
      albums: [],
      scanTotal: 0,
      scanProcessed: 0,
    );
    try {
      await _loadAudioSources(selectedDirectory, gen);
    } finally {
      if (gen == _scanGeneration) {
        state = state.copyWith(
          isLoading: false,
          scanTotal: 0,
          scanProcessed: 0,
        );
      }
    }
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

  Future<void> _loadAudioSources(String path, int gen) async {
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

    final files = await directory
        .list(recursive: true)
        .where((e) => e is File)
        .cast<File>()
        .toList();
    final filePaths = files.map((f) => f.path).toList();

    if (gen != _scanGeneration) return;
    state = state.copyWith(scanTotal: filePaths.length, scanProcessed: 0);

    final dao = ref.read(trackDaoProvider);
    final cachedTracks = await dao.getTracksByDirectory(path);
    final cacheMap = {for (final track in cachedTracks) track.filePath: track};
    final tempDirPath = (await getTemporaryDirectory()).path;

    final artPaths = cacheMap.values
        .map((t) => t.artCachePath)
        .whereType<String>()
        .toList();
    // 存在確認は上限付きバッチで並行実行（大規模ライブラリでの I/O 集中を避ける）
    final existingArtPaths = <String>{};
    for (final batch in chunk(artPaths, _scanBatchSize)) {
      final exists = await Future.wait(
        batch.map((path) => File(path).exists()),
      );
      for (var i = 0; i < batch.length; i++) {
        if (exists[i]) existingArtPaths.add(batch[i]);
      }
    }

    final partition = partitionFiles(filePaths, cacheMap, existingArtPaths);

    await runScan(
      gen: gen,
      partition: partition,
      cacheMap: cacheMap,
      writer: _writerFor(dao),
      runBatch: (batch) => Isolate.run(() => scanBatch(batch, tempDirPath)),
    );
  }

  static ScanWriter _writerFor(TrackDao dao) => _DaoScanWriter(dao);

  TracksCompanion _companionForResult(ScanResult r) => TracksCompanion(
    filePath: Value(r.filePath),
    title: Value(r.title ?? p.basenameWithoutExtension(r.filePath)),
    album: Value(r.album),
    artist: Value(r.artist),
    discNumber: Value(r.discNumber),
    trackNumber: Value(r.trackNumber),
    durationMs: Value(r.durationMs),
    artCachePath: Value(r.artCachePath),
    waveCachePath: Value(r.wavePath),
    scannedAt: Value(DateTime.now()),
  );

  @visibleForTesting
  int get debugCurrentGeneration => _scanGeneration;

  @visibleForTesting
  void debugBumpGeneration() => _scanGeneration++;

  @visibleForTesting
  Future<void> runScan({
    required int gen,
    required Partition partition,
    required Map<String, Track> cacheMap,
    required ScanWriter writer,
    required Future<List<ScanResult>> Function(List<ScanRequest> batch)
    runBatch,
  }) async {
    final mediaItems = <MediaItem>[];
    for (final track in partition.cachedReady) {
      mediaItems.add(mediaItemFromTrack(track));
    }
    if (gen != _scanGeneration) return;
    state = state.copyWith(
      albums: groupByAlbum(mediaItems),
      scanProcessed: partition.cachedReady.length,
    );

    for (final batch in chunk(partition.toScan, _scanBatchSize)) {
      if (gen != _scanGeneration) return;
      List<ScanResult> results;
      try {
        results = await runBatch(batch);
      } catch (_) {
        results = batch.map(failedResultFor).toList();
      }
      if (gen != _scanGeneration) return;

      await writer.runInTransaction(() async {
        for (final r in results) {
          final outcome = applyScanResult(r, cacheMap[r.filePath]);
          if (outcome.mediaItem != null) {
            mediaItems.add(outcome.mediaItem!);
          }
          switch (outcome.dbAction.type) {
            case DbActionType.none:
              break;
            case DbActionType.upsert:
              await writer.upsertTrack(_companionForResult(r));
            case DbActionType.updateArt:
              await writer.updateArtCachePath(
                r.filePath,
                outcome.dbAction.artCachePath,
              );
          }
        }
      });

      if (gen != _scanGeneration) return;
      state = state.copyWith(
        albums: groupByAlbum(mediaItems),
        scanProcessed: state.scanProcessed + results.length,
      );
    }

    if (gen != _scanGeneration) return;
    state = state.copyWith(albums: groupByAlbum(mediaItems));
  }
}

class _DaoScanWriter implements ScanWriter {
  final TrackDao _dao;
  _DaoScanWriter(this._dao);

  @override
  Future<void> runInTransaction(Future<void> Function() action) =>
      _dao.transaction(action);

  @override
  Future<void> upsertTrack(TracksCompanion entry) => _dao.upsertTrack(entry);

  @override
  Future<void> updateArtCachePath(String filePath, String? artCachePath) =>
      _dao.updateArtCachePath(filePath, artCachePath);
}

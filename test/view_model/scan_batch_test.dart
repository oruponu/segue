import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:segue/usecase/library_scan.dart';

void main() {
  late Directory tempDir;

  setUp(() {
    tempDir = Directory.systemTemp.createTempSync('scan_batch_test');
  });
  tearDown(() {
    if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
  });

  test('scanBatch reads tags and writes artwork for uncached file', () {
    final results = scanBatch([
      const ScanRequest('test/fixtures/tagged_sample.mp3', ScanKind.uncached),
    ], tempDir.path);
    final r = results.single;
    expect(r.failed, isFalse);
    expect(r.title, 'Sample Title');
    expect(r.album, 'Sample Album');
    expect(r.artCachePath, isNotNull);
    expect(File(r.artCachePath!).existsSync(), isTrue);
    expect(r.wavePath, endsWith('.wave'));
  });

  test('scanBatch marks unreadable/missing file as failed', () {
    final results = scanBatch([
      const ScanRequest('test/fixtures/does_not_exist.mp3', ScanKind.uncached),
    ], tempDir.path);
    expect(results.single.failed, isTrue);
  });

  test('scanBatch returns noImage for tagless file (artMissing)', () {
    final results = scanBatch([
      const ScanRequest('test/fixtures/no_tags.mp3', ScanKind.artMissing),
    ], tempDir.path);
    expect(results.single.artStatus, ArtStatus.noImage);
  });
}

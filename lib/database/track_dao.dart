import 'package:drift/drift.dart';
import 'package:segue/database/database.dart';
import 'package:segue/database/tables.dart';

part 'track_dao.g.dart';

@DriftAccessor(tables: [Tracks])
class TrackDao extends DatabaseAccessor<AppDatabase> with _$TrackDaoMixin {
  TrackDao(super.db);

  Future<Track?> getTrackByPath(String filePath) {
    return (select(
      tracks,
    )..where((track) => track.filePath.equals(filePath))).getSingleOrNull();
  }

  Future<List<Track>> getTracksByDirectory(String directoryPath) {
    final prefix = directoryPath.endsWith('/') || directoryPath.endsWith('\\')
        ? directoryPath
        : '$directoryPath/';
    return (select(
      tracks,
    )..where((track) => track.filePath.like('$prefix%'))).get();
  }

  Future<void> upsertTrack(TracksCompanion entry) {
    return into(tracks).insertOnConflictUpdate(entry);
  }

  Future<void> saveAnalysisResult({
    required String filePath,
    required double bpm,
    required double bpmConfidence,
    required String musicalKey,
    required double keyConfidence,
  }) {
    return (update(
      tracks,
    )..where((track) => track.filePath.equals(filePath))).write(
      TracksCompanion(
        bpm: Value(bpm),
        bpmConfidence: Value(bpmConfidence),
        musicalKey: Value(musicalKey),
        keyConfidence: Value(keyConfidence),
        analyzedAt: Value(DateTime.now()),
      ),
    );
  }

  Future<void> saveStylePredictions({
    required String filePath,
    required String stylesJson,
  }) {
    return (update(tracks)..where((track) => track.filePath.equals(filePath)))
        .write(TracksCompanion(stylesJson: Value(stylesJson)));
  }
}

import 'package:drift/drift.dart';

class Tracks extends Table {
  TextColumn get filePath => text()();
  TextColumn get title => text().withDefault(const Constant('Unknown Title'))();
  TextColumn get album => text().nullable()();
  TextColumn get artist => text().nullable()();
  IntColumn get trackNumber => integer().nullable()();
  IntColumn get durationMs => integer().nullable()();
  TextColumn get artCachePath => text().nullable()();
  TextColumn get waveCachePath => text().nullable()();
  RealColumn get bpm => real().nullable()();
  RealColumn get bpmConfidence => real().nullable()();
  TextColumn get musicalKey => text().nullable()();
  RealColumn get keyConfidence => real().nullable()();
  TextColumn get stylesJson => text().nullable()();
  DateTimeColumn get scannedAt => dateTime()();
  DateTimeColumn get analyzedAt => dateTime().nullable()();

  @override
  Set<Column> get primaryKey => {filePath};
}

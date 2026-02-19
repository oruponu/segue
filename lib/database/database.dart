import 'package:drift_flutter/drift_flutter.dart';
import 'package:drift/drift.dart';
import 'package:segue/database/tables.dart';
import 'package:segue/database/track_dao.dart';

part 'database.g.dart';

@DriftDatabase(tables: [Tracks], daos: [TrackDao])
class AppDatabase extends _$AppDatabase {
  AppDatabase._() : super(driftDatabase(name: 'segue'));

  static AppDatabase? _instance;

  factory AppDatabase() {
    return _instance ??= AppDatabase._();
  }

  @override
  int get schemaVersion => 1;
}

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:segue/database/database.dart';
import 'package:segue/database/track_dao.dart';

final databaseProvider = Provider<AppDatabase>((ref) {
  return AppDatabase();
});

final trackDaoProvider = Provider<TrackDao>((ref) {
  return ref.watch(databaseProvider).trackDao;
});

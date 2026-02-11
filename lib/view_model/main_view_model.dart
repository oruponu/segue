import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:segue/providers/player_sheet_controller_provider.dart';
import 'package:segue/view_model/library_view_model.dart';

final mainViewModelProvider = NotifierProvider<MainViewModel, bool>(() {
  return MainViewModel();
});

/// state = canPop
class MainViewModel extends Notifier<bool> {
  @override
  bool build() {
    final isPlayerExpanded = ref
        .watch(playerSheetControllerProvider)
        .isExpanded;
    final hasSelectedAlbum =
        ref.watch(libraryViewModelProvider).selectedAlbum != null;
    return !isPlayerExpanded && !hasSelectedAlbum;
  }

  void handleBack() {
    if (ref.read(playerSheetControllerProvider).isExpanded) {
      ref.read(playerSheetControllerProvider.notifier).collapse();
      return;
    }
    if (ref.read(libraryViewModelProvider).selectedAlbum != null) {
      ref.read(libraryViewModelProvider.notifier).goBackToAlbums();
    }
  }
}

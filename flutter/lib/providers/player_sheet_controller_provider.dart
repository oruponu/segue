import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:segue/model/player_sheet_state.dart';

final playerSheetControllerProvider =
    NotifierProvider<PlayerSheetController, PlayerSheetState>(() {
      return PlayerSheetController();
    });

class PlayerSheetController extends Notifier<PlayerSheetState> {
  @override
  PlayerSheetState build() {
    return PlayerSheetState(PlayerSheetAction.none, 0);
  }

  void expand() {
    state = PlayerSheetState(
      PlayerSheetAction.expand,
      DateTime.now().millisecondsSinceEpoch,
      isExpanded: true,
    );
  }

  void collapse() {
    state = PlayerSheetState(
      PlayerSheetAction.collapse,
      DateTime.now().millisecondsSinceEpoch,
      isExpanded: false,
    );
  }

  void setExpanded(bool expanded) {
    if (expanded == state.isExpanded) return;
    state = PlayerSheetState(
      PlayerSheetAction.none,
      state.timestamp,
      isExpanded: expanded,
    );
  }
}

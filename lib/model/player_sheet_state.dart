enum PlayerSheetAction { none, expand, collapse }

class PlayerSheetState {
  final PlayerSheetAction action;
  final int timestamp;

  PlayerSheetState(this.action, this.timestamp);
}

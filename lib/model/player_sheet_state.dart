enum PlayerSheetAction { none, expand, collapse }

class PlayerSheetState {
  final PlayerSheetAction action;
  final int timestamp;
  final bool isExpanded;

  PlayerSheetState(this.action, this.timestamp, {this.isExpanded = false});
}

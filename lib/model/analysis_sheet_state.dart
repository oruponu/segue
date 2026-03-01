enum AnalysisSheetAction { none, expand, collapse }

class AnalysisSheetState {
  final AnalysisSheetAction action;
  final int timestamp;
  final bool isExpanded;

  AnalysisSheetState(this.action, this.timestamp, {this.isExpanded = false});
}

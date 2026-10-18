import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:segue/model/analysis_sheet_state.dart';

final analysisSheetControllerProvider =
    NotifierProvider<AnalysisSheetController, AnalysisSheetState>(() {
      return AnalysisSheetController();
    });

class AnalysisSheetController extends Notifier<AnalysisSheetState> {
  @override
  AnalysisSheetState build() {
    return AnalysisSheetState(AnalysisSheetAction.none, 0);
  }

  void expand() {
    state = AnalysisSheetState(
      AnalysisSheetAction.expand,
      DateTime.now().millisecondsSinceEpoch,
      isExpanded: true,
    );
  }

  void collapse() {
    state = AnalysisSheetState(
      AnalysisSheetAction.collapse,
      DateTime.now().millisecondsSinceEpoch,
      isExpanded: false,
    );
  }

  void setExpanded(bool expanded) {
    if (expanded == state.isExpanded) return;
    state = AnalysisSheetState(
      AnalysisSheetAction.none,
      state.timestamp,
      isExpanded: expanded,
    );
  }
}

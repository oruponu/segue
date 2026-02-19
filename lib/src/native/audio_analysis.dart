import 'dart:convert';
import 'dart:developer' as dev;
import 'dart:ffi';
import 'dart:isolate';

import 'package:ffi/ffi.dart';

import 'discogs_labels.dart';
import 'essentia_bindings.dart';
import 'native_library.dart';

class AnalysisResult {
  final double bpm;
  final double bpmConfidence;
  final String key;
  final double keyConfidence;

  const AnalysisResult({
    required this.bpm,
    required this.bpmConfidence,
    required this.key,
    required this.keyConfidence,
  });
}

class StylePrediction {
  final int labelIndex;
  final double confidence;
  final String genre;
  final String style;
  final String displayName;

  const StylePrediction({
    required this.labelIndex,
    required this.confidence,
    required this.genre,
    required this.style,
    required this.displayName,
  });

  static final Set<String> _duplicateStyles = _buildDuplicateStyles();

  static Set<String> _buildDuplicateStyles() {
    final counts = <String, int>{};
    for (final label in discogsLabels) {
      final parts = label.split('---');
      final style = parts.length > 1 ? parts[1] : parts[0];
      counts[style] = (counts[style] ?? 0) + 1;
    }
    return {
      for (final e in counts.entries)
        if (e.value > 1) e.key,
    };
  }

  static String listToJson(List<StylePrediction> predictions) {
    return jsonEncode([
      for (final prediction in predictions)
        {
          'labelIndex': prediction.labelIndex,
          'confidence': prediction.confidence,
        },
    ]);
  }

  static List<StylePrediction> listFromJson(String json) {
    final list = (jsonDecode(json) as List).cast<Map<String, dynamic>>();
    return [
      for (final entry in list)
        fromLabelIndex(
          entry['labelIndex'] as int,
          (entry['confidence'] as num).toDouble(),
        ),
    ];
  }

  static StylePrediction fromLabelIndex(int index, double confidence) {
    final label = discogsLabels[index];
    final parts = label.split('---');
    final genre = parts[0];
    final style = parts.length > 1 ? parts[1] : parts[0];
    final displayName = _duplicateStyles.contains(style)
        ? '$style ($genre)'
        : style;
    return StylePrediction(
      labelIndex: index,
      confidence: confidence,
      genre: genre,
      style: style,
      displayName: displayName,
    );
  }
}

class AudioAnalysis {
  static DynamicLibrary? _lib;
  static late final EssentiaCancelFlagCreate _cancelFlagCreate;
  static late final EssentiaCancelFlagSet _cancelFlagSet;
  static late final EssentiaCancelFlagDestroy _cancelFlagDestroy;
  static late final EssentiaInit _init;

  static Pointer<EssentiaCancelFlag>? _currentCancelFlag;
  static Pointer<EssentiaCancelFlag>? _currentStyleCancelFlag;

  static void ensureInitialized() {
    if (_lib != null) return;

    final lib = openEssentiaLibrary();
    _lib = lib;

    _cancelFlagCreate = lib
        .lookupFunction<
          EssentiaCancelFlagCreateNative,
          EssentiaCancelFlagCreate
        >('essentia_cancel_flag_create');

    _cancelFlagSet = lib
        .lookupFunction<EssentiaCancelFlagSetNative, EssentiaCancelFlagSet>(
          'essentia_cancel_flag_set',
        );

    _cancelFlagDestroy = lib
        .lookupFunction<
          EssentiaCancelFlagDestroyNative,
          EssentiaCancelFlagDestroy
        >('essentia_cancel_flag_destroy');

    _init = lib.lookupFunction<EssentiaInitNative, EssentiaInit>(
      'essentia_init',
    );

    _init();
  }

  static Future<AnalysisResult?> analyze({required String pathStr}) async {
    ensureInitialized();

    // 前回の分析にキャンセルを通知（破棄は前回の finally に任せる）
    final oldFlag = _currentCancelFlag;
    if (oldFlag != null) {
      _cancelFlagSet(oldFlag);
    }

    final flag = _cancelFlagCreate();
    _currentCancelFlag = flag;
    final flagAddress = flag.address;

    try {
      final result = await Isolate.run(() {
        return _runAnalysis(pathStr, flagAddress);
      });
      return result;
    } finally {
      _cancelFlagDestroy(flag);
      if (_currentCancelFlag == flag) {
        _currentCancelFlag = null;
      }
    }
  }

  static Future<List<StylePrediction>?> classifyStyle({
    required String pathStr,
    required String modelPath,
  }) async {
    ensureInitialized();

    final oldFlag = _currentStyleCancelFlag;
    if (oldFlag != null) {
      _cancelFlagSet(oldFlag);
    }

    final flag = _cancelFlagCreate();
    _currentStyleCancelFlag = flag;
    final flagAddress = flag.address;

    try {
      final result = await Isolate.run(() {
        return _runStyleClassification(pathStr, modelPath, flagAddress);
      });
      return result;
    } finally {
      _cancelFlagDestroy(flag);
      if (_currentStyleCancelFlag == flag) {
        _currentStyleCancelFlag = null;
      }
    }
  }

  static const _errorMessages = {
    0: 'success',
    1: 'cancelled',
    2: 'decode error',
    3: 'analysis error',
    4: 'model load error',
  };

  static AnalysisResult? _runAnalysis(String pathStr, int flagAddress) {
    dev.log('analyze: path=$pathStr', name: 'Essentia');

    final lib = openEssentiaLibrary();
    final analyze = lib.lookupFunction<EssentiaAnalyzeNative, EssentiaAnalyze>(
      'essentia_analyze',
    );

    final pathPtr = pathStr.toNativeUtf8();
    final flag = Pointer<EssentiaCancelFlag>.fromAddress(flagAddress);

    try {
      final result = analyze(pathPtr, flag);

      dev.log(
        'result: errorCode=${result.errorCode} (${_errorMessages[result.errorCode] ?? "unknown"}), '
        'bpm=${result.bpm}, bpmConf=${result.bpmConfidence}, '
        'keyNote=${result.keyNote}, keyScale=${result.keyScale}, keyConf=${result.keyConfidence}',
        name: 'Essentia',
      );

      if (result.errorCode != 0) {
        return null;
      }

      return AnalysisResult(
        bpm: result.bpm,
        bpmConfidence: result.bpmConfidence,
        key: _noteToString(result.keyNote, result.keyScale),
        keyConfidence: result.keyConfidence,
      );
    } finally {
      malloc.free(pathPtr);
    }
  }

  static List<StylePrediction>? _runStyleClassification(
    String pathStr,
    String modelPath,
    int flagAddress,
  ) {
    dev.log('classifyStyle: path=$pathStr', name: 'Essentia');

    final lib = openEssentiaLibrary();
    final classify = lib
        .lookupFunction<EssentiaClassifyStyleNative, EssentiaClassifyStyle>(
          'essentia_classify_style',
        );

    final pathPtr = pathStr.toNativeUtf8();
    final modelPtr = modelPath.toNativeUtf8();
    final flag = Pointer<EssentiaCancelFlag>.fromAddress(flagAddress);

    try {
      final result = classify(pathPtr, modelPtr, flag);

      dev.log(
        'style result: errorCode=${result.errorCode} '
        '(${_errorMessages[result.errorCode] ?? "unknown"}), '
        'count=${result.count}',
        name: 'Essentia',
      );

      if (result.errorCode != 0) {
        return null;
      }

      final predictions = <StylePrediction>[];
      for (int i = 0; i < result.count; i++) {
        predictions.add(
          StylePrediction.fromLabelIndex(
            result.indices[i],
            result.confidences[i],
          ),
        );
      }
      return predictions;
    } finally {
      malloc.free(pathPtr);
      malloc.free(modelPtr);
    }
  }

  static void cancelAnalyze() {
    final flag = _currentCancelFlag;
    if (flag != null) {
      _cancelFlagSet(flag);
    }
  }

  static void cancelStyleClassify() {
    final flag = _currentStyleCancelFlag;
    if (flag != null) {
      _cancelFlagSet(flag);
    }
  }

  static const _majorNames = [
    'C',
    'Db',
    'D',
    'Eb',
    'E',
    'F',
    'Gb',
    'G',
    'Ab',
    'A',
    'Bb',
    'B',
  ];
  static const _minorNames = [
    'C',
    'C#',
    'D',
    'Eb',
    'E',
    'F',
    'F#',
    'G',
    'G#',
    'A',
    'Bb',
    'B',
  ];

  static String _noteToString(int noteIndex, int scale) {
    if (noteIndex < 0 || noteIndex > 11) return 'Unknown';

    if (scale == 1) {
      return '${_minorNames[noteIndex]} Minor';
    } else {
      return '${_majorNames[noteIndex]} Major';
    }
  }
}

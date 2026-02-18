import 'dart:developer' as dev;
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

class ModelManager {
  static String? _cachedModelPath;

  static Future<String> ensureModel(String assetPath) async {
    if (_cachedModelPath != null && File(_cachedModelPath!).existsSync()) {
      return _cachedModelPath!;
    }

    final appDir = await getApplicationSupportDirectory();
    final modelFile = File('${appDir.path}/$assetPath');

    final data = await rootBundle.load('assets/$assetPath');
    final assetSize = data.lengthInBytes;

    // ファイルが存在しないかサイズが異なる場合は再展開する
    if (!modelFile.existsSync() || modelFile.lengthSync() != assetSize) {
      dev.log(
        'Extracting model: assets/$assetPath ($assetSize bytes) -> ${modelFile.path}',
        name: 'ModelManager',
      );
      await modelFile.parent.create(recursive: true);
      await modelFile.writeAsBytes(
        data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes),
      );
    }

    _cachedModelPath = modelFile.path;
    dev.log('Model path: $_cachedModelPath', name: 'ModelManager');
    return _cachedModelPath!;
  }
}

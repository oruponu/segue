import 'dart:ffi';
import 'dart:io';

DynamicLibrary openEssentiaLibrary() {
  if (Platform.isAndroid) {
    return DynamicLibrary.open('libessentia_bridge.so');
  }
  throw UnsupportedError('Unsupported platform: ${Platform.operatingSystem}');
}

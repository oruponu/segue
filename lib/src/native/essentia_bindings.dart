import 'dart:ffi';
import 'package:ffi/ffi.dart';

final class EssentiaResult extends Struct {
  @Float()
  external double bpm;

  @Float()
  external double bpmConfidence;

  @Int8()
  external int keyNote; // 0-11 (C=0...B=11), -1 = unknown

  @Int8()
  external int keyScale; // 0=major, 1=minor

  @Float()
  external double keyConfidence;

  @Int32()
  external int errorCode; // 0=success, 1=cancelled, 2=decode error, 3=analysis error
}

const int styleMaxResults = 5;

final class StyleResult extends Struct {
  @Int32()
  external int count;

  @Array(styleMaxResults)
  external Array<Int32> indices;

  @Array(styleMaxResults)
  external Array<Float> confidences;

  @Int32()
  external int errorCode;
}

final class EssentiaCancelFlag extends Opaque {}

typedef EssentiaCancelFlagCreateNative = Pointer<EssentiaCancelFlag> Function();
typedef EssentiaCancelFlagCreate = Pointer<EssentiaCancelFlag> Function();

typedef EssentiaCancelFlagSetNative =
    Void Function(Pointer<EssentiaCancelFlag> flag);
typedef EssentiaCancelFlagSet = void Function(Pointer<EssentiaCancelFlag> flag);

typedef EssentiaCancelFlagDestroyNative =
    Void Function(Pointer<EssentiaCancelFlag> flag);
typedef EssentiaCancelFlagDestroy =
    void Function(Pointer<EssentiaCancelFlag> flag);

typedef EssentiaInitNative = Void Function();
typedef EssentiaInit = void Function();

typedef EssentiaShutdownNative = Void Function();
typedef EssentiaShutdown = void Function();

typedef EssentiaAnalyzeNative =
    EssentiaResult Function(
      Pointer<Utf8> path,
      Pointer<EssentiaCancelFlag> cancelFlag,
    );
typedef EssentiaAnalyze =
    EssentiaResult Function(
      Pointer<Utf8> path,
      Pointer<EssentiaCancelFlag> cancelFlag,
    );

typedef EssentiaClassifyStyleNative =
    StyleResult Function(
      Pointer<Utf8> audioPath,
      Pointer<Utf8> modelPath,
      Pointer<EssentiaCancelFlag> cancelFlag,
    );
typedef EssentiaClassifyStyle =
    StyleResult Function(
      Pointer<Utf8> audioPath,
      Pointer<Utf8> modelPath,
      Pointer<EssentiaCancelFlag> cancelFlag,
    );

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

final class SpectrumData extends Struct {
  external Pointer<Float> bands;

  @Int32()
  external int numFrames;

  @Int32()
  external int numBands;

  @Float()
  external double hopDuration;

  @Int32()
  external int errorCode;
}

typedef EssentiaComputeSpectrumNative =
    Pointer<SpectrumData> Function(
      Pointer<Utf8> path,
      Int32 numBands,
      Int32 frameSize,
      Int32 hopSize,
      Pointer<EssentiaCancelFlag> cancelFlag,
    );
typedef EssentiaComputeSpectrum =
    Pointer<SpectrumData> Function(
      Pointer<Utf8> path,
      int numBands,
      int frameSize,
      int hopSize,
      Pointer<EssentiaCancelFlag> cancelFlag,
    );

typedef EssentiaFreeSpectrumNative = Void Function(Pointer<SpectrumData> data);
typedef EssentiaFreeSpectrum = void Function(Pointer<SpectrumData> data);

final class StereoPeakData extends Struct {
  external Pointer<Float> leftPeaks;
  external Pointer<Float> rightPeaks;
  external Pointer<Uint8> clipFlags;

  @Int32()
  external int numFrames;

  @Float()
  external double hopDuration;

  @Int32()
  external int errorCode;
}

typedef EssentiaComputeStereoPeaksNative =
    Pointer<StereoPeakData> Function(
      Pointer<Utf8> path,
      Int32 frameSize,
      Int32 hopSize,
      Pointer<EssentiaCancelFlag> cancelFlag,
    );
typedef EssentiaComputeStereoPeaks =
    Pointer<StereoPeakData> Function(
      Pointer<Utf8> path,
      int frameSize,
      int hopSize,
      Pointer<EssentiaCancelFlag> cancelFlag,
    );

typedef EssentiaFreeStereoPeaksNative =
    Void Function(Pointer<StereoPeakData> data);
typedef EssentiaFreeStereoPeaks = void Function(Pointer<StereoPeakData> data);

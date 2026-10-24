package com.oruponu.segue.core

/**
 * JNI for the legacy implementation (core/legacy, the Flutter app's native API). Used only to
 * generate the baseline data (core/testdata/expected.json); remove together with the Flutter app.
 */
internal object LegacyBaseline {
    init {
        System.loadLibrary("segue_legacy_jni")
    }

    @JvmStatic external fun nativeInit()

    /** [bpm, bpmConfidence, keyNote, keyScale, keyConfidence, errorCode] */
    @JvmStatic external fun nativeAnalyze(path: String): FloatArray

    /** [errorCode, count, index0..4, confidence0..4] */
    @JvmStatic external fun nativeStyle(path: String, modelPath: String): FloatArray

    /** [errorCode, frameCount, bandCount, frameCount × bandCount dB values] */
    @JvmStatic external fun nativeSpectrum(path: String): FloatArray

    /** [errorCode, frameCount, left × frameCount, right × frameCount, clipFlags × frameCount] */
    @JvmStatic external fun nativePeaks(path: String): FloatArray
}

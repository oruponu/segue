package com.oruponu.segue.core

/** Handles are C pointers cast to Long (0 is NULL). */
internal object NativeLib {
    init {
        System.loadLibrary("segue_bridge")
    }

    @JvmStatic external fun nativeInit(): Int
    @JvmStatic external fun nativeAnalysisVersion(): Int
    @JvmStatic external fun nativeCancelCreate(): Long
    @JvmStatic external fun nativeCancelRequest(token: Long)
    @JvmStatic external fun nativeCancelDestroy(token: Long)
    @JvmStatic external fun nativeDecodeFile(path: String, token: Long, out: LongArray): Int
    @JvmStatic external fun nativeAudioFree(audio: Long)
    @JvmStatic external fun nativeVisualsWrite(audio: Long, path: String): Int
    @JvmStatic external fun nativeVisualsRead(path: String, out: LongArray): Int
    @JvmStatic external fun nativeVisualsFree(visuals: Long)
    @JvmStatic external fun nativeVisualsInfo(visuals: Long, out: IntArray)
    @JvmStatic external fun nativeVisualsCopy(visuals: Long, kind: Int, dst: ByteArray)
}

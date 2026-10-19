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
}

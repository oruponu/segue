package com.oruponu.segue.core

import kotlin.coroutines.cancellation.CancellationException

/** Status returned by the Segue Core C API. Values must match SegueStatus in segue_core.h. */
enum class SegueStatus(val code: Int) {
    OK(0),
    CANCELLED(1),
    DECODE_FAILED(2),
    ANALYSIS_FAILED(3),
    MODEL_LOAD_FAILED(4),
    IO_FAILED(5),
    INVALID_ARGUMENT(6),
    ;

    companion object {
        fun fromCode(code: Int): SegueStatus =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown SegueStatus code: $code")
    }
}

class SegueException(val status: SegueStatus) : Exception("Segue Core failed with $status")

internal fun checkStatus(code: Int) {
    when (val status = SegueStatus.fromCode(code)) {
        SegueStatus.OK -> Unit
        SegueStatus.CANCELLED -> throw CancellationException("Segue Core operation was cancelled")
        else -> throw SegueException(status)
    }
}

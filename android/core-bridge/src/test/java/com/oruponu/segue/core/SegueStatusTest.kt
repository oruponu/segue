package com.oruponu.segue.core

import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SegueStatusTest {
    @Test
    fun okDoesNotThrow() {
        checkStatus(0)
    }

    @Test
    fun cancelledBecomesCancellationException() {
        assertThrows(CancellationException::class.java) { checkStatus(1) }
    }

    @Test
    fun failuresBecomeSegueExceptionWithStatus() {
        for (code in 2..6) {
            val e = assertThrows(SegueException::class.java) { checkStatus(code) }
            assertEquals(code, e.status.code)
        }
    }

    @Test
    fun unknownCodeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { checkStatus(99) }
    }
}

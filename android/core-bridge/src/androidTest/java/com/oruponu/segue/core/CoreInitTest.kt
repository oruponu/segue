package com.oruponu.segue.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreInitTest {
    @Test
    fun initSucceedsRepeatedly() {
        SegueCore.init()
        SegueCore.init()
    }

    @Test
    fun analysisVersionIsOne() {
        assertEquals(1, SegueCore.analysisVersion)
    }

    @Test
    fun cancelTokenRequestAndCloseAreIdempotent() {
        val token = CancelToken()
        token.request()
        token.request()
        token.close()
        token.close()
        token.request()
        assertThrows(IllegalStateException::class.java) { token.requireHandle() }
    }
}

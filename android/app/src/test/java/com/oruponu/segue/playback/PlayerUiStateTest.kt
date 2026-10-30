package com.oruponu.segue.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerUiStateTest {
    @Test
    fun connectedKeyIsCurrentKeyWhileConnected() {
        assertEquals("a", PlayerUiState(currentKey = "a", connected = true).connectedKey)
    }

    @Test
    fun connectedKeyIsNullWhileDisconnected() {
        assertNull(PlayerUiState(currentKey = "a", connected = false).connectedKey)
    }
}

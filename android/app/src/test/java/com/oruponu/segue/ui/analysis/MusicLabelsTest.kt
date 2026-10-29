package com.oruponu.segue.ui.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicLabelsTest {
    @Test
    fun keyNamesFollowNotation() {
        val major = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
        val minor = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B")
        for (note in 0..11) {
            assertEquals("${major[note]} Major", keyName(note, 0))
            assertEquals("${minor[note]} Minor", keyName(note, 1))
        }
        assertEquals("Db Major", keyName(1, 0))
        assertEquals("C# Minor", keyName(1, 1))
        assertEquals("G# Minor", keyName(8, 1))
    }

    @Test
    fun unknownKeyIsNull() {
        assertNull(keyName(-1, 0))
        assertNull(keyName(12, 1))
    }
}

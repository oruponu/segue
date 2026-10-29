package com.oruponu.segue.ui.analysis

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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

    @Test
    fun duplicateStyleGetsGenre() {
        val labels = StyleLabels(listOf("A---X", "B---X", "C---Y"))
        assertEquals("X (A)", labels.displayName(0))
        assertEquals("X (B)", labels.displayName(1))
        assertEquals("Y", labels.displayName(2))
    }

    @Test
    fun bundledLabelsLoad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val labels = loadStyleLabels(context.assets)
        assertEquals(400, labels.size)
        assertEquals("Boogie Woogie", labels.displayName(0))
    }
}

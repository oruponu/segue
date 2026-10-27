package com.oruponu.segue.analysis

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VisualsCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var dir: File
    private var clock = 0L

    @Before
    fun setUp() {
        dir = tmp.newFolder("visuals")
    }

    private fun cache(maxBytes: Long = 500L * 1024 * 1024) =
        VisualsCache(dir, FakeAnalysisEngine(), maxBytes) { clock }

    private fun sha1hex(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private fun validBytes(size: Int) = "SGVZ".toByteArray() + ByteArray(size - 4)

    @Test
    fun fileNameIsSha1OfKey() {
        assertEquals(sha1hex("Music/a.mp3") + ".sgv", cache().fileFor("Music/a.mp3").name)
        assertEquals(sha1hex("Music/a.mp3") + ".sgv.tmp", cache().tempFor("Music/a.mp3").name)
    }

    @Test
    fun readTouchesLastModified() {
        val c = cache()
        c.fileFor("k").writeBytes(validBytes(10))
        c.fileFor("k").setLastModified(1000L)
        clock = 5000L
        assertNotNull(c.read("k"))
        assertEquals(5000L, c.fileFor("k").lastModified())
    }

    @Test
    fun brokenFileIsDeletedOnRead() {
        val c = cache()
        c.fileFor("k").writeBytes("XXXX".toByteArray())
        assertNull(c.read("k"))
        assertFalse(c.fileFor("k").exists())
    }

    @Test
    fun commitRenamesTempAndTrimsOldest() {
        val c = cache(maxBytes = 250)
        for ((i, k) in listOf("a", "b").withIndex()) {
            c.fileFor(k).writeBytes(validBytes(100))
            c.fileFor(k).setLastModified((i + 1).toLong() * 1000)
        }
        c.tempFor("c").writeBytes(validBytes(100))
        c.tempFor("c").setLastModified(3000L)
        assertNotNull(c.commit("c"))
        assertFalse(c.tempFor("c").exists())
        assertTrue(c.exists("c"))
        assertTrue(c.exists("b"))
        assertFalse(c.exists("a"))
    }

    @Test
    fun commitRejectsBrokenTemp() {
        val c = cache()
        c.tempFor("k").writeBytes("XXXX".toByteArray())
        assertNull(c.commit("k"))
        assertFalse(c.tempFor("k").exists())
        assertFalse(c.fileFor("k").exists())
    }

    @Test
    fun commitOverLimitReturnsVisualsAndRespectsLimit() {
        val c = cache(maxBytes = 50)
        c.tempFor("k").writeBytes(validBytes(100))
        assertNotNull(c.commit("k"))
        val total = dir.listFiles { f -> f.name.endsWith(".sgv") }!!.sumOf { it.length() }
        assertTrue(total <= 50)
    }

    @Test
    fun staleTempFilesAreRemovedOnStart() {
        val c = cache()
        c.tempFor("x").writeBytes(validBytes(10))
        c.fileFor("y").writeBytes(validBytes(10))
        c.removeStaleTemps()
        assertFalse(c.tempFor("x").exists())
        assertTrue(c.fileFor("y").exists())
    }
}

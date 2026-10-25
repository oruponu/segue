package com.oruponu.segue.analysis

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private class BytesAsset(private val data: ByteArray) : ModelAsset {
        override val length = data.size.toLong()

        override fun open(): InputStream = ByteArrayInputStream(data)
    }

    private val data = ByteArray(1000) { it.toByte() }

    @Test
    fun copiesWhenMissing() {
        val dest = File(tmp.root, "models/m.onnx")
        val result = ModelStore(BytesAsset(data), dest).prepare()
        assertEquals(dest, result)
        assertArrayEquals(data, dest.readBytes())
    }

    @Test
    fun skipsCopyWhenLengthMatches() {
        val dest = File(tmp.root, "models/m.onnx")
        dest.parentFile!!.mkdirs()
        dest.writeBytes(ByteArray(1000))
        var opens = 0
        val asset =
            object : ModelAsset {
                override val length = 1000L

                override fun open(): InputStream {
                    opens++
                    return ByteArrayInputStream(data)
                }
            }
        assertEquals(dest, ModelStore(asset, dest).prepare())
        assertEquals(0, opens)
    }

    @Test
    fun partialModelCopyIsReplaced() {
        val dest = File(tmp.root, "models/m.onnx")
        dest.parentFile!!.mkdirs()
        dest.writeBytes(ByteArray(10))
        val result = ModelStore(BytesAsset(data), dest).prepare()
        assertEquals(dest, result)
        assertArrayEquals(data, dest.readBytes())
        assertFalse(File(dest.path + ".partial").exists())
    }

    @Test
    fun failedCopyReturnsNullAndLeavesNoPartial() {
        val dest = File(tmp.root, "models/m.onnx")
        val failing =
            object : ModelAsset {
                override val length = 1000L

                override fun open(): InputStream =
                    object : InputStream() {
                        private var n = 0

                        override fun read(): Int {
                            if (n++ > 100) throw IOException("boom")
                            return 1
                        }
                    }
            }
        assertNull(ModelStore(failing, dest).prepare())
        assertFalse(dest.exists())
        assertFalse(File(dest.path + ".partial").exists())
    }
}

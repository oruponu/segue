package com.oruponu.segue.analysis

import com.oruponu.segue.core.SegueException
import com.oruponu.segue.core.Visuals
import java.io.File
import java.security.MessageDigest

class VisualsCache(
    private val dir: File,
    private val engine: AnalysisEngine,
    private val maxBytes: Long = 500L * 1024 * 1024,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun fileFor(key: String): File = File(dir, sha1hex(key) + ".sgv")

    fun tempFor(key: String): File = File(dir, sha1hex(key) + ".sgv.tmp")

    fun exists(key: String): Boolean = fileFor(key).exists()

    fun read(key: String): Visuals? {
        val file = fileFor(key)
        if (!file.exists()) return null
        return try {
            engine.readVisuals(file.path).also { file.setLastModified(now()) }
        } catch (_: SegueException) {
            file.delete()
            null
        }
    }

    fun commit(key: String): Visuals? {
        val temp = tempFor(key)
        val visuals =
            try {
                engine.readVisuals(temp.path)
            } catch (_: SegueException) {
                temp.delete()
                return null
            }
        if (!temp.renameTo(fileFor(key))) {
            temp.delete()
            return null
        }
        trim()
        return visuals
    }

    fun discardTemp(key: String) {
        tempFor(key).delete()
    }

    fun delete(keys: Collection<String>) {
        keys.forEach { fileFor(it).delete() }
    }

    fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".sgv") } ?: return
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    fun removeStaleTemps() {
        dir.listFiles { f -> f.name.endsWith(".sgv.tmp") }?.forEach { it.delete() }
    }

    private fun sha1hex(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
}

package com.oruponu.segue.analysis

import java.io.File
import java.io.InputStream

interface ModelAsset {
    val length: Long

    fun open(): InputStream
}

class ModelStore(
    private val asset: ModelAsset,
    private val dest: File,
    private val onError: (Exception) -> Unit = {},
) {
    fun prepare(): File? {
        val partial = File(dest.parentFile, "${dest.name}.partial")
        return try {
            if (dest.isFile && dest.length() == asset.length) return dest
            dest.parentFile?.mkdirs()
            asset.open().use { input -> partial.outputStream().use { input.copyTo(it) } }
            dest.delete()
            check(partial.renameTo(dest)) { "Cannot rename $partial" }
            dest
        } catch (e: Exception) {
            onError(e)
            partial.delete()
            null
        }
    }
}

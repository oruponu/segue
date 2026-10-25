package com.oruponu.segue.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun readFrom(input: InputStream): T =
        try {
            json.decodeFromString(serializer, input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Cannot read the stored JSON", e)
        }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }
}

package com.oruponu.segue.analysis

import com.oruponu.segue.core.MusicResult
import com.oruponu.segue.core.SegueException
import com.oruponu.segue.core.SegueStatus
import com.oruponu.segue.core.Visuals
import java.io.File

class FakeAnalysisEngine : AnalysisEngine {
    override val analysisVersion: Int = 1

    override fun newToken(): EngineToken =
        object : EngineToken {
            override fun request() = Unit

            override fun close() = Unit
        }

    override fun decode(path: String, token: EngineToken): EngineAudio =
        object : EngineAudio {
            override fun close() = Unit
        }

    override fun writeVisuals(audio: EngineAudio, path: String) {
        File(path).writeBytes("SGVZ".toByteArray())
    }

    override fun readVisuals(path: String): Visuals {
        val head = File(path).inputStream().use { it.readNBytes(4) }
        if (String(head, Charsets.US_ASCII) != "SGVZ") throw SegueException(SegueStatus.IO_FAILED)
        return Visuals(
            sampleRate = 44100,
            hop = 1024,
            frameCount = 0,
            bandCount = 0,
            spectrum = ByteArray(0),
            peakLeft = ByteArray(0),
            peakRight = ByteArray(0),
            clipBits = ByteArray(0),
            waveformRate = 100,
            waveformCount = 0,
            waveform = ByteArray(0),
        )
    }

    override fun loadModels(path: String): EngineModels = object : EngineModels {}

    override fun analyze(
        audio: EngineAudio,
        models: EngineModels?,
        token: EngineToken,
    ): MusicResult = MusicResult(120f, 1f, 0, 0, 1f, emptyList())
}

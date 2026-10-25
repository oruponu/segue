package com.oruponu.segue.analysis

import com.oruponu.segue.core.MusicResult
import com.oruponu.segue.core.SegueException
import com.oruponu.segue.core.SegueStatus
import com.oruponu.segue.core.Visuals
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Records its calls. Hooks run inside the call with the decoded path and may block or throw. Tokens
 * ignore request(), like a native call that returns before it checks the token.
 */
class FakeAnalysisEngine : AnalysisEngine {
    override val analysisVersion: Int = 1

    val decodes = AtomicInteger()
    val writes = AtomicInteger()
    val analyzes = AtomicInteger()
    val modelLoads = AtomicInteger()
    val analyzeModels: MutableList<EngineModels?> = Collections.synchronizedList(mutableListOf())
    val tokens: MutableList<FakeToken> = Collections.synchronizedList(mutableListOf())
    val audios: MutableList<FakeAudio> = Collections.synchronizedList(mutableListOf())

    @Volatile var onDecode: (String) -> Unit = {}
    @Volatile var onWriteVisuals: (String) -> Unit = {}
    @Volatile var onAnalyze: (String) -> Unit = {}
    @Volatile var loadModelsError: Exception? = null
    @Volatile var music = MusicResult(120f, 1f, 0, 0, 1f, emptyList())

    class FakeToken : EngineToken {
        val requested = AtomicBoolean()
        val closed = CountDownLatch(1)

        override fun request() = requested.set(true)

        override fun close() = closed.countDown()
    }

    class FakeAudio(val path: String) : EngineAudio {
        val closed = AtomicBoolean()

        override fun close() = closed.set(true)
    }

    override fun newToken(): EngineToken = FakeToken().also { tokens += it }

    override fun decode(path: String, token: EngineToken): EngineAudio {
        decodes.incrementAndGet()
        onDecode(path)
        return FakeAudio(path).also { audios += it }
    }

    override fun writeVisuals(audio: EngineAudio, path: String) {
        writes.incrementAndGet()
        (audio as? FakeAudio)?.let { onWriteVisuals(it.path) }
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

    override fun loadModels(path: String): EngineModels {
        modelLoads.incrementAndGet()
        loadModelsError?.let { throw it }
        return object : EngineModels {}
    }

    override fun analyze(
        audio: EngineAudio,
        models: EngineModels?,
        token: EngineToken,
    ): MusicResult {
        analyzes.incrementAndGet()
        analyzeModels += models
        (audio as? FakeAudio)?.let { onAnalyze(it.path) }
        return music
    }
}

package com.oruponu.segue.analysis

import com.oruponu.segue.core.CancelToken
import com.oruponu.segue.core.MusicResult
import com.oruponu.segue.core.NativeAudio
import com.oruponu.segue.core.NativeModels
import com.oruponu.segue.core.SegueCore
import com.oruponu.segue.core.Visuals
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SegueCoreEngine @Inject constructor() : AnalysisEngine {
    private val initialized by lazy { SegueCore.init() }

    override val analysisVersion: Int
        get() = SegueCore.analysisVersion

    override fun newToken(): EngineToken {
        ensureInit()
        return CoreToken(CancelToken())
    }

    override fun decode(path: String, token: EngineToken): EngineAudio {
        ensureInit()
        return CoreAudio(SegueCore.decode(path, (token as CoreToken).token))
    }

    override fun writeVisuals(audio: EngineAudio, path: String) {
        ensureInit()
        SegueCore.writeVisuals((audio as CoreAudio).audio, path)
    }

    override fun readVisuals(path: String): Visuals {
        ensureInit()
        return SegueCore.readVisuals(path)
    }

    override fun loadModels(path: String): EngineModels {
        ensureInit()
        return CoreModels(SegueCore.loadModels(path))
    }

    override fun analyze(
        audio: EngineAudio,
        models: EngineModels?,
        token: EngineToken,
    ): MusicResult {
        ensureInit()
        return SegueCore.analyze(
            (audio as CoreAudio).audio,
            (models as CoreModels?)?.models,
            (token as CoreToken).token,
        )
    }

    private fun ensureInit() = initialized

    private class CoreToken(val token: CancelToken) : EngineToken {
        override fun request() = token.request()

        override fun close() = token.close()
    }

    private class CoreAudio(val audio: NativeAudio) : EngineAudio {
        override fun close() = audio.close()
    }

    private class CoreModels(val models: NativeModels) : EngineModels
}

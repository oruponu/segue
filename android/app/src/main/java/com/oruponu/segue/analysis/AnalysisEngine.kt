package com.oruponu.segue.analysis

import com.oruponu.segue.core.MusicResult
import com.oruponu.segue.core.Visuals

interface EngineToken : AutoCloseable {
    fun request()
}

interface EngineAudio : AutoCloseable

interface EngineModels

interface AnalysisEngine {
    val analysisVersion: Int

    fun newToken(): EngineToken

    fun decode(path: String, token: EngineToken): EngineAudio

    fun writeVisuals(audio: EngineAudio, path: String)

    fun readVisuals(path: String): Visuals

    fun loadModels(path: String): EngineModels

    fun analyze(audio: EngineAudio, models: EngineModels?, token: EngineToken): MusicResult
}

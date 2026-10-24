package com.oruponu.segue.core

object SegueCore {
    /**
     * Initializes Essentia. Safe to call any number of times (each function also initializes if
     * needed).
     */
    fun init() = checkStatus(NativeLib.nativeInit())

    /** Version of the analysis and visuals output. Used to invalidate the DB and caches. */
    val analysisVersion: Int
        get() = NativeLib.nativeAnalysisVersion()

    /** Decodes a file and computes its visuals. path is a path such as /proc/self/fd/N. */
    fun decode(path: String, token: CancelToken): NativeAudio {
        val out = LongArray(1)
        checkStatus(NativeLib.nativeDecodeFile(path, token.requireHandle(), out))
        return NativeAudio(out[0])
    }

    fun writeVisuals(audio: NativeAudio, cachePath: String) =
        checkStatus(NativeLib.nativeVisualsWrite(audio.requireHandle(), cachePath))

    fun readVisuals(cachePath: String): Visuals {
        val out = LongArray(1)
        checkStatus(NativeLib.nativeVisualsRead(cachePath, out))
        val handle = out[0]
        try {
            val info = IntArray(6)
            NativeLib.nativeVisualsInfo(handle, info)
            val frameCount = info[2]
            val bandCount = info[3]
            val waveformCount = info[5]
            fun copy(kind: Int, size: Int) =
                ByteArray(size).also { NativeLib.nativeVisualsCopy(handle, kind, it) }
            return Visuals(
                sampleRate = info[0],
                hop = info[1],
                frameCount = frameCount,
                bandCount = bandCount,
                spectrum = copy(KIND_SPECTRUM, frameCount * bandCount),
                peakLeft = copy(KIND_PEAK_LEFT, frameCount),
                peakRight = copy(KIND_PEAK_RIGHT, frameCount),
                clipBits = copy(KIND_CLIP_BITS, (frameCount + 7) / 8),
                waveformRate = info[4],
                waveformCount = waveformCount,
                waveform = copy(KIND_WAVEFORM, waveformCount * 2),
            )
        } finally {
            NativeLib.nativeVisualsFree(handle)
        }
    }

    fun loadModels(effnetPath: String): NativeModels {
        val out = LongArray(1)
        checkStatus(NativeLib.nativeModelsLoad(effnetPath, out))
        return NativeModels(out[0])
    }

    /** Analyzes the BPM, key and styles. Skips style classification when models is null. */
    fun analyze(audio: NativeAudio, models: NativeModels?, token: CancelToken): MusicResult {
        val floats = FloatArray(3 + STYLE_MAX)
        val ints = IntArray(3 + STYLE_MAX)
        checkStatus(
            NativeLib.nativeAnalyze(
                audio.requireHandle(),
                models?.requireHandle() ?: 0L,
                token.requireHandle(),
                floats,
                ints,
            ),
        )
        return MusicResult(
            bpm = floats[0],
            bpmConfidence = floats[1],
            keyNote = ints[0],
            keyScale = ints[1],
            keyConfidence = floats[2],
            styles =
                List(ints[2]) {
                    StyleScore(labelIndex = ints[3 + it], confidence = floats[3 + it])
                },
        )
    }

    private const val STYLE_MAX = 5

    private const val KIND_SPECTRUM = 0
    private const val KIND_PEAK_LEFT = 1
    private const val KIND_PEAK_RIGHT = 2
    private const val KIND_CLIP_BITS = 3
    private const val KIND_WAVEFORM = 4
}

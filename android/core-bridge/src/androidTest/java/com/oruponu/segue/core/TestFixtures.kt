package com.oruponu.segue.core

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

/** Exposes the androidTest assets (core/testdata and models) as files. */
object TestFixtures {
    data class Fixture(val name: String, val sourceSampleRate: Int)

    /** Fixtures checked against the legacy implementation. expected.json is generated from this list. */
    val MAIN_FIXTURES = listOf(
        Fixture("click120_aminor_44k_stereo.wav", 44100),
        Fixture("click120_aminor_44k_stereo.mp3", 44100),
        Fixture("click120_aminor_44k_stereo.ogg", 44100),
        Fixture("click120_aminor_44k_stereo.opus", 48000),
        Fixture("chords96_cmajor_48k_stereo.flac", 48000),
        Fixture("click100_dminor_44k_mono.m4a", 44100),
        Fixture("clip_lr_44k_stereo.wav", 44100),
    )

    private val context get() = InstrumentationRegistry.getInstrumentation().context

    fun workDir(): File = File(context.cacheDir, "segue-test").apply { mkdirs() }

    fun file(name: String): File {
        val dir = File(context.cacheDir, "fixtures").apply { mkdirs() }
        val out = File(dir, name)
        if (!out.exists()) {
            context.assets.open(name).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        return out
    }

    fun model(): File = file("discogs-effnet-bsdynamic-1.onnx")

    fun expected(): JSONObject =
        JSONObject(context.assets.open("expected.json").bufferedReader().use { it.readText() })
}

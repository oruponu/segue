package com.oruponu.segue.core

/**
 * One style classification result. labelIndex is an index into the 400 Discogs-EffNet style labels.
 */
data class StyleScore(val labelIndex: Int, val confidence: Float)

/**
 * Result of music analysis. keyNote is 0-11 (C=0), or -1 if unknown. keyScale is 0=major / 1=minor.
 */
data class MusicResult(
    val bpm: Float,
    val bpmConfidence: Float,
    val keyNote: Int,
    val keyScale: Int,
    val keyConfidence: Float,
    val styles: List<StyleScore>,
)

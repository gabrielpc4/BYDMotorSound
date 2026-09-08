package com.gabrielpc.enginesoundsimulator.audio

/** Snapshot shown in Settings to explain where bank discovery succeeded or failed. */
data class FmodBankDiagnostics(
    val generatedAtEpochMillis: Long,
    val recognizedCarCount: Int,
    val validPackCount: Int,
    val issueCount: Int,
    val logLines: List<String>,
)

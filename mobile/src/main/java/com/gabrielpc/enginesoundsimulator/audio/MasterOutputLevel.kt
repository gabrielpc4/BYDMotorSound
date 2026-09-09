package com.gabrielpc.enginesoundsimulator.audio

import java.util.Locale
import kotlin.math.log10

/** Converts the post-limiter FMOD output meter peak into a readable dBFS dashboard label. */
object MasterOutputLevel {
    const val SILENCE_DBFS = -120.0

    fun linearToDbFs(linear: Float): Double {
        if (linear <= 1e-9f) {
            return SILENCE_DBFS
        }

        return 20.0 * log10(linear.toDouble())
    }

    fun formatDbFs(dbFs: Double): String {
        if (dbFs <= SILENCE_DBFS + 1.0) {
            return "-∞ dB"
        }

        if (dbFs >= 0.0) {
            return String.format(Locale.US, "%+.0f dB", dbFs)
        }

        return String.format(Locale.US, "%.0f dB", dbFs)
    }

    fun formatLinear(linear: Float): String {
        return formatDbFs(linearToDbFs(linear))
    }
}

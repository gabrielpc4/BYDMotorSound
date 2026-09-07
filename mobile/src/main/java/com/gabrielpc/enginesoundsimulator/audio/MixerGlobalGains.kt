package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** App-wide mixer multipliers applied on top of each car's dashboard mix settings. */
data class MixerGlobalGains(
    val engineInterior: Float = 1.0f,
    val engineExterior: Float = 1.0f,
    val effectsHost: Float = 1.0f,
    val transmission: Float = 1.0f,
    val gearShift: Float = 1.0f,
    val turbo: Float = 1.0f,
    val backfire: Float = 1.0f,
    val limiter: Float = 1.0f,
) {
    fun normalized(): MixerGlobalGains = copy(
        engineInterior = snap(engineInterior),
        engineExterior = snap(engineExterior),
        effectsHost = snap(effectsHost),
        transmission = snap(transmission),
        gearShift = snap(gearShift),
        turbo = snap(turbo),
        backfire = snap(backfire),
        limiter = snap(limiter),
    )

    companion object {
        val STOPS = floatArrayOf(0f, 0.12f, 0.25f, 0.5f, 1f, 2f, 3f, 5f)

        const val MIN = 0f
        const val MAX = 5f

        fun snap(value: Float): Float {
            return STOPS.minByOrNull { abs(it - value) } ?: 1f
        }

        fun stopIndex(value: Float): Int {
            return STOPS.indices.minByOrNull { abs(STOPS[it] - value) } ?: defaultStopIndex()
        }

        fun stopValue(index: Int): Float {
            return STOPS[index.coerceIn(0, STOPS.lastIndex)]
        }

        fun formatMultiplier(value: Float): String {
            if (abs(value) < 0.001f) {
                return "0x"
            }

            val snapped = snap(value)
            if (abs(value - snapped) < 0.001f) {
                return when (snapped) {
                    0.12f -> "0.12x"
                    0.25f -> "0.25x"
                    else -> String.format(Locale.US, "%.1fx", snapped)
                }
            }

            return String.format(Locale.US, "%.1fx", value)
        }

        private fun defaultStopIndex(): Int {
            return STOPS.indexOfFirst { it == 1f }.coerceAtLeast(0)
        }
    }
}

internal fun AudioMixGains.effectiveWith(
    mixerGlobal: MixerGlobalGains,
    mixerSpecific: MixerCarSpecificGains,
): AudioMixGains {
    val overall = mixerSpecific.overall
    return copy(
        effectsHost = effectsHost * mixerGlobal.effectsHost * mixerSpecific.effectsHost * overall,
        transmission = transmission * mixerGlobal.transmission * mixerSpecific.transmission * overall,
        gearShift = gearShift * mixerGlobal.gearShift * mixerSpecific.gearShift * overall,
        turbo = turbo * mixerGlobal.turbo * mixerSpecific.turbo * overall,
        backfire = backfire * mixerGlobal.backfire * mixerSpecific.backfire * overall,
        limiter = limiter * mixerGlobal.limiter * mixerSpecific.limiter * overall,
    )
}

internal class MixerGlobalGainRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.MIXER_GLOBAL_GAINS,
        Context.MODE_PRIVATE,
    )

    fun load(): MixerGlobalGains {
        val legacyEngineHost = preferences.getFloat("engine_host", 1.0f)
        return MixerGlobalGains(
            engineInterior = read("engine_interior", legacyEngineHost),
            engineExterior = read("engine_exterior", legacyEngineHost),
            effectsHost = read("effects_host"),
            transmission = read("transmission"),
            gearShift = read("gear_shift"),
            turbo = read("turbo"),
            backfire = read("backfire"),
            limiter = read("limiter"),
        ).normalized()
    }

    fun save(gains: MixerGlobalGains) {
        val normalized = gains.normalized()
        preferences.edit()
            .putFloat("engine_interior", normalized.engineInterior)
            .putFloat("engine_exterior", normalized.engineExterior)
            .putFloat("effects_host", normalized.effectsHost)
            .putFloat("transmission", normalized.transmission)
            .putFloat("gear_shift", normalized.gearShift)
            .putFloat("turbo", normalized.turbo)
            .putFloat("backfire", normalized.backfire)
            .putFloat("limiter", normalized.limiter)
            .commit()
    }

    fun resetAll() {
        preferences.edit().clear().commit()
    }

    private fun read(key: String, default: Float = 1.0f): Float = preferences.getFloat(key, default)
}

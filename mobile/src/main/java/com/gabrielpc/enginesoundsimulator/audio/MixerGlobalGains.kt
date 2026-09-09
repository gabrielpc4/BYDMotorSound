package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** App-wide mixer multipliers applied on top of each car's per-car mixer profile. */
data class MixerGlobalGains(
    val engineInterior: Float = 1.0f,
    val engineExterior: Float = 1.0f,
    val effectsHost: Float = 1.0f,
    val transmission: Float = 1.0f,
    val gearShift: Float = 1.0f,
    val turbo: Float = 1.0f,
    val backfire: Float = 1.0f,
    val limiter: Float = 1.0f,
    val supercharger: Float = 1.0f,
    val backfireOverrideGain: Float = 1.0f,
    val shiftOverrideGain: Float = 1.0f,
) {
    fun normalized(): MixerGlobalGains = copy(
        engineInterior = snapToStep(engineInterior),
        engineExterior = snapToStep(engineExterior),
        effectsHost = snapToStep(effectsHost),
        transmission = snapToStep(transmission),
        gearShift = snapToStep(gearShift),
        turbo = snapToStep(turbo),
        backfire = snapToStep(backfire),
        limiter = snapToStep(limiter),
        supercharger = snapToStep(supercharger),
        backfireOverrideGain = snapToStep(backfireOverrideGain),
        shiftOverrideGain = snapToStep(shiftOverrideGain),
    )

    companion object {
        const val MIN = 0f
        const val MAX = 5f
        const val STEP = 0.1f

        fun clamp(value: Float): Float {
            return value.coerceIn(MIN, MAX)
        }

        fun snapToStep(value: Float): Float {
            val stepped = (value / STEP).roundToInt() * STEP
            return clamp(stepped)
        }

        fun sliderSteps(): Int {
            return ((MAX - MIN) / STEP).roundToInt() - 1
        }

        fun formatMultiplier(value: Float): String {
            val clamped = snapToStep(value)

            if (abs(clamped) < 0.001f) {
                return "0x"
            }

            val rounded = (clamped * 100f).roundToInt() / 100f

            if (abs(rounded - rounded.roundToInt()) < 0.001f) {
                return String.format(Locale.US, "%.0fx", rounded)
            }

            return String.format(Locale.US, "%.2fx", rounded)
        }
    }
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
            supercharger = read("supercharger"),
            backfireOverrideGain = read("backfire_override"),
            shiftOverrideGain = read("shift_override"),
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
            .putFloat("supercharger", normalized.supercharger)
            .putFloat("backfire_override", normalized.backfireOverrideGain)
            .putFloat("shift_override", normalized.shiftOverrideGain)
            .commit()
    }

    fun resetAll() {
        preferences.edit().clear().commit()
    }

    private fun read(key: String, default: Float = 1.0f): Float = preferences.getFloat(key, default)
}

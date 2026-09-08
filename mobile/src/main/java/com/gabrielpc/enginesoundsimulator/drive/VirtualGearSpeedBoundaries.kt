package com.gabrielpc.enginesoundsimulator.drive

import com.gabrielpc.enginesoundsimulator.simulation.VirtualGearProfile
import kotlin.math.roundToInt

/** Editable 0..190 km/h band edges for virtual gear presets (6, 10, 15). */
object VirtualGearSpeedBoundaries {
    const val TOP_SPEED_KMH = 190
    const val SNAP_KMH = 10
    const val MIN_SEGMENT_KMH = 10
    val PRESETS: List<Int> = listOf(6, 10, 15)

    fun snapSpeedKmh(speedKmh: Double): Int {
        val snapped = (speedKmh / SNAP_KMH).roundToInt() * SNAP_KMH
        return snapped.coerceIn(0, TOP_SPEED_KMH)
    }

    fun defaultBoundariesKmh(gearCount: Int): List<Int> {
        require(gearCount in PRESETS) {
            "Custom speed bands are only defined for presets $PRESETS"
        }

        return List(gearCount + 1) { index ->
            when (index) {
                0 -> 0
                gearCount -> TOP_SPEED_KMH
                else -> snapSpeedKmh(TOP_SPEED_KMH * index.toDouble() / gearCount.toDouble())
            }
        }
    }

    fun normalizeBoundaries(boundaries: List<Int>, gearCount: Int): List<Int> {
        require(gearCount in PRESETS) {
            "Custom speed bands are only defined for presets $PRESETS"
        }

        val expectedSize = gearCount + 1
        if (boundaries.size != expectedSize) {
            return defaultBoundariesKmh(gearCount)
        }

        val normalized = IntArray(expectedSize)
        normalized[0] = 0
        normalized[gearCount] = TOP_SPEED_KMH

        for (index in 1 until gearCount) {
            val minimum = normalized[index - 1] + MIN_SEGMENT_KMH
            val maximum = TOP_SPEED_KMH - (gearCount - index) * MIN_SEGMENT_KMH
            normalized[index] = snapSpeedKmh(boundaries[index].toDouble())
                .coerceIn(minimum, maximum)
        }

        for (index in 1 until gearCount) {
            if (normalized[index] <= normalized[index - 1]) {
                return defaultBoundariesKmh(gearCount)
            }
        }

        return normalized.toList()
    }

    fun withBoundaryAtIndex(
        boundaries: List<Int>,
        gearCount: Int,
        boundaryIndex: Int,
        speedKmh: Int,
    ): List<Int> {
        if (boundaryIndex !in 1 until gearCount) {
            return normalizeBoundaries(boundaries, gearCount)
        }

        val updated = if (boundaries.size == gearCount + 1) {
            boundaries.toMutableList()
        } else {
            VirtualGearSpeedBoundaries.defaultBoundariesKmh(gearCount).toMutableList()
        }

        val minimum = updated[boundaryIndex - 1] + MIN_SEGMENT_KMH
        val maximum = updated[boundaryIndex + 1] - MIN_SEGMENT_KMH
        updated[boundaryIndex] = snapSpeedKmh(speedKmh.toDouble()).coerceIn(minimum, maximum)
        return normalizeBoundaries(updated, gearCount)
    }

    fun coerceVirtualPreset(count: Int): Int {
        if (count in PRESETS) {
            return count
        }

        return VirtualGearProfile.DEFAULT_VIRTUAL_GEARS
    }
}

data class VirtualGearSpeedBoundariesSettings(
    val boundariesByPreset: Map<Int, List<Int>> = VirtualGearSpeedBoundaries.PRESETS.associateWith {
        VirtualGearSpeedBoundaries.defaultBoundariesKmh(it)
    },
) {
    fun boundariesFor(preset: Int): List<Int> {
        val gearCount = VirtualGearSpeedBoundaries.coerceVirtualPreset(preset)
        val stored = boundariesByPreset[gearCount]
            ?: VirtualGearSpeedBoundaries.defaultBoundariesKmh(gearCount)
        return VirtualGearSpeedBoundaries.normalizeBoundaries(stored, gearCount)
    }

    fun withPresetBoundaries(preset: Int, boundaries: List<Int>): VirtualGearSpeedBoundariesSettings {
        val gearCount = VirtualGearSpeedBoundaries.coerceVirtualPreset(preset)
        val normalized = VirtualGearSpeedBoundaries.normalizeBoundaries(boundaries, gearCount)
        return copy(
            boundariesByPreset = boundariesByPreset + (gearCount to normalized),
        )
    }

    fun withRestoredPreset(preset: Int): VirtualGearSpeedBoundariesSettings {
        val gearCount = VirtualGearSpeedBoundaries.coerceVirtualPreset(preset)
        return withPresetBoundaries(
            preset = gearCount,
            boundaries = VirtualGearSpeedBoundaries.defaultBoundariesKmh(gearCount),
        )
    }

    fun normalized(): VirtualGearSpeedBoundariesSettings {
        val normalizedPresets = VirtualGearSpeedBoundaries.PRESETS.associateWith { preset ->
            boundariesFor(preset)
        }
        return copy(boundariesByPreset = normalizedPresets)
    }
}

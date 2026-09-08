package com.gabrielpc.enginesoundsimulator.simulation

import com.gabrielpc.enginesoundsimulator.drive.VirtualGearSpeedBoundaries
import kotlin.math.abs
import kotlin.math.ln

/**
 * Virtual forward-gear profile layered over bank-authored drivetrain data.
 *
 * Extends the bank's ratio list to [virtualForwardGearCount] and splits 0..190 km/h into
 * equal-width physical speed bands. With the default ten forward gears, 60 km/h lands in
 * 4th gear a little above its lower boundary without widening that band.
 */
internal data class VirtualGearProfile(
    val virtualForwardGearCount: Int,
    val synthesizedRatios: List<Double>,
    val physicalBoundarySpeedsKmh: List<Double>,
    val finalDrive: Double,
    val wheelRadiusMeters: Double,
    val usesAuthoredRatiosOnly: Boolean = false,
) {
    init {
        require(virtualForwardGearCount in 1..MAX_VIRTUAL_GEARS) {
            "Virtual gear count must be between 1 and $MAX_VIRTUAL_GEARS"
        }
        if (!usesAuthoredRatiosOnly) {
            require(virtualForwardGearCount in VirtualGearSpeedBoundaries.PRESETS) {
                "Virtual gear count must be one of ${VirtualGearSpeedBoundaries.PRESETS}"
            }
        }
        require(synthesizedRatios.size == virtualForwardGearCount) {
            "Expected $virtualForwardGearCount synthesized ratios, got ${synthesizedRatios.size}"
        }
        require(physicalBoundarySpeedsKmh.size == virtualForwardGearCount + 1) {
            "Expected ${virtualForwardGearCount + 1} physical boundaries"
        }
    }

    fun ratioForVirtualGear(gear: Int): Double {
        if (gear == -1) {
            error("Reverse ratio is not part of the virtual forward profile")
        }
        if (gear == 0) {
            return 0.0
        }
        if (gear !in 1..virtualForwardGearCount) {
            error("Virtual gear $gear is outside 1..$virtualForwardGearCount")
        }
        return synthesizedRatios[gear - 1]
    }

    /** Forward gear implied by documented road speed and the physical speed bands. */
    fun gearForRoadSpeedKmh(roadSpeedKmh: Double): Int {
        val speed = roadSpeedKmh.coerceAtLeast(0.0)
        if (speed <= 0.0) {
            return 1
        }

        val boundaries = physicalBoundarySpeedsKmh
        if (speed >= boundaries.last()) {
            return virtualForwardGearCount
        }

        val lowerBoundaryIndex = boundaries.indexOfLast { it <= speed }
            .coerceIn(0, boundaries.lastIndex - 1)
        return (lowerBoundaryIndex + 1).coerceIn(1, virtualForwardGearCount)
    }

    companion object {
        const val MIN_VIRTUAL_GEARS = 6
        const val MAX_VIRTUAL_GEARS = 15
        const val DEFAULT_VIRTUAL_GEARS = 10
        private const val TOP_SPEED_KMH = 190.0

        fun from(
            physics: AssettoPhysics,
            virtualGearCount: Int,
            physicalBoundarySpeedsKmh: List<Double>? = null,
        ): VirtualGearProfile {
            val count = VirtualGearSpeedBoundaries.coerceVirtualPreset(virtualGearCount)
            val wheelRadius = drivenWheelRadius(physics)
            val ratios = synthesizeForwardRatios(
                authoredRatios = physics.drivetrain.forwardRatios,
                virtualCount = count,
            )
            val boundaries = physicalBoundarySpeedsKmh
                ?.map { it.toDouble() }
                ?: defaultPhysicalBoundarySpeedsKmh(count)
            return VirtualGearProfile(
                virtualForwardGearCount = count,
                synthesizedRatios = ratios,
                physicalBoundarySpeedsKmh = boundaries,
                finalDrive = physics.drivetrain.finalDrive,
                wheelRadiusMeters = wheelRadius,
                usesAuthoredRatiosOnly = false,
            )
        }

        /** Bank-authored forward ratios and count without virtual synthesis or custom shift times. */
        fun fromOriginal(physics: AssettoPhysics): VirtualGearProfile {
            val ratios = physics.drivetrain.forwardRatios
            require(ratios.isNotEmpty()) { "Bank must provide at least one forward ratio" }
            val count = ratios.size
            val wheelRadius = drivenWheelRadius(physics)
            return VirtualGearProfile(
                virtualForwardGearCount = count,
                synthesizedRatios = ratios.toList(),
                physicalBoundarySpeedsKmh = equalPhysicalBoundarySpeedsKmh(count),
                finalDrive = physics.drivetrain.finalDrive,
                wheelRadiusMeters = wheelRadius,
                usesAuthoredRatiosOnly = true,
            )
        }

        internal fun synthesizeForwardRatios(
            authoredRatios: List<Double>,
            virtualCount: Int,
        ): List<Double> {
            require(authoredRatios.isNotEmpty()) { "Bank must provide at least one forward ratio" }
            if (virtualCount <= authoredRatios.size) {
                return authoredRatios.take(virtualCount)
            }

            val result = authoredRatios.toMutableList()
            val lastIndex = authoredRatios.lastIndex
            val logStep = if (lastIndex >= 1 && authoredRatios[lastIndex - 1] > 0.0) {
                ln(abs(authoredRatios[lastIndex]) / abs(authoredRatios[lastIndex - 1]))
            } else {
                ln(0.82)
            }
            var currentRatio = abs(authoredRatios[lastIndex])
            while (result.size < virtualCount) {
                currentRatio *= kotlin.math.exp(logStep)
                result.add(currentRatio)
            }
            return result
        }

        internal fun physicalBoundarySpeedsKmh(virtualGearCount: Int): List<Double> {
            return defaultPhysicalBoundarySpeedsKmh(virtualGearCount)
        }

        internal fun defaultPhysicalBoundarySpeedsKmh(virtualGearCount: Int): List<Double> {
            return VirtualGearSpeedBoundaries.defaultBoundariesKmh(virtualGearCount)
                .map { it.toDouble() }
        }

        private fun equalPhysicalBoundarySpeedsKmh(virtualGearCount: Int): List<Double> {
            return List(virtualGearCount + 1) { index ->
                TOP_SPEED_KMH * index / virtualGearCount
            }
        }
    }
}

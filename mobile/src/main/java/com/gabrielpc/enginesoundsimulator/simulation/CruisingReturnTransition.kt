package com.gabrielpc.enginesoundsimulator.simulation

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Smooth racing → cruising handoff: drop RPM into the cruising band and upshift as each
 * landing is reached.
 *
 * The previous version used a one-second lerp toward a live mapped target. That finished on a
 * clock instead of on arrival, chased a rising target while the car was still accelerating, and
 * snapped whenever the throttle was pumped. This state machine glides at a fixed rate until the
 * needle is actually on the coupled cruising RPM in the right gear.
 */
internal class CruisingReturnTransition {
    var active: Boolean = false
        private set

    var targetGear: Int = 1
        private set

    /** Last values seen by [step], including the finishing frame after [active] clears. */
    var lastDebug: CruisingReturnDebug = CruisingReturnDebug()
        private set

    /** The RPM the needle is allowed to chase this frame. Falls immediately, rises only at glide rate. */
    private var chaseRpm: Double = 0.0

    private var glideRpmPerSecond: Double = CruisingReturn.MIN_GLIDE_RPM_PER_SECOND

    fun clear() {
        active = false
        targetGear = 1
        chaseRpm = 0.0
        glideRpmPerSecond = CruisingReturn.MIN_GLIDE_RPM_PER_SECOND
    }

    /**
     * Arm a return. A second call while already active is ignored so throttle pumps cannot
     * restart the glide from a new snapshot and jump the needle.
     */
    fun begin(
        currentRpm: Double,
        currentGear: Int,
        computedTargetGear: Int,
        initialLiveTargetRpm: Double,
    ) {
        if (active) {
            return
        }

        targetGear = computedTargetGear.coerceAtLeast(currentGear)
        chaseRpm = initialLiveTargetRpm
        val gap = abs(currentRpm - initialLiveTargetRpm)
        glideRpmPerSecond = max(
            gap / CruisingReturn.REFERENCE_SECONDS,
            CruisingReturn.MIN_GLIDE_RPM_PER_SECOND,
        )
        active = true
    }

    fun step(
        dt: Double,
        currentRpm: Double,
        currentGear: Int,
        shifting: Boolean,
        liveTargetRpm: Double,
        computedTargetGear: Int,
        nextGearCoupledRpm: Double?,
    ): CruisingReturnStep {
        if (!active) {
            lastDebug = CruisingReturnDebug(
                active = false,
                targetGear = targetGear,
                chaseRpm = chaseRpm,
                liveTargetRpm = liveTargetRpm,
                computedTargetGear = computedTargetGear,
                glideRpmPerSecond = glideRpmPerSecond,
                nextGearCoupledRpm = nextGearCoupledRpm,
                requestUpshift = false,
                finished = false,
            )
            return CruisingReturnStep(
                rpm = currentRpm,
                requestUpshift = false,
                finished = false,
            )
        }

        val seconds = dt.coerceAtLeast(0.0)
        val maxStep = glideRpmPerSecond * seconds
        targetGear = computedTargetGear.coerceAtLeast(currentGear)

        if (liveTargetRpm < chaseRpm) {
            chaseRpm = liveTargetRpm
        } else {
            chaseRpm = min(liveTargetRpm, chaseRpm + maxStep)
        }

        val error = chaseRpm - currentRpm
        val rpm = if (abs(error) <= CruisingReturn.RPM_TOLERANCE) {
            chaseRpm
        } else {
            currentRpm + sign(error) * min(abs(error), maxStep)
        }

        val onLiveTarget = abs(rpm - liveTargetRpm) <= CruisingReturn.RPM_TOLERANCE
        val reachedNextGear = nextGearCoupledRpm != null &&
            rpm <= nextGearCoupledRpm + CruisingReturn.RPM_TOLERANCE
        val requestUpshift = !shifting &&
            currentGear < targetGear &&
            (reachedNextGear || onLiveTarget)
        val finished = !shifting && currentGear >= targetGear && onLiveTarget

        lastDebug = CruisingReturnDebug(
            active = true,
            targetGear = targetGear,
            chaseRpm = chaseRpm,
            liveTargetRpm = liveTargetRpm,
            computedTargetGear = computedTargetGear,
            glideRpmPerSecond = glideRpmPerSecond,
            nextGearCoupledRpm = nextGearCoupledRpm,
            requestUpshift = requestUpshift,
            finished = finished,
        )

        if (finished) {
            clear()
        }

        return CruisingReturnStep(
            rpm = rpm,
            requestUpshift = requestUpshift,
            finished = finished,
        )
    }
}

internal data class CruisingReturnStep(
    val rpm: Double,
    val requestUpshift: Boolean,
    val finished: Boolean,
)

internal data class CruisingReturnDebug(
    val active: Boolean = false,
    val targetGear: Int = 0,
    val chaseRpm: Double = 0.0,
    val liveTargetRpm: Double = 0.0,
    val computedTargetGear: Int = 0,
    val glideRpmPerSecond: Double = 0.0,
    val nextGearCoupledRpm: Double? = null,
    val requestUpshift: Boolean = false,
    val finished: Boolean = false,
)

internal object CruisingReturn {
    /** Comfortable duration for a typical gap; larger gaps and moving targets take longer. */
    const val REFERENCE_SECONDS = 1.0

    const val RPM_TOLERANCE = 30.0

    /** Floor so a tiny initial gap cannot crawl, and a restart cannot stall. */
    const val MIN_GLIDE_RPM_PER_SECOND = 2_500.0

    /**
     * Tallest gear still at or below [currentGear] is never chosen. Walks up while the coupled
     * RPM of the candidate sits above the cruising upshift line.
     */
    fun targetGear(
        currentGear: Int,
        topGear: Int,
        cruisingUpshiftRpm: Double,
        coupledRpmForGear: (Int) -> Double,
    ): Int {
        val top = topGear.coerceAtLeast(1)
        var candidate = currentGear.coerceIn(1, top)

        while (candidate < top) {
            if (coupledRpmForGear(candidate) <= cruisingUpshiftRpm) {
                break
            }

            candidate++
        }

        return candidate
    }
}

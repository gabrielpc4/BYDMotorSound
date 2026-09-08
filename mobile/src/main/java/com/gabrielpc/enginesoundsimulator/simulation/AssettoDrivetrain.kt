package com.gabrielpc.enginesoundsimulator.simulation

import com.gabrielpc.enginesoundsimulator.drive.BackfireSettings
import com.gabrielpc.enginesoundsimulator.drive.CruisingShiftOffsetByTachMaxRpm
import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal class AssettoDrivetrainFrame(
    var rpm: Double,
    var speedMetersPerSecond: Double,
    var gear: Int,
    var drivetrainSpeedRadiansPerSecond: Double,
    var driverThrottle: Double,
    var effectiveThrottle: Double,
    var brake: Double,
    var clutch: Double,
    var boost: Double,
    var bov: Double,
    var bovDecaySeconds: Double,
    var limiterPulse: Boolean,
    var backfireTriggered: Boolean,
    /** Shared Alfa sample selected for this one-shot, or -1 when no backfire fired. */
    var backfireSampleIndex: Int = -1,
    var shiftStarted: Boolean,
    var shiftRejected: Boolean,
    var shifting: Boolean,
    var shiftDirection: Int,
    var shiftProgress: Double,
    var authoredShiftDurationSeconds: Double = 0.0,
    var effectiveShiftDurationSeconds: Double = 0.0,
    var authoredClutchDurationSeconds: Double = 0.0,
    var effectiveClutchDurationSeconds: Double = 0.0,
    var tractionLimitActive: Boolean,
    var tractionLimitPulse: Boolean,
    /** Manual mode held at redline long enough to request a return to automatic shifting. */
    var requestAutomaticShiftMode: Boolean = false,
    var automaticTransmissionMode: AutomaticTransmissionMode = AutomaticTransmissionMode.CRUISING,
    var racingReturnArmed: Boolean = false,
    var launchSixGearOverrideActive: Boolean = false,
    var launchReturnArmed: Boolean = false,
    var cruisingReturnActive: Boolean = false,
    var cruisingReturnTargetGear: Int = 0,
    var cruisingReturnChaseRpm: Double = 0.0,
    var cruisingReturnLiveTargetRpm: Double = 0.0,
    var cruisingReturnComputedTargetGear: Int = 0,
    var cruisingReturnGlideRpmPerSecond: Double = 0.0,
    var cruisingReturnNextGearCoupledRpm: Double = 0.0,
    var cruisingReturnRequestUpshift: Boolean = false,
    var cruisingReturnFinishedThisStep: Boolean = false,
    var coupledRpmCurrentGear: Double = 0.0,
    /** Upshift threshold after cruising offset and mode, for the current gear. */
    var effectiveAutomaticUpshiftRpm: Double = 0.0,
    /** Per-gear downshift threshold after cruising offset and mode. */
    var effectiveAutomaticDownshiftRpm: Double = 0.0,
)

/**
 * Straight-line Assetto drivetrain used by the Audio Lab, ported with the same
 * control ordering, three-millisecond timing and authored car parameters.
 *
 * Vehicle speed and FMOD drivetrain speed are intentionally separate. Vehicle speed is the
 * physical/documented road speed, while the FMOD speed is a derived internal value used to make
 * equal-speed gear mapping possible without changing the bank's authored RPM thresholds.
 */
internal class AssettoDrivetrain(
    private var physics: AssettoPhysics,
    private var virtualGearProfile: VirtualGearProfile,
) {
    private var launchSixGearProfile: VirtualGearProfile = buildLaunchSixGearProfile(physics)
    private var sixGearOnLaunchEnabled = false
    private var launchSixGearOverrideActive = false
    /**
     * Test branch behavior: keep the clutch disengaged in D so clutch-slip physics never fights
     * the driver, and always derive RPM from the mapped FMOD road speed in the current gear.
     */
    private val simplifiedDisengagedClutch = SIMPLIFIED_DISENGAGED_CLUTCH
    private var rpm = physics.engine.idleRpm
    private var speedMetersPerSecond = 0.0
    private var gear = 1
    private var sessionElapsedMilliseconds = 0.0
    private var clutchSignal = 0.0
    private var clutchSequence: List<AssettoCurvePoint> = emptyList()
    private var clutchSequenceElapsed = 0.0
    private var automaticGasCutoff = 0.0
    private var engineCutoff = 0.0
    private var shiftDirection = 0
    private var shiftTarget = 1
    private var shiftElapsed = 0.0
    private var shiftDuration = 0.0
    private var shiftStartRpm = 0.0
    private var authoredShiftDuration = 0.0
    private var authoredClutchDuration = 0.0
    private var effectiveClutchDuration = 0.0
    /** Prevents hard braking from chaining automatic downshifts back-to-back. */
    private var automaticDownshiftCooldownSeconds = 0.0
    private var shiftWasAutomatic = false
    private var manualShiftRequest = 0
    /**
     * Landing RPM recorded when each gear is selected by an upshift. Downshifts use this
     * remembered result instead of the bank's broad automatic downshift threshold so a gear is
     * held until it reaches the RPM where the preceding upshift originally landed it.
     */
    private var landingRpmByGear = DoubleArray(virtualGearProfile.virtualForwardGearCount + 1)
    private var previousTractionLimit = false
    private var turboQs = MutableList(physics.engine.turbos.size) { 0.0 }
    private var boost = 0.0
    private var bov = 0.0
    private var bovDecay = 10.0
    private var backfireArmed = false
    private var backfireReleaseTimer = 0.0
    private var previousBackfireThrottle = 0.0
    private var backfireSuppressedAfterShift = false
    private var backfireSettings = BackfireSettings()
    private var currentTransmissionPosition = TransmissionPosition.DRIVE
    private var nextBackfireSampleCursor = 0
    private var useOriginalBackfire = true
    private var limiterCounter = 0
    private var fmodDrivetrainSpeedMetersPerSecond = 0.0
    private var previousFmodWheelSpeed = 0.0
    // Launch control is deliberately kept in the drivetrain because this is where the
    // authoritative RPM, clutch, gear and wheel-coupling state lives. It is enabled by D input
    // from either pedal source and either shift mode; FMOD still receives resulting parameters.
    private var launchControlPhase = LaunchControlPhase.INACTIVE
    private var launchControlJitterPhase = 0.0
    private var launchControlArmedElapsedSeconds = 0.0
    private var launchControlArmedStartRpm = physics.engine.idleRpm
    private var launchControlDisarmElapsedSeconds = 0.0
    private var launchControlTachCycleElapsedSeconds = 0.0
    private var launchControlTachCycleStartRpm = physics.engine.idleRpm
    private var previousLaunchControlPhase = LaunchControlPhase.INACTIVE
    private var cruisingShiftOffsetRpm = 0
    private var racingReturnMaxThrottle = 0.30
    private var manualRedlineHoldSeconds: Double? = 1.0
    private var manualAutodownshiftRpm = 2_000.0
    private var cruisingLogicEnabled = true
    private var automaticTransmissionMode = AutomaticTransmissionMode.CRUISING
    private var racingReturnArmed = false
    private var manualRedlineElapsedSeconds = 0.0
    private var requestAutomaticShiftMode = false
    private var emergencyUpshiftElapsedSeconds = 0.0
    /** Remaining target after a cruising stomp switches to racing. */
    private var racingStompPendingTargetGear: Int? = null
    /** Armed when launch 6-gear ends; full profile resumes on the next throttle application. */
    private var launchReturnArmed = false
    /** Remaining target after launch profile return resumes the configured gear count. */
    private var launchReturnPendingTargetGear: Int? = null
    /** Previous throttle sample used to detect manual kickdown stomps. */
    private var previousRawGasForManualStomp = 0.0
    /** Eases racing RPM into the cruising band after an armed return. */
    private val cruisingReturn = CruisingReturnTransition()
    private var driven = drivenAxle(physics.drivetrain.vehicle)
    private var aeroDrag = 0.0
    private var downforce = 0.0
    private val engineTorqueResult = EngineTorqueFrame()
    private val lastFrame = snapshot()

    fun updatePhysics(updated: AssettoPhysics) {
        physics = updated
        launchSixGearProfile = buildLaunchSixGearProfile(updated)
        driven = drivenAxle(updated.drivetrain.vehicle)
        rpm = rpm.coerceIn(0.0, updated.engine.limiterRpm)
        gear = gear.coerceIn(0, virtualGearProfile.virtualForwardGearCount)
        landingRpmByGear = DoubleArray(virtualGearProfile.virtualForwardGearCount + 1)
        automaticDownshiftCooldownSeconds = 0.0
        shiftWasAutomatic = false
        turboQs = MutableList(updated.engine.turbos.size) { 0.0 }
        boost = 0.0
        bov = 0.0
        bovDecay = 10.0
        resetLaunchControl()
    }

    fun updateVirtualGearProfile(updated: VirtualGearProfile) {
        val hadLaunchProfileOverride = launchSixGearOverrideActive || launchReturnArmed
        virtualGearProfile = updated
        launchSixGearProfile = buildLaunchSixGearProfile(physics)
        landingRpmByGear = DoubleArray(updated.virtualForwardGearCount + 1)
        if (hadLaunchProfileOverride) {
            immediatelySyncGearToConfiguredProfile()
        } else {
            gear = gear.coerceIn(0, updated.virtualForwardGearCount)
        }
    }

    internal fun updateLaunchControl(
        rawThrottle: Double,
        brake: Double,
        enabled: Boolean,
        sixGearOnLaunchEnabled: Boolean,
    ) {
        this.sixGearOnLaunchEnabled = sixGearOnLaunchEnabled
        updateLaunchControlPhase(
            rawThrottle = rawThrottle,
            brake = brake,
            enabled = enabled,
        )
        updateLaunchSixGearOverride(
            rawThrottle = rawThrottle,
            brake = brake,
        )
        updateLaunchProfileReturn(rawThrottle = rawThrottle)
    }

    internal fun isLaunchSixGearOverrideActive(): Boolean = launchSixGearOverrideActive

    internal fun clearLaunchSixGearOverride() {
        if (launchSixGearOverrideActive || launchReturnArmed) {
            immediatelySyncGearToConfiguredProfile()
        } else {
            clearLaunchProfileReturnState()
        }
    }

    fun updateBackfireSettings(updated: BackfireSettings) {
        backfireSettings = updated.normalized()
        backfireArmed = false
        backfireReleaseTimer = 0.0
        previousBackfireThrottle = 0.0
        backfireSuppressedAfterShift = false
        nextBackfireSampleCursor = 0
    }

    fun setUseOriginalBackfire(enabled: Boolean) { useOriginalBackfire = enabled }

    fun reset(engineRunning: Boolean) {
        rpm = if (engineRunning) physics.engine.idleRpm else 0.0
        speedMetersPerSecond = 0.0
        gear = 1
        sessionElapsedMilliseconds = 0.0
        clutchSignal = 0.0
        clutchSequence = emptyList()
        clutchSequenceElapsed = 0.0
        automaticGasCutoff = 0.0
        engineCutoff = 0.0
        shiftDirection = 0
        shiftTarget = 1
        shiftElapsed = 0.0
        shiftDuration = 0.0
        shiftStartRpm = if (engineRunning) physics.engine.idleRpm else 0.0
        authoredShiftDuration = 0.0
        authoredClutchDuration = 0.0
        effectiveClutchDuration = 0.0
        automaticDownshiftCooldownSeconds = 0.0
        shiftWasAutomatic = false
        manualShiftRequest = 0
        landingRpmByGear.fill(0.0)
        fmodDrivetrainSpeedMetersPerSecond = 0.0
        previousFmodWheelSpeed = 0.0
        resetLaunchControl()
        resetAutomaticTransmissionMode()
        previousRawGasForManualStomp = 0.0
        clearCruisingReturnTransition()
        manualRedlineElapsedSeconds = 0.0
        emergencyUpshiftElapsedSeconds = 0.0
        requestAutomaticShiftMode = false
        previousTractionLimit = false
        turboQs.fill(0.0)
        boost = 0.0
        bov = 0.0
        bovDecay = 10.0
        backfireArmed = false
        backfireReleaseTimer = 0.0
        previousBackfireThrottle = 0.0
        backfireSuppressedAfterShift = false
        limiterCounter = 0
        lastFrame.rpm = rpm
        lastFrame.speedMetersPerSecond = speedMetersPerSecond
        lastFrame.gear = gear
        lastFrame.drivetrainSpeedRadiansPerSecond = 0.0
        lastFrame.driverThrottle = 0.0
        lastFrame.effectiveThrottle = 0.0
        lastFrame.brake = 0.0
        lastFrame.clutch = clutchSignal
        lastFrame.boost = boost
        lastFrame.bov = bov
        lastFrame.bovDecaySeconds = bovDecay
        lastFrame.limiterPulse = false
        lastFrame.backfireTriggered = false
        lastFrame.backfireSampleIndex = -1
        lastFrame.shiftStarted = false
        lastFrame.shiftRejected = false
        lastFrame.shifting = shifting
        lastFrame.shiftDirection = shiftDirection
        lastFrame.shiftProgress = 0.0
        lastFrame.authoredShiftDurationSeconds = 0.0
        lastFrame.effectiveShiftDurationSeconds = 0.0
        lastFrame.authoredClutchDurationSeconds = 0.0
        lastFrame.effectiveClutchDurationSeconds = 0.0
        lastFrame.tractionLimitActive = false
        lastFrame.tractionLimitPulse = false
    }

    /**
     * Reconcile gear and RPM after the bank physics profile changes while the vehicle is still
     * moving. Road speed stays authoritative; gear is chosen from the virtual profile bands and
     * RPM follows the mapped FMOD speed in that gear.
     */
    fun seedFromRoadMotion(
        roadSpeedKmh: Double,
        fmodDrivetrainSpeedKmh: Double,
        transmissionPosition: TransmissionPosition,
    ) {
        currentTransmissionPosition = transmissionPosition
        if (transmissionPosition != TransmissionPosition.DRIVE) {
            reset(engineRunning = true)
            return
        }

        val cleanRoadKmh = roadSpeedKmh.coerceAtLeast(0.0)
        if (cleanRoadKmh <= 0.0) {
            reset(engineRunning = true)
            return
        }

        shiftDirection = 0
        shiftTarget = 1
        shiftElapsed = 0.0
        shiftDuration = 0.0
        shiftStartRpm = physics.engine.idleRpm
        authoredShiftDuration = 0.0
        authoredClutchDuration = 0.0
        effectiveClutchDuration = 0.0
        automaticDownshiftCooldownSeconds = 0.0
        shiftWasAutomatic = false
        manualShiftRequest = 0
        clutchSignal = 0.0
        clutchSequence = emptyList()
        clutchSequenceElapsed = 0.0
        automaticGasCutoff = 0.0
        engineCutoff = 0.0
        resetLaunchControl()

        speedMetersPerSecond = cleanRoadKmh / 3.6
        fmodDrivetrainSpeedMetersPerSecond = fmodDrivetrainSpeedKmh.coerceAtLeast(0.0) / 3.6
        previousFmodWheelSpeed = fmodDrivetrainSpeedMetersPerSecond / driven.radius

        val targetGear = computeProfileReturnTargetGear(
            profile = virtualGearProfile,
            dt = 0.016,
        )
        gear = targetGear
        shiftTarget = targetGear

        landingRpmByGear.fill(0.0)
        val upshiftRpm = upshiftTriggerRpmForGear()
        for (indexedGear in 2..targetGear) {
            landingRpmByGear[indexedGear] = landingRpmAfterUpshift(
                fromGear = indexedGear - 1,
                upshiftRpm = upshiftRpm,
            )
        }

        rpm = if (shouldLockRpmToMappedRoadSpeed()) {
            coupledRpmForGear(gear)
        } else {
            physics.engine.idleRpm
        }
        if (physics.engine.limiterRpm > 0.0) {
            rpm = rpm.coerceAtMost(physics.engine.limiterRpm)
        }

        lastFrame.rpm = rpm
        lastFrame.speedMetersPerSecond = speedMetersPerSecond
        lastFrame.gear = gear
        lastFrame.drivetrainSpeedRadiansPerSecond = fmodDrivetrainSpeedMetersPerSecond / driven.radius
        lastFrame.driverThrottle = 0.0
        lastFrame.effectiveThrottle = 0.0
        lastFrame.brake = 0.0
        lastFrame.clutch = clutchSignal
        lastFrame.boost = boost
        lastFrame.bov = bov
        lastFrame.bovDecaySeconds = bovDecay
        lastFrame.limiterPulse = false
        lastFrame.backfireTriggered = false
        lastFrame.backfireSampleIndex = -1
        lastFrame.shiftStarted = false
        lastFrame.shiftRejected = false
        lastFrame.shifting = false
        lastFrame.shiftDirection = 0
        lastFrame.shiftProgress = 0.0
        lastFrame.authoredShiftDurationSeconds = 0.0
        lastFrame.effectiveShiftDurationSeconds = 0.0
        lastFrame.authoredClutchDurationSeconds = 0.0
        lastFrame.effectiveClutchDurationSeconds = 0.0
        lastFrame.tractionLimitActive = false
        lastFrame.tractionLimitPulse = false
    }

    fun requestShift(direction: Int): Boolean {
        if (direction !in -1..1 || direction == 0 || shifting) return false
        manualShiftRequest = direction
        return true
    }

    fun step(
        throttle: Double,
        brake: Double,
        transmissionPosition: TransmissionPosition,
        automaticShifting: Boolean,
        externalVehicleSpeedMetersPerSecond: Double?,
        fmodDrivetrainSpeedMetersPerSecond: Double?,
        deltaSeconds: Double,
        launchControlEnabled: Boolean,
        automaticTransmissionConfig: AutomaticTransmissionConfig,
    ): AssettoDrivetrainFrame {
        val dt = f32(deltaSeconds.coerceIn(0.0001, 0.050))
        cruisingShiftOffsetRpm = CruisingShiftOffsetByTachMaxRpm.resolveOffset(
            offsets = automaticTransmissionConfig.cruisingShiftOffsetsByTachMaxRpm,
            tachometerMaximumRpm = physics.engine.tachometerMaximumRpm,
        )
        racingReturnMaxThrottle = automaticTransmissionConfig.racingReturnMaxThrottle.coerceIn(0.0, 1.0)
        manualRedlineHoldSeconds = automaticTransmissionConfig.manualRedlineHoldSeconds
        manualAutodownshiftRpm = automaticTransmissionConfig.manualAutodownshiftRpm.coerceAtLeast(0.0)
        cruisingLogicEnabled = automaticTransmissionConfig.cruisingLogicEnabled
        sixGearOnLaunchEnabled = automaticTransmissionConfig.sixGearOnLaunchEnabled
        if (!sixGearOnLaunchEnabled && (launchSixGearOverrideActive || launchReturnArmed)) {
            immediatelySyncGearToConfiguredProfile()
        }
        currentTransmissionPosition = transmissionPosition
        sessionElapsedMilliseconds += dt * 1_000.0
        externalVehicleSpeedMetersPerSecond?.let(::anchorVehicleSpeed)
        // In P/N the engine must remain a free-revving authored event. D is the only position
        // allowed to receive the derived FMOD drivetrain speed from the mapping layer.
        this.fmodDrivetrainSpeedMetersPerSecond = if (
            transmissionPosition == TransmissionPosition.DRIVE
        ) {
            (fmodDrivetrainSpeedMetersPerSecond ?: speedMetersPerSecond).coerceAtLeast(0.0)
        } else {
            0.0
        }

        if (transmissionPosition != TransmissionPosition.DRIVE) {
            if (gear != 0 || shifting) setGearImmediately(0)
            // A selector change to P/N cancels any D-only shift cut or clutch
            // profile immediately, so a free rev cannot inherit a prior shift.
            automaticGasCutoff = 0.0
            engineCutoff = 0.0
            clutchSequence = emptyList()
            clutchSequenceElapsed = 0.0
        } else if (gear == 0 && !shifting) {
            setGearImmediately(1)
        }

        var shiftStarted = false
        var shiftRejected = false
        var shiftCompleted = false
        automaticDownshiftCooldownSeconds = max(0.0, automaticDownshiftCooldownSeconds - dt)
        var eventDirection = if (shifting) shiftDirection else 0
        val rawGas = throttle.coerceIn(0.0, 1.0)
        val cleanBrake = brake.coerceIn(0.0, 1.0)
        updateAutomaticTransmissionMode(
            rawGas = rawGas,
            brake = cleanBrake,
            automaticShifting = automaticShifting,
            dt = dt,
        )
        updateLaunchControlRpm(dt, rawGas)
        updateManualAutodownshift(
            automaticShifting = automaticShifting,
            dt = dt,
        )
        updateManualStompDownshift(
            rawGas = rawGas,
            automaticShifting = automaticShifting,
            dt = dt,
        )
        applyRacingStompDownshift(dt)
        applyLaunchReturnGearSync(dt)
        updateEmergencyUpshift(dt)
        updateAeroForSpeed(speedMetersPerSecond)

        val clutch = autoclutchStep(dt, rawGas, cleanBrake)
        var controlsGas = rawGas

        // Neutral and Park are free-revving positions. The zero gear used by
        // the drivetrain integrator must never be mistaken for a request to
        // select first gear when the engine reaches its authored shift RPM.
        val automaticRequest = if (transmissionPosition == TransmissionPosition.DRIVE) {
            automaticShiftDecision(
                controlsGas,
                clutch,
                automaticShifting,
                dt,
            )
        } else {
            0
        }
        if (automaticGasCutoff > 0.0) {
            automaticGasCutoff = f32(automaticGasCutoff - dt)
            controlsGas = 0.0
        }

        val requestedDirection = if (transmissionPosition == TransmissionPosition.DRIVE) {
            manualShiftRequest.takeIf { it != 0 } ?: automaticRequest
        } else {
            // Discard a stale request if the selector changed while a control
            // frame was in flight; P/N must not enter a synthetic shift cycle.
            0
        }
        val requestedManualShift = manualShiftRequest != 0
        manualShiftRequest = 0
        if (acceptShift(requestedDirection, clutch, dt, controlsGas)) {
            shiftStarted = true
            eventDirection = requestedDirection
            shiftWasAutomatic = !requestedManualShift
        } else if (requestedDirection != 0) {
            shiftRejected = true
        }

        if (shifting) {
            if (shiftDuration < shiftElapsed) {
                gear = shiftTarget
                eventDirection = shiftDirection
                shiftDirection = 0
                shiftCompleted = true
                if (eventDirection < 0 && shiftWasAutomatic) {
                    automaticDownshiftCooldownSeconds = AUTOMATIC_DOWNSHIFT_CHAIN_COOLDOWN_SECONDS
                }
            } else {
                shiftElapsed += dt
            }
        }

        val engineGas = if (engineCutoff > 0.0) 0.0 else controlsGas
        if (engineCutoff > 0.0) engineCutoff -= dt
        val engine = engineTorque(dt, controlsGas, engineGas)
        val vehicle = physics.drivetrain.vehicle
        val frontRadius = vehicle.frontWheelRadiusMeters.coerceAtLeast(1e-6)
        val rearRadius = vehicle.rearWheelRadiusMeters.coerceAtLeast(1e-6)
        // Road forces use the physical speed, but engine-to-wheel coupling uses the FMOD speed.
        // This lets 19 km/h of vehicle motion reach an authored 8,000 RPM shift point when the
        // equal-speed mapping says that first gear should occupy 0..19 km/h.
        val wheelSpeed = this.fmodDrivetrainSpeedMetersPerSecond / driven.radius
        val rollingForce = 2.0 * (
            vehicle.frontRollingResistance0 + vehicle.rearRollingResistance0 +
                (vehicle.frontRollingResistance1 + vehicle.rearRollingResistance1) *
                speedMetersPerSecond * speedMetersPerSecond
            )
        val frontBrake = 2.0 * vehicle.brakeMaximumTorque * vehicle.brakeFrontShare / frontRadius
        val rearBrake = 2.0 * vehicle.brakeMaximumTorque * (1.0 - vehicle.brakeFrontShare) / rearRadius
        var serviceBrakeForce = cleanBrake * (frontBrake + rearBrake)
        val totalNormal = vehicle.massKg * GRAVITY + downforce
        val brakeGrip = totalNormal * (
            vehicle.frontWeightFraction * vehicle.frontGripCoefficient +
                (1.0 - vehicle.frontWeightFraction) * vehicle.rearGripCoefficient
            )
        serviceBrakeForce = min(serviceBrakeForce, brakeGrip)
        val resistingForce = aeroDrag + rollingForce + serviceBrakeForce
        val effectiveMass = vehicle.massKg +
            2.0 * vehicle.frontWheelInertia / frontRadius.pow(2) +
            2.0 * vehicle.rearWheelInertia / rearRadius.pow(2)
        // Keep the selected gear visible during a shift, but briefly uncouple
        // the drivetrain exactly as the Lab does while the clutch changes.
        val ratio = abs(ratioForGear(physicsGear) * physics.drivetrain.finalDrive)
        var engineOmega = rpm * RADIAN_SECONDS_PER_RPM
        var driveForce = 0.0
        var clutchTorqueApplied = 0.0
        var requiredClutchTorque = 0.0
        var clutchCapacity = 0.0
        var gripCapacity = 0.0
        var tractionTorqueLimited = false
        if (ratio > 0.0 && clutch > 0.0) {
            val engineInertia = physics.engine.inertia + physics.drivetrain.gearboxInertia
            val slip = engineOmega - ratio * wheelSpeed
            val denominator = 1.0 / engineInertia +
                ratio * ratio / (effectiveMass * driven.radius * driven.radius)
            requiredClutchTorque = (
                slip / dt + engine.torque / engineInertia +
                    resistingForce * ratio / (effectiveMass * driven.radius)
                ) / denominator
            clutchCapacity = physics.drivetrain.clutchMaximumTorque * clutch.pow(1.5)
            val gripForce = driven.grip * totalNormal * driven.normalFraction
            gripCapacity = gripForce * driven.radius / ratio
            tractionTorqueLimited = engine.effectiveThrottle > 0.0 &&
                requiredClutchTorque > gripCapacity + 1e-6 && gripCapacity < clutchCapacity - 1e-6
            clutchTorqueApplied = min(
                clutchCapacity,
                min(gripCapacity, max(-clutchCapacity, requiredClutchTorque)),
            )
            driveForce = clutchTorqueApplied * ratio / driven.radius
            engineOmega += (engine.torque - clutchTorqueApplied) / engineInertia * dt
        } else {
            engineOmega += engine.torque / physics.engine.inertia.coerceAtLeast(0.001) * dt
        }

        if (externalVehicleSpeedMetersPerSecond == null) {
            val oldSpeed = speedMetersPerSecond
            speedMetersPerSecond = max(0.0, speedMetersPerSecond + (driveForce - resistingForce) / effectiveMass * dt)
            if (oldSpeed <= 0.0 && driveForce <= resistingForce) speedMetersPerSecond = 0.0
        }
        rpm = max(0.0, engineOmega * RPM_PER_RADIAN_SECOND)
        // LIMITER is a hard upper bound for the value sent to FMOD and the tachometer. This
        // applies equally to automatic and manual transmission: holding a gear at the limiter
        // now cuts torque without ever producing an above-limiter RPM sample.
        if (physics.engine.limiterRpm > 0.0) {
            rpm = rpm.coerceAtMost(physics.engine.limiterRpm)
            // In P/N there is no drivetrain load to absorb the limiter cut. Holding the
            // authored boundary while the pedal remains down prevents the repeated cut/coast
            // cycle from making a free-revving engine audibly bounce below redline.
            if (transmissionPosition != TransmissionPosition.DRIVE && rawGas > 0.0 && limiterCounter > 0) {
                rpm = physics.engine.limiterRpm
            }
        }
        updateLaunchControlRpm(dt, rawGas)
        if (shouldLockRpmToMappedRoadSpeed()) {
            applyMappedRoadSpeedRpm()
        }
        applyCruisingReturnTransitionRpm(dt)
        if (physics.engine.limiterRpm > 0.0) {
            rpm = rpm.coerceAtMost(physics.engine.limiterRpm)
        }
        previousFmodWheelSpeed = wheelSpeed
        val tractionActive = engine.effectiveThrottle > 0.0 && tractionTorqueLimited
        val tractionPulse = tractionActive && !previousTractionLimit
        previousTractionLimit = tractionActive
        // Reuse the frame object. The simulation has one consumer, so publishing these fields
        // in place removes one large allocation from every fixed-step update without changing
        // values.
        lastFrame.rpm = rpm
        lastFrame.speedMetersPerSecond = speedMetersPerSecond
        // Do not expose the internal neutral interval as gear 0 during a normal shift; effects
        // still use shiftStarted below.
        lastFrame.gear = if (shifting) shiftTarget else gear
        // Native FMOD receives this internal angular speed. The public vehicle speed remains
        // available separately through DrivetrainState.fmodDrivetrainSpeedKmh.
        lastFrame.drivetrainSpeedRadiansPerSecond = this.fmodDrivetrainSpeedMetersPerSecond / driven.radius
        lastFrame.driverThrottle = rawGas
        lastFrame.effectiveThrottle = engine.effectiveThrottle
        lastFrame.brake = cleanBrake
        lastFrame.clutch = clutch
        lastFrame.boost = boost
        lastFrame.bov = bov
        lastFrame.bovDecaySeconds = bovDecay
        lastFrame.limiterPulse = engine.limiterActive
        lastFrame.backfireTriggered = engine.backfire
        lastFrame.backfireSampleIndex = if (engine.backfire &&
            (backfireSettings.soundOnlyOverrideEnabled || !engine.naturalBankBackfire)
        ) {
            chooseBackfireSample()
        } else {
            -1
        }
        lastFrame.shiftStarted = shiftStarted
        lastFrame.shiftRejected = shiftRejected
        lastFrame.shifting = shifting
        lastFrame.shiftDirection = if (shiftStarted || shiftCompleted || shifting) eventDirection else 0
        // Preserve a final 1.0 sample so presentation code can finish a gear-ratio crossfade
        // without reintroducing an audible one-frame pitch step.
        lastFrame.shiftProgress = when {
            shifting -> (shiftElapsed / shiftDuration.coerceAtLeast(dt)).coerceIn(0.0, 1.0)
            shiftCompleted -> 1.0
            else -> 0.0
        }
        lastFrame.authoredShiftDurationSeconds = authoredShiftDuration
        lastFrame.effectiveShiftDurationSeconds = shiftDuration
        lastFrame.authoredClutchDurationSeconds = authoredClutchDuration
        lastFrame.effectiveClutchDurationSeconds = effectiveClutchDuration
        lastFrame.tractionLimitActive = tractionActive
        lastFrame.tractionLimitPulse = tractionPulse
        updateManualRedlineAutomaticMode(
            automaticShifting = automaticShifting,
            dt = dt,
        )
        if (launchControlPhase == LaunchControlPhase.LAUNCHED && gear > 1 && !shifting) {
            launchControlPhase = LaunchControlPhase.INACTIVE
        }
        lastFrame.automaticTransmissionMode = automaticTransmissionMode
        lastFrame.effectiveAutomaticUpshiftRpm = effectiveUpshiftTriggerRpm()
        lastFrame.effectiveAutomaticDownshiftRpm = effectiveDownshiftRpmForCurrentGear()
        lastFrame.racingReturnArmed = racingReturnArmed
        lastFrame.launchSixGearOverrideActive = launchSixGearOverrideActive
        lastFrame.launchReturnArmed = launchReturnArmed
        lastFrame.requestAutomaticShiftMode = requestAutomaticShiftMode
        return lastFrame
    }

    fun frame(): AssettoDrivetrainFrame = lastFrame

    private val shifting: Boolean get() = shiftDirection != 0

    private fun anchorVehicleSpeed(speed: Double) {
        speedMetersPerSecond = speed.coerceAtLeast(0.0)
    }

    private fun setGearImmediately(target: Int) {
        ratioForGear(target)
        gear = target
        shiftDirection = 0
        shiftTarget = target
        shiftElapsed = 0.0
        shiftDuration = 0.0
        shiftStartRpm = rpm
        authoredShiftDuration = 0.0
        authoredClutchDuration = 0.0
        effectiveClutchDuration = 0.0
    }

    private fun autoclutchStep(dt: Double, gas: Double, brake: Double): Double {
        if (
            simplifiedDisengagedClutch &&
            currentTransmissionPosition == TransmissionPosition.DRIVE
        ) {
            clutchSequence = emptyList()
            clutchSequenceElapsed = 0.0
            clutchSignal = 0.0
            return 0.0
        }

        // In automatic D, a firm brake at walking speed means the driver is holding the car
        // stopped. The authored autoclutch rate is intentionally gentle for normal launches, but
        // letting that rate continue all the way to zero would keep the engine mechanically tied
        // to a stationary wheel while the presentation-speed estimate decays. Release immediately
        // in this one physical condition so the engine settles at its authored idle instead of
        // being dragged through zero RPM. This is a stop-protection invariant, not a new shift
        // threshold or a replacement for any bank clutch profile.
        if (
            gear == 1 &&
            !shifting &&
            brake >= STOPPED_CLUTCH_RELEASE_BRAKE &&
            speedMetersPerSecond <= STOPPED_CLUTCH_RELEASE_SPEED_MPS
        ) {
            clutchSequence = emptyList()
            clutchSequenceElapsed = 0.0
            clutchSignal = 0.0
            return 0.0
        }
        if (clutchSequence.isNotEmpty()) {
            clutchSignal = interpolateAssettoCurve(clutchSequence, clutchSequenceElapsed)
            clutchSequenceElapsed = f32(clutchSequenceElapsed + dt)
            if (clutchSequenceElapsed > clutchSequence.last().x) clutchSequence = emptyList()
            return clutchSignal.coerceIn(0.0, 1.0)
        }

        val spec = physics.drivetrain
        val controlGear = physicsGear
        val target = when {
            controlGear == -1 || controlGear == 1 -> when {
                rpm < spec.autoclutchMinimumRpm -> 0.0
                rpm > spec.autoclutchMaximumRpm -> 1.0
                else -> (rpm - spec.autoclutchMinimumRpm) /
                    (spec.autoclutchMaximumRpm - spec.autoclutchMinimumRpm).coerceAtLeast(1.0)
            }
            controlGear == 0 -> if (speedMetersPerSecond * 3.6 >= 5.0 || gas > 0.2) 1.0 else 0.0
            else -> if (rpm >= spec.autoclutchMinimumRpm) 1.0 else 0.0
        }
        val maximumStep = spec.autoclutchSpeed * dt
        clutchSignal += (target - clutchSignal).coerceIn(-maximumStep, maximumStep)
        return clutchSignal.coerceIn(0.0, 1.0)
    }

    private fun automaticShiftRpmForDecision(): Double {
        if (launchControlPhase == LaunchControlPhase.LAUNCHED) {
            // Launch tach animation can hold first-gear RPM at redline while road speed is still
            // near zero. Automatic upshifts must follow mapped wheel speed, not the staged RPM.
            return coupledRpmForGear(gear)
        }

        return rpm
    }

    private fun automaticShiftDecision(
        gas: Double,
        clutch: Double,
        enabled: Boolean,
        dt: Double,
    ): Int {
        if (!enabled || gear == -1 || shifting) return 0
        // While staging, launch control owns the engine RPM target. Do not let the automatic
        // threshold interpret the 5k staging ramp as a request to leave first gear before the
        // driver releases the brake.
        if (launchControlPhase == LaunchControlPhase.ARMED ||
            launchControlPhase == LaunchControlPhase.DISARMING
        ) {
            return 0
        }

        if (cruisingReturn.active) {
            return 0
        }

        var request = 0
        if (simplifiedDisengagedClutch || clutch > 0.99 || gear == 0) {
            // Upshifts use the bank-authored automatic threshold. The equal-speed layer changes
            // only speed-to-RPM conversion.
            val shiftRpm = automaticShiftRpmForDecision()
            val upshiftRpm = effectiveUpshiftTriggerRpm()
            if (
                automaticUpshiftAllowed(shiftRpm, upshiftRpm, gas) &&
                gear < effectiveVirtualGearProfile().virtualForwardGearCount &&
                automaticGasCutoff <= 0.0
            ) {
                request = 1
                automaticGasCutoff = f32(physics.drivetrain.automaticGasCutoffSeconds)
            } else {
                val downshiftRpm = effectiveDownshiftRpmForCurrentGear()
                // Landing-RPM downshifts are an intentional app policy, but they must describe
                // a lift/braking phase rather than the tiny post-upshift settling error. When the
                // FMOD drivetrain speed is still increasing, a full-throttle car is accelerating
                // and cannot legitimately request the reverse shift immediately after an upshift.
                // The bank's RPM landing value remains the threshold once the documented/FMOD
                // speed is actually falling.
                // previousFmodWheelSpeed is stored as wheel angular speed (rad/s), so compare
                // it with the same normalized wheel quantity. Comparing raw m/s to rad/s here
                // would make every positive road speed look like a deceleration and would allow
                // the landing-RPM rule to chatter through every gear under full throttle.
                val currentFmodWheelSpeed = fmodDrivetrainSpeedMetersPerSecond / driven.radius
                val fmodDrivetrainSpeedDecreasing =
                    currentFmodWheelSpeed < previousFmodWheelSpeed - 1e-4
                if (
                    shiftRpm < downshiftRpm &&
                    gear > 1 && (simplifiedDisengagedClutch || clutch > 0.85) &&
                    (gas <= 0.2 || fmodDrivetrainSpeedDecreasing) &&
                    downshiftAllowed(gear - 1, dt) &&
                    automaticGasCutoff <= 0.0 &&
                    automaticDownshiftCooldownSeconds <= 0.0
                ) request = -1
            }
        }
        return request
    }

    private fun acceptShift(
        direction: Int,
        clutch: Double,
        dt: Double,
        gas: Double,
    ): Boolean {
        if (direction == 0 || shifting) return false
        val target = gear + direction
        val forwardGearCount = effectiveVirtualGearProfile().virtualForwardGearCount
        if (target !in -1..forwardGearCount) return false
        if (direction < 0 && !downshiftAllowed(target, dt)) return false

        shiftDirection = direction
        shiftTarget = target
        shiftStartRpm = rpm
        shiftElapsed = 0.0
        authoredShiftDuration = if (direction > 0) physics.drivetrain.gearUpTimeSeconds
        else physics.drivetrain.gearDownTimeSeconds
        // Deliberate app-level divergence: fixed short timings make the controls feel
        // responsive, while FMOD still receives the same authored event triggers and
        // continuous parameters. The bank timing is retained above for diagnostics.
        shiftDuration = when {
            cruisingReturn.active && direction < 0 -> CRUISING_RETURN_DOWNSHIFT_SECONDS
            direction > 0 -> FIXED_UPSHIFT_SECONDS
            else -> FIXED_DOWNSHIFT_SECONDS
        }
        if (direction > 0) {
            landingRpmByGear[target] = landingRpmAfterUpshift(
                fromGear = gear,
                upshiftRpm = upshiftTriggerRpmForGear(),
            )
        }
        if (direction > 0 && physics.drivetrain.autoCutoffTimeSeconds != 0.0) {
            engineCutoff = physics.drivetrain.autoCutoffTimeSeconds
        }
        val authoredProfile = if (direction > 0) {
            physics.drivetrain.autoclutchUpshiftProfile
        } else {
            physics.drivetrain.autoclutchDownshiftProfile
        }
        authoredClutchDuration = authoredProfile.maxOfOrNull { it.x } ?: 0.0
        if (physics.drivetrain.autoclutchOnChanges && clutch > 0.01 && !simplifiedDisengagedClutch) {
            // The authored downshift curve can keep the clutch open for ~1.5 s. Use a
            // compact equivalent curve so the drivetrain catches the new gear promptly;
            // this changes only host clutch timing, not FMOD gear/lift-off/turbo events.
            effectiveClutchDuration = shiftDuration
            clutchSequence = fixedClutchSequence(effectiveClutchDuration)
            clutchSequenceElapsed = 0.0
        }
        return true
    }

    /** True when RPM must follow mapped FMOD road speed in the current gear. */
    private fun shouldLockRpmToMappedRoadSpeed(): Boolean {
        if (!simplifiedDisengagedClutch) {
            return false
        }

        if (currentTransmissionPosition != TransmissionPosition.DRIVE) {
            return false
        }

        // Launch control owns RPM while staging or executing a launch. The simplified clutch path
        // otherwise pins first-gear RPM to idle whenever FMOD road speed is zero.
        if (launchControlPhase != LaunchControlPhase.INACTIVE) {
            return false
        }

        return gear >= 1
    }

    /**
     * RPM locked to mapped FMOD speed within the current gear band. Gear 1 starts at authored
     * idle when fmod speed is zero and rises immediately with road speed instead of staying at
     * idle until the raw ratio formula would exceed idle on its own.
     */
    private fun coupledRpmForGear(
        currentGear: Int,
        profile: VirtualGearProfile = effectiveVirtualGearProfile(),
    ): Double {
        if (currentGear < 1) {
            return physics.engine.idleRpm
        }

        val idle = physics.engine.idleRpm
        val upshiftRpm = upshiftTriggerRpmForGear()
        val forwardGearCount = profile.virtualForwardGearCount
        val fmodMps = fmodDrivetrainSpeedMetersPerSecond

        val lowerFmodMps = if (currentGear == 1) {
            0.0
        } else {
            fmodDrivetrainSpeedMpsAtRpm(upshiftRpm, currentGear - 1, profile)
        }

        val upperRpm = if (currentGear >= forwardGearCount) {
            physics.engine.limiterRpm.coerceAtLeast(upshiftRpm)
        } else {
            upshiftRpm
        }
        val upperFmodMps = fmodDrivetrainSpeedMpsAtRpm(upperRpm, currentGear, profile)

        val lowerRpm = if (currentGear == 1) {
            idle
        } else {
            landingRpmAfterUpshift(fromGear = currentGear - 1, upshiftRpm = upshiftRpm)
        }

        val fmodSpan = upperFmodMps - lowerFmodMps
        if (fmodSpan <= 1e-9) {
            return lowerRpm
        }

        // Do not clamp the lower end: when FMOD speed falls below this gear's band the RPM must
        // be allowed to drop under the landing value so automatic downshifts can fire.
        val fraction = (fmodMps - lowerFmodMps) / fmodSpan
        val interpolated = lowerRpm + fraction * (upperRpm - lowerRpm)
        return interpolated.coerceAtLeast(idle).let { rpm ->
            if (physics.engine.limiterRpm > 0.0) {
                rpm.coerceAtMost(physics.engine.limiterRpm)
            } else {
                rpm
            }
        }
    }

    /**
     * Follow mapped road speed exactly while driving. During a shift, linearly blend from the
     * RPM at shift request time to the target gear's coupled value over the shift duration.
     */
    private fun applyMappedRoadSpeedRpm() {
        if (cruisingReturn.active) {
            return
        }

        if (shifting) {
            val target = coupledRpmForGear(shiftTarget)
            val progress = (shiftElapsed / shiftDuration.coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
            rpm = shiftStartRpm + (target - shiftStartRpm) * progress
        } else {
            rpm = coupledRpmForGear(gear)
        }

        if (physics.engine.limiterRpm > 0.0) {
            rpm = rpm.coerceAtMost(physics.engine.limiterRpm)
        }

        applyCruisingBandCap()
    }

    /**
     * While cruising, never publish a mapped RPM above the cruising upshift line.
     *
     * The speed-to-RPM table is authored on racing thresholds. In a tall gear at high road
     * speed that table still sits hundreds of RPM above the cruising band, which is the
     * settle the driver sees after a return if we hand control straight back to the map.
     */
    private fun applyCruisingBandCap() {
        if (!cruisingLogicEnabled) {
            return
        }

        if (automaticTransmissionMode != AutomaticTransmissionMode.CRUISING) {
            return
        }

        if (launchControlPhase != LaunchControlPhase.INACTIVE) {
            return
        }

        val cap = effectiveUpshiftTriggerRpm()
        if (rpm > cap) {
            rpm = cap
        }
    }

    private fun cruisingBandTargetRpm(coupledRpm: Double): Double {
        return CruisingReturn.bandTargetRpm(
            coupledRpm = coupledRpm,
            cruisingUpshiftRpm = effectiveUpshiftTriggerRpm(),
        )
    }

    /** Internal FMOD wheel speed that would produce [rpm] in [gearForRatio]. */
    private fun fmodDrivetrainSpeedMpsAtRpm(
        rpm: Double,
        gearForRatio: Int,
        profile: VirtualGearProfile = effectiveVirtualGearProfile(),
    ): Double {
        val ratio = abs(ratioForGear(gearForRatio, profile) * physics.drivetrain.finalDrive)
        if (ratio <= 0.0) {
            return 0.0
        }

        return rpm * driven.radius / (ratio * RPM_PER_RADIAN_SECOND)
    }

    /** Relocated automatic upshift RPM, anchored just below the limiter when one exists. */
    private fun upshiftTriggerRpmForGear(): Double {
        return relocatedShiftThresholds().upshiftRpm
    }

    private fun downshiftTriggerRpmForGear(): Double {
        return relocatedShiftThresholds().downshiftRpm
    }

    private fun relocatedShiftThresholds(): AutomaticTransmissionPolicy.RelocatedShiftThresholds {
        return AutomaticTransmissionPolicy.relocatedShiftThresholds(
            authoredUpshiftRpm = physics.drivetrain.automaticUpshiftRpm,
            authoredDownshiftRpm = physics.drivetrain.automaticDownshiftRpm,
            limiterRpm = physics.engine.limiterRpm,
            idleRpm = physics.engine.idleRpm,
        )
    }

    fun applyCruisingLogicToggle(enabled: Boolean) {
        cruisingLogicEnabled = enabled
        automaticTransmissionMode = AutomaticTransmissionMode.CRUISING
        racingReturnArmed = false
        racingStompPendingTargetGear = null

        if (!enabled) {
            automaticTransmissionMode = AutomaticTransmissionMode.RACING
        }
    }

    private fun effectiveUpshiftTriggerRpm(): Double {
        val base = upshiftTriggerRpmForGear()
        if (!cruisingLogicEnabled || automaticTransmissionMode == AutomaticTransmissionMode.RACING || cruisingShiftOffsetRpm <= 0) {
            return base
        }

        return AutomaticTransmissionPolicy.applyCruisingOffset(
            baseRpm = base,
            offsetRpm = cruisingShiftOffsetRpm,
            idleRpm = physics.engine.idleRpm,
        )
    }

    /**
     * Cruising lowers the upshift threshold. If RPM ends up above that relocated point — for
     * example after returning from racing with a light pedal — still request the upshift.
     */
    private fun automaticUpshiftAllowed(
        shiftRpm: Double,
        upshiftRpm: Double,
        gas: Double,
    ): Boolean {
        if (shiftRpm < upshiftRpm) {
            return false
        }

        if (!cruisingLogicEnabled) {
            return gas > 0.2
        }

        if (automaticTransmissionMode == AutomaticTransmissionMode.CRUISING) {
            return true
        }

        return gas > 0.2
    }

    /** RPM that resulted from the preceding upshift at a shared wheel speed. */
    private fun landingRpmAfterUpshift(fromGear: Int, upshiftRpm: Double): Double {
        val fromRatio = abs(ratioForGear(fromGear))
        val targetRatio = abs(ratioForGear(fromGear + 1))
        if (fromRatio <= 1e-9 || targetRatio <= 1e-9) return upshiftRpm
        return (upshiftRpm * targetRatio / fromRatio).coerceAtLeast(physics.engine.idleRpm)
    }

    private fun downshiftRpmForCurrentGear(): Double {
        return landingRpmByGear.getOrNull(gear)
            ?.takeIf { it > 0.0 }
            ?: landingRpmAfterUpshift(
                fromGear = gear - 1,
                upshiftRpm = upshiftTriggerRpmForGear(),
            ).coerceAtLeast(downshiftTriggerRpmForGear())
    }

    private fun effectiveDownshiftRpmForCurrentGear(): Double {
        val base = if (gear <= 1) {
            physics.engine.idleRpm
        } else {
            downshiftRpmForCurrentGear()
        }
        if (!cruisingLogicEnabled || automaticTransmissionMode == AutomaticTransmissionMode.RACING || cruisingShiftOffsetRpm <= 0) {
            return base
        }

        return AutomaticTransmissionPolicy.applyCruisingOffset(
            baseRpm = base,
            offsetRpm = cruisingShiftOffsetRpm,
            idleRpm = physics.engine.idleRpm,
        )
    }

    private fun resetAutomaticTransmissionMode() {
        automaticTransmissionMode = AutomaticTransmissionMode.CRUISING
        racingReturnArmed = false
        racingStompPendingTargetGear = null
        clearCruisingReturnTransition()
    }

    private fun clearCruisingReturnTransition() {
        cruisingReturn.clear()
    }

    private fun beginCruisingReturnTransition() {
        val targetGear = computeCruisingReturnTargetGear()
        cruisingReturn.begin(
            currentRpm = rpm,
            currentGear = gear,
            computedTargetGear = targetGear,
            initialLiveTargetRpm = cruisingBandTargetRpm(coupledRpmForGear(targetGear)),
        )
    }

    /** Tallest needed gear whose coupled RPM sits in the cruising upshift band. */
    private fun computeCruisingReturnTargetGear(): Int {
        val profile = effectiveVirtualGearProfile()
        return CruisingReturn.targetGear(
            currentGear = gear,
            topGear = profile.virtualForwardGearCount,
            cruisingUpshiftRpm = effectiveUpshiftTriggerRpm(),
            coupledRpmForGear = { candidate ->
                coupledRpmForGear(candidate, profile)
            },
        )
    }

    /**
     * Glide toward the live coupled RPM of the cruising gear. Does not yield to mapped road-speed
     * lock until that gear is selected and the needle is actually there.
     */
    private fun applyCruisingReturnTransitionRpm(dt: Double) {
        val computedTargetGear = computeCruisingReturnTargetGear()
        val liveTargetRpm = cruisingBandTargetRpm(coupledRpmForGear(computedTargetGear))
        val topGear = effectiveVirtualGearProfile().virtualForwardGearCount
        val nextGearCoupledRpm = if (gear < topGear) {
            coupledRpmForGear(gear + 1)
        } else {
            null
        }

        if (!cruisingReturn.active) {
            lastFrame.cruisingReturnActive = false
            lastFrame.cruisingReturnTargetGear = computedTargetGear
            lastFrame.cruisingReturnChaseRpm = 0.0
            lastFrame.cruisingReturnLiveTargetRpm = liveTargetRpm
            lastFrame.cruisingReturnComputedTargetGear = computedTargetGear
            lastFrame.cruisingReturnGlideRpmPerSecond = 0.0
            lastFrame.cruisingReturnNextGearCoupledRpm = nextGearCoupledRpm ?: 0.0
            lastFrame.cruisingReturnRequestUpshift = false
            lastFrame.cruisingReturnFinishedThisStep = false
            lastFrame.coupledRpmCurrentGear = coupledRpmForGear(gear)
            return
        }

        val step = cruisingReturn.step(
            dt = dt,
            currentRpm = rpm,
            currentGear = gear,
            shifting = shifting,
            liveTargetRpm = liveTargetRpm,
            computedTargetGear = computedTargetGear,
            nextGearCoupledRpm = nextGearCoupledRpm,
        )
        rpm = step.rpm

        if (physics.engine.limiterRpm > 0.0) {
            rpm = rpm.coerceAtMost(physics.engine.limiterRpm)
        }

        if (step.requestUpshift) {
            manualShiftRequest = 1
        }

        publishCruisingReturnDebug(
            liveTargetRpm = liveTargetRpm,
            computedTargetGear = computedTargetGear,
            nextGearCoupledRpm = nextGearCoupledRpm,
        )
    }

    private fun publishCruisingReturnDebug(
        liveTargetRpm: Double,
        computedTargetGear: Int,
        nextGearCoupledRpm: Double?,
    ) {
        val debug = cruisingReturn.lastDebug
        lastFrame.cruisingReturnActive = cruisingReturn.active || debug.finished
        lastFrame.cruisingReturnTargetGear = debug.targetGear
        lastFrame.cruisingReturnChaseRpm = debug.chaseRpm
        lastFrame.cruisingReturnLiveTargetRpm = liveTargetRpm
        lastFrame.cruisingReturnComputedTargetGear = computedTargetGear
        lastFrame.cruisingReturnGlideRpmPerSecond = debug.glideRpmPerSecond
        lastFrame.cruisingReturnNextGearCoupledRpm = nextGearCoupledRpm ?: 0.0
        lastFrame.cruisingReturnRequestUpshift = debug.requestUpshift
        lastFrame.cruisingReturnFinishedThisStep = debug.finished
        lastFrame.coupledRpmCurrentGear = coupledRpmForGear(gear)
    }

    /**
     * Cruising stomp kickdown: at the current mapped road speed, pick the highest gear whose
     * coupled RPM lands in [redline - manualAutodownshiftRpm, redline).
     */
    private fun racingStompTargetRpm(): Double {
        val redlineRpm = effectiveRedlineThresholdRpm()
        return (redlineRpm - manualAutodownshiftRpm).coerceAtLeast(physics.engine.idleRpm)
    }

    private fun computeRacingStompTargetGear(dt: Double): Int {
        if (gear <= 1) {
            return 1
        }

        val targetRpm = racingStompTargetRpm()
        val redlineRpm = effectiveRedlineThresholdRpm()
        var selectedGear = gear

        for (candidate in (gear - 1) downTo 1) {
            if (!downshiftAllowed(candidate, dt)) {
                continue
            }

            val projectedRpm = coupledRpmForGear(candidate)
            if (projectedRpm >= targetRpm && projectedRpm < redlineRpm) {
                selectedGear = candidate
                break
            }
        }

        if (selectedGear == gear) {
            val currentProjected = coupledRpmForGear(gear)
            if (currentProjected < targetRpm) {
                var mostAggressiveAllowed = gear
                for (candidate in (gear - 1) downTo 1) {
                    if (!downshiftAllowed(candidate, dt)) {
                        continue
                    }

                    val projectedRpm = coupledRpmForGear(candidate)
                    if (projectedRpm < redlineRpm) {
                        mostAggressiveAllowed = candidate
                    }
                }
                selectedGear = mostAggressiveAllowed
            }
        }

        return selectedGear
    }

    private fun applyRacingStompDownshift(dt: Double) {
        val targetGear = racingStompPendingTargetGear ?: return

        if (shifting) {
            return
        }

        if (gear <= targetGear) {
            racingStompPendingTargetGear = null
            return
        }

        if (downshiftAllowed(gear - 1, dt)) {
            manualShiftRequest = -1
        } else {
            racingStompPendingTargetGear = null
        }
    }

    private fun updateEmergencyUpshift(dt: Double) {
        if (cruisingReturn.active) {
            emergencyUpshiftElapsedSeconds = 0.0
            return
        }

        if (currentTransmissionPosition != TransmissionPosition.DRIVE) {
            emergencyUpshiftElapsedSeconds = 0.0
            return
        }

        if (shifting) {
            return
        }

        val topGear = effectiveVirtualGearProfile().virtualForwardGearCount
        if (gear < 1 || gear >= topGear) {
            emergencyUpshiftElapsedSeconds = 0.0
            return
        }

        if (!isAtEmergencyUpshiftRpm()) {
            emergencyUpshiftElapsedSeconds = 0.0
            return
        }

        emergencyUpshiftElapsedSeconds += dt
        if (emergencyUpshiftElapsedSeconds >= AutomaticTransmissionPolicy.EMERGENCY_UPSHIFT_HOLD_SECONDS) {
            emergencyUpshiftElapsedSeconds = 0.0
            manualShiftRequest = 1
        }
    }

    private fun isAtEmergencyUpshiftRpm(): Boolean {
        if (physics.engine.limiterRpm > 0.0) {
            return rpm >= physics.engine.limiterRpm
        }

        return rpm >= upshiftTriggerRpmForGear()
    }

    private fun updateAutomaticTransmissionMode(
        rawGas: Double,
        brake: Double,
        automaticShifting: Boolean,
        dt: Double,
    ) {
        if (currentTransmissionPosition != TransmissionPosition.DRIVE) {
            resetAutomaticTransmissionMode()
            return
        }

        if (!automaticShifting) {
            return
        }

        if (!cruisingLogicEnabled) {
            automaticTransmissionMode = AutomaticTransmissionMode.RACING
            racingReturnArmed = false
            return
        }

        if (launchControlPhase != LaunchControlPhase.INACTIVE) {
            automaticTransmissionMode = AutomaticTransmissionMode.RACING
            racingReturnArmed = false
            racingStompPendingTargetGear = null
            clearCruisingReturnTransition()
            return
        }

        if (speedMetersPerSecond <= AutomaticTransmissionPolicy.CRUISING_RETURN_MAX_SPEED_MPS) {
            automaticTransmissionMode = AutomaticTransmissionMode.CRUISING
            racingReturnArmed = false
            racingStompPendingTargetGear = null
        }

        val racingThrottleRequested = rawGas > AutomaticTransmissionPolicy.RACING_ENTER_MIN_THROTTLE

        if (
            automaticTransmissionMode == AutomaticTransmissionMode.CRUISING &&
            racingThrottleRequested &&
            launchControlPhase == LaunchControlPhase.INACTIVE
        ) {
            automaticTransmissionMode = AutomaticTransmissionMode.RACING
            racingReturnArmed = false
            clearCruisingReturnTransition()
            if (gear > 1 && !shifting) {
                val targetGear = computeRacingStompTargetGear(dt)
                if (targetGear < gear) {
                    racingStompPendingTargetGear = targetGear
                }
            }
        }

        if (automaticTransmissionMode == AutomaticTransmissionMode.RACING) {
            if (brake >= AutomaticTransmissionPolicy.RACING_RETURN_ARM_MIN_BRAKE) {
                racingReturnArmed = true
            }

            if (rawGas <= AutomaticTransmissionPolicy.RACING_RETURN_ARM_MAX_THROTTLE) {
                racingReturnArmed = true
            }

            if (racingReturnArmed && rawGas > 0.0) {
                if (rawGas > racingReturnMaxThrottle) {
                    racingReturnArmed = false
                } else {
                    automaticTransmissionMode = AutomaticTransmissionMode.CRUISING
                    racingReturnArmed = false
                    racingStompPendingTargetGear = null
                    beginCruisingReturnTransition()
                }
            }
        }
    }

    private fun updateManualAutodownshift(
        automaticShifting: Boolean,
        dt: Double,
    ) {
        if (automaticShifting || currentTransmissionPosition != TransmissionPosition.DRIVE) {
            return
        }

        if (launchControlPhase != LaunchControlPhase.INACTIVE) {
            return
        }

        if (
            rpm < manualAutodownshiftRpm &&
            gear > 1 &&
            !shifting &&
            automaticDownshiftCooldownSeconds <= 0.0 &&
            downshiftAllowed(gear - 1, dt)
        ) {
            manualShiftRequest = -1
        }
    }

    /**
     * Manual kickdown: crossing the same racing stomp throttle threshold arms a downshift to the
     * highest gear whose coupled RPM lands in the high-RPM window at the current road speed.
     */
    private fun updateManualStompDownshift(
        rawGas: Double,
        automaticShifting: Boolean,
        dt: Double,
    ) {
        if (automaticShifting || currentTransmissionPosition != TransmissionPosition.DRIVE) {
            previousRawGasForManualStomp = rawGas
            return
        }

        if (
            launchControlPhase != LaunchControlPhase.INACTIVE ||
            launchSixGearOverrideActive ||
            launchReturnArmed
        ) {
            previousRawGasForManualStomp = rawGas
            return
        }

        val stompThreshold = AutomaticTransmissionPolicy.RACING_ENTER_MIN_THROTTLE
        val stompDetected = previousRawGasForManualStomp <= stompThreshold &&
            rawGas > stompThreshold
        previousRawGasForManualStomp = rawGas

        if (!stompDetected || gear <= 1 || shifting || racingStompPendingTargetGear != null) {
            return
        }

        val targetGear = computeRacingStompTargetGear(dt)
        if (targetGear < gear) {
            racingStompPendingTargetGear = targetGear
        }
    }

    private fun updateManualRedlineAutomaticMode(
        automaticShifting: Boolean,
        dt: Double,
    ) {
        requestAutomaticShiftMode = false

        if (automaticShifting || currentTransmissionPosition != TransmissionPosition.DRIVE) {
            manualRedlineElapsedSeconds = 0.0
            return
        }

        if (launchControlPhase != LaunchControlPhase.INACTIVE) {
            manualRedlineElapsedSeconds = 0.0
            return
        }

        val redlineThreshold = effectiveRedlineThresholdRpm()
        val holdSeconds = manualRedlineHoldSeconds
        if (rpm >= redlineThreshold) {
            manualRedlineElapsedSeconds += dt
            if (holdSeconds != null && manualRedlineElapsedSeconds >= holdSeconds) {
                manualRedlineElapsedSeconds = 0.0
                automaticTransmissionMode = AutomaticTransmissionMode.RACING
                racingReturnArmed = false
                requestAutomaticShiftMode = true
            }
        } else {
            manualRedlineElapsedSeconds = 0.0
        }
    }

    private fun effectiveRedlineThresholdRpm(): Double {
        if (physics.engine.limiterRpm > 0.0) {
            return physics.engine.limiterRpm
        }

        return upshiftTriggerRpmForGear().coerceAtLeast(physics.engine.idleRpm)
    }

    private fun downshiftAllowed(target: Int, dt: Double): Boolean {
        val spec = physics.drivetrain
        if (!spec.downshiftProtection) return true
        if (target == 0 && spec.downshiftLocksNeutral && speedMetersPerSecond * 3.6 > 2.0) return false
        if (target <= 0) return true
        return projectedRpmForGear(target, dt) <= physics.engine.limiterRpm + spec.downshiftOverrevRpm
    }

    private fun projectedRpmForGear(
        target: Int,
        dt: Double,
    ): Double {
        val spec = physics.drivetrain
        val wheelSpeed = fmodDrivetrainSpeedMetersPerSecond / driven.radius
        val wheelAcceleration = max(0.0, (wheelSpeed - previousFmodWheelSpeed) / dt.coerceAtLeast(1e-9))
        // Match downshift protection to the effective fixed transition, otherwise the
        // protection calculation would still predict using the bank's slower timing.
        val shiftTime = FIXED_DOWNSHIFT_SECONDS
        val projected = wheelSpeed + shiftTime * wheelAcceleration
        return projected * abs(ratioForGear(target) * spec.finalDrive) * RPM_PER_RADIAN_SECOND
    }

    private fun applyLaunchReturnGearSync(dt: Double) {
        val targetGear = launchReturnPendingTargetGear ?: return

        if (shifting) {
            return
        }

        if (gear == targetGear) {
            launchReturnPendingTargetGear = null
            return
        }

        if (gear < targetGear) {
            manualShiftRequest = 1
            return
        }

        if (downshiftAllowed(gear - 1, dt)) {
            manualShiftRequest = -1
        } else {
            launchReturnPendingTargetGear = null
        }
    }

    /**
     * After launch 6-gear ends, pick the configured-profile gear whose coupled RPM best fits the
     * current mapped road speed. May upshift or downshift relative to the speed-band baseline.
     */
    private fun computeProfileReturnTargetGear(
        profile: VirtualGearProfile,
        dt: Double,
    ): Int {
        val roadKmh = speedMetersPerSecond * 3.6
        var candidate = profile.gearForRoadSpeedKmh(roadKmh)
            .coerceIn(1, profile.virtualForwardGearCount)
        val upshiftRpm = upshiftTriggerRpmForGear()
        val downshiftRpm = downshiftTriggerRpmForGear()

        while (candidate < profile.virtualForwardGearCount) {
            val projectedRpm = coupledRpmForGear(candidate, profile)
            if (projectedRpm <= upshiftRpm) {
                break
            }

            candidate++
        }

        while (candidate > 1) {
            val projectedRpm = coupledRpmForGear(candidate, profile)
            if (projectedRpm >= downshiftRpm) {
                break
            }

            if (!downshiftAllowed(candidate - 1, dt)) {
                break
            }

            candidate--
        }

        return candidate
    }

    private fun clearLaunchProfileReturnState() {
        launchReturnArmed = false
        launchSixGearOverrideActive = false
        launchReturnPendingTargetGear = null
    }

    private fun immediatelySyncGearToConfiguredProfile() {
        clearLaunchProfileReturnState()

        val roadKmh = speedMetersPerSecond * 3.6
        if (roadKmh <= 0.0 || currentTransmissionPosition != TransmissionPosition.DRIVE) {
            gear = gear.coerceIn(0, virtualGearProfile.virtualForwardGearCount)
            return
        }

        val targetGear = computeProfileReturnTargetGear(virtualGearProfile, dt = 0.016)
        setGearImmediately(targetGear)

        landingRpmByGear.fill(0.0)
        val upshiftRpm = upshiftTriggerRpmForGear()
        for (indexedGear in 2..targetGear) {
            landingRpmByGear[indexedGear] = landingRpmAfterUpshift(
                fromGear = indexedGear - 1,
                upshiftRpm = upshiftRpm,
            )
        }

        if (shouldLockRpmToMappedRoadSpeed()) {
            rpm = coupledRpmForGear(gear)
            if (physics.engine.limiterRpm > 0.0) {
                rpm = rpm.coerceAtMost(physics.engine.limiterRpm)
            }
        }
    }

    private fun updateLaunchProfileReturn(rawThrottle: Double) {
        if (!launchReturnArmed) {
            return
        }

        if (rawThrottle < LaunchControl.THROTTLE_INTENT_THRESHOLD) {
            return
        }

        launchReturnArmed = false
        launchSixGearOverrideActive = false
        launchReturnPendingTargetGear = computeProfileReturnTargetGear(
            profile = virtualGearProfile,
            dt = 0.016,
        )
    }

    private fun ratioForGear(
        gear: Int,
        profile: VirtualGearProfile = effectiveVirtualGearProfile(),
    ): Double {
        if (gear in 1..profile.virtualForwardGearCount) {
            return profile.ratioForVirtualGear(gear)
        }

        return physics.drivetrain.ratioForGear(gear)
    }

    private fun effectiveVirtualGearProfile(): VirtualGearProfile {
        if (launchSixGearOverrideActive) {
            return launchSixGearProfile
        }

        return virtualGearProfile
    }

    private fun buildLaunchSixGearProfile(physics: AssettoPhysics): VirtualGearProfile {
        return VirtualGearProfile.from(physics, VirtualGearProfile.MIN_VIRTUAL_GEARS)
    }

    private fun clampGearToLaunchProfile() {
        val launchTopGear = launchSixGearProfile.virtualForwardGearCount
        if (gear > launchTopGear && !shifting) {
            setGearImmediately(launchTopGear)
        }
    }

    private fun updateLaunchSixGearOverride(rawThrottle: Double, brake: Double) {
        if (!sixGearOnLaunchEnabled) {
            return
        }

        if (
            launchControlPhase == LaunchControlPhase.LAUNCHED &&
            previousLaunchControlPhase != LaunchControlPhase.LAUNCHED
        ) {
            launchSixGearOverrideActive = true
            launchReturnArmed = false
            launchReturnPendingTargetGear = null
            clampGearToLaunchProfile()
        }

        if (!launchSixGearOverrideActive) {
            return
        }

        if (launchReturnArmed) {
            return
        }

        val throttleReleased = rawThrottle < LaunchControl.THROTTLE_INTENT_THRESHOLD
        val brakePressed = brake >= LaunchControl.ARM_BRAKE_THRESHOLD
        if (throttleReleased || brakePressed) {
            launchReturnArmed = true
        }
    }

    private fun resetLaunchControl() {
        launchControlPhase = LaunchControlPhase.INACTIVE
        previousLaunchControlPhase = LaunchControlPhase.INACTIVE
        launchControlJitterPhase = 0.0
        launchControlArmedElapsedSeconds = 0.0
        launchControlArmedStartRpm = physics.engine.idleRpm
        launchControlDisarmElapsedSeconds = 0.0
        launchControlTachCycleElapsedSeconds = 0.0
        launchControlTachCycleStartRpm = physics.engine.idleRpm
        clearLaunchProfileReturnState()
    }

    private fun updateLaunchControlPhase(
        rawThrottle: Double,
        brake: Double,
        enabled: Boolean,
    ) {
        val previousPhase = launchControlPhase
        previousLaunchControlPhase = previousPhase
        launchControlPhase = LaunchControl.advancePhase(
            phase = launchControlPhase,
            rawThrottle = rawThrottle,
            brake = brake,
            speedMps = speedMetersPerSecond,
            enabled = enabled,
        )
        if (previousPhase != launchControlPhase) {
            Log.d(
                "LaunchControl",
                "phase=$previousPhase->$launchControlPhase throttle=$rawThrottle brake=$brake " +
                    "speedMps=$speedMetersPerSecond rpm=$rpm enabled=$enabled",
            )
        }
        if (launchControlPhase == LaunchControlPhase.ARMED && previousPhase != LaunchControlPhase.ARMED) {
            launchControlJitterPhase = 0.0
            launchControlArmedElapsedSeconds = 0.0
            launchControlArmedStartRpm = rpm
            automaticTransmissionMode = AutomaticTransmissionMode.RACING
            racingReturnArmed = false
            racingStompPendingTargetGear = null
            clearCruisingReturnTransition()
        }
        if (launchControlPhase == LaunchControlPhase.DISARMING && previousPhase == LaunchControlPhase.ARMED) {
            launchControlDisarmElapsedSeconds = 0.0
        }
        if (launchControlPhase == LaunchControlPhase.LAUNCHED && previousPhase != LaunchControlPhase.LAUNCHED) {
            launchControlTachCycleElapsedSeconds = 0.0
            launchControlTachCycleStartRpm = rpm
        }
    }

    private fun updateLaunchControlRpm(dt: Double, rawThrottle: Double) {
        when (launchControlPhase) {
            LaunchControlPhase.INACTIVE -> Unit

            LaunchControlPhase.DISARMING -> {
                launchControlDisarmElapsedSeconds += dt
                val target = launchControlArmedStartRpm.coerceIn(physics.engine.idleRpm, physics.engine.limiterRpm)
                rpm = approachRpm(target, LaunchControl.DISARM_RESPONSE_SECONDS, dt)
                val settled = LaunchControl.disarmSettled(rpm, target)
                if (
                    settled &&
                    launchControlDisarmElapsedSeconds >= LaunchControl.DISARM_MIN_SECONDS
                ) {
                    launchControlPhase = LaunchControlPhase.INACTIVE
                    rpm = target
                } else if (launchControlDisarmElapsedSeconds >= LaunchControl.DISARM_MAX_SECONDS) {
                    launchControlPhase = LaunchControlPhase.INACTIVE
                    rpm = target
                }
            }

            LaunchControlPhase.ARMED -> {
                launchControlArmedElapsedSeconds += dt
                launchControlJitterPhase = launchControlJitterPhaseStep(dt, launchControlJitterPhase)
                val target = LaunchControl.armedTargetRpm(
                    armedElapsedSeconds = launchControlArmedElapsedSeconds,
                    jitterPhaseRadians = launchControlJitterPhase,
                    startRpm = launchControlArmedStartRpm,
                )
                // Launch control owns the RPM in this phase. Applying the target after the
                // authored torque integration mirrors the old replacement path and prevents the
                // natural torque from immediately undoing the staging clamp.
                rpm = target.coerceIn(physics.engine.idleRpm, physics.engine.limiterRpm)
            }

            LaunchControlPhase.LAUNCHED -> {
                val shiftActive = shifting
                if (shiftActive) {
                    // During a gear change the authored drivetrain ratio and the app's fixed
                    // clutch profile remain authoritative; the tach animation resumes in first.
                    val target = if (shiftDirection > 0) {
                        rpm.coerceAtMost(upshiftTriggerRpmForGear())
                    } else {
                        rpm.coerceAtMost(physics.engine.limiterRpm)
                    }
                    rpm = approachRpm(target, FIXED_UPSHIFT_SECONDS, dt)
                } else if (LaunchControl.shouldPlayLaunchTachAnimation(gear - 1, rawThrottle)) {
                    launchControlTachCycleElapsedSeconds += dt
                    val target = LaunchControl.launchedTachTargetRpm(
                        cycleElapsedSeconds = launchControlTachCycleElapsedSeconds,
                        redlineRpm = physics.engine.limiterRpm,
                        launchStartRpm = launchControlTachCycleStartRpm,
                    )
                    rpm = target.coerceIn(physics.engine.idleRpm, physics.engine.limiterRpm)
                } else {
                    val target = rpm.coerceIn(physics.engine.idleRpm, physics.engine.limiterRpm)
                    rpm = approachRpm(target, LaunchControl.LAUNCHED_ENGINE_BRAKE_RESPONSE_SECONDS, dt)
                }
            }
        }
    }

    private fun approachRpm(target: Double, responseSeconds: Double, dt: Double): Double {
        val response = responseSeconds.coerceAtLeast(0.001)
        val alpha = (1.0 - kotlin.math.exp(-dt / response)).coerceIn(0.0, 1.0)
        return (rpm + (target - rpm) * alpha).coerceIn(physics.engine.idleRpm, physics.engine.limiterRpm)
    }

    private val physicsGear: Int
        get() {
            if (shifting) {
                return 0
            }

            return gear
        }

    private fun engineTorque(dt: Double, controlsGas: Double, engineGas: Double): EngineTorqueFrame {
        val engine = physics.engine
        val backfire = backfireSettings
        // Automatic shifting briefly cuts engine gas. That cut is not a driver lift-off and must
        // never arm or trigger the global backfire policy.
        val shiftThrottleCut = shifting || automaticGasCutoff > 0.0 || engineCutoff > 0.0
        // The app override is intentionally limited to moving D above first gear. Neutral, P,
        // and first gear must remain free of override backfire pulses.
        val overrideBackfireAllowed = gear >= 2 ||
            (backfire.allowParkNeutralOverride && currentTransmissionPosition != TransmissionPosition.DRIVE)
        if (!overrideBackfireAllowed) {
            backfireArmed = false
            backfireReleaseTimer = 0.0
        }
        if (shiftThrottleCut) {
            // A transmission cut is not a driver release. Require a fresh throttle run after the
            // shift before the global override can arm again.
            backfireArmed = false
            backfireReleaseTimer = 0.0
            backfireSuppressedAfterShift = true
        }
        if (!shiftThrottleCut && controlsGas > 0.10) {
            backfireSuppressedAfterShift = false
        }
        val authoredBackfire = physics.engine.backfire
        if (useOriginalBackfire && !shiftThrottleCut && previousBackfireThrottle >= authoredBackfire.triggerGas && controlsGas < previousBackfireThrottle) {
            backfireReleaseTimer = min(10.0, backfireReleaseTimer + dt)
        }
        val naturalBankBackfire = useOriginalBackfire && !shiftThrottleCut &&
            previousBackfireThrottle >= authoredBackfire.triggerGas &&
            controlsGas > 0.0 && controlsGas < authoredBackfire.maximumGas &&
            rpm > authoredBackfire.minimumRpm && rpm <= authoredBackfire.maximumRpm &&
            backfireReleaseTimer > 1.0
        if (overrideBackfireAllowed && controlsGas >= backfire.armThrottle) {
            backfireArmed = true
            backfireReleaseTimer = 0.0
            backfireSuppressedAfterShift = false
        }
        if (!shiftThrottleCut && backfireArmed && controlsGas <= backfire.releaseThrottle) {
            backfireReleaseTimer = min(10.0, backfireReleaseTimer + dt)
        } else if (backfireArmed) {
            backfireReleaseTimer = 0.0
        }
        // This intentionally replaces the bank's per-car RPM/gas gates with one global policy:
        // a clear attempted run followed by a configurable lift-off delay is the user-facing
        // definition of backfire, independent of how a particular bank authored its thresholds.
        val triggerBackfire = overrideBackfireAllowed && !shiftThrottleCut && !backfireSuppressedAfterShift && backfireArmed &&
            controlsGas <= backfire.releaseThrottle &&
            rpm >= backfire.minimumRpm && rpm <= backfire.maximumRpm &&
            backfireReleaseTimer >= backfire.releaseDelaySeconds
        if (triggerBackfire) {
            backfireArmed = false
            backfireReleaseTimer = 0.0
        }
        previousBackfireThrottle = if (shiftThrottleCut) previousBackfireThrottle else controlsGas

        val mapped = interpolateAssettoCurve(engine.throttleCurve, engineGas)
        val limiterSteps = if (engine.limiterHz > 0.0) {
            (1_000.0 / engine.limiterHz).toInt() / 3
        } else {
            50
        }
        // The limiter gate is evaluated at the beginning of this fixed physics step, while the
        // engine is integrated below. A previous version intentionally exposed one overshoot
        // sample; that made manual mode visibly exceed the car's hard limiter and let an
        // automatic UP value above LIMITER start a shift too late. At the exact boundary we arm
        // the authored limiter pulse, then the integration result is clamped to the boundary.
        if (engine.limiterRpm > 0.0 && rpm >= engine.limiterRpm) limiterCounter = max(1, limiterSteps)
        val limiterActive = limiterCounter > 0
        if (limiterActive) limiterCounter -= 1
        val effective = if (limiterActive) 0.0 else mapped

        if (engine.turbos.isNotEmpty()) {
            var totalBoost = 0.0
            for (index in engine.turbos.indices) {
                val turbo = engine.turbos[index]
                val input = (effective * rpm / turbo.referenceRpm.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
                val target = input.pow(turbo.gamma)
                var q = turboQs[index]
                val lag = if (target > q) turbo.lagUp else turbo.lagDown
                q += (dt * lag).coerceIn(0.0, 1.0) * (target - q)
                if (turbo.wastegate > 0.0 && turbo.maximumBoost * q > turbo.wastegate) {
                    q = turbo.wastegate / turbo.maximumBoost.coerceAtLeast(0.001)
                }
                turboQs[index] = q
                totalBoost += turbo.maximumBoost * q
            }
            boost = totalBoost
            bov = if (boost * (1.0 - effective) > engine.turbos.first().bovThreshold) 1.0 else 0.0
            bovDecay = if (bov > 0.0) 0.0 else min(10.0, bovDecay + dt)
        }

        val power = interpolateAssettoCurve(engine.torqueCurve, rpm) * (1.0 + boost)
        val coast = coastTorque(rpm)
        var torque = coast + effective * (power - coast)
        if (rpm < engine.idleRpm) torque = max(torque, 15.0)
        engineTorqueResult.torque = torque
        engineTorqueResult.effectiveThrottle = effective
        engineTorqueResult.limiterActive = limiterActive
        // With the global policy disabled, the app only reports the lift-off edge to FMOD. The
        // bank's own backfire event, automation, and source randomisation then decide whether
        // anything is audible; no extracted sample or app threshold is imposed in this mode.
        engineTorqueResult.backfire = triggerBackfire || naturalBankBackfire
        engineTorqueResult.naturalBankBackfire = naturalBankBackfire
        return engineTorqueResult
    }

    private fun chooseBackfireSample(): Int {
        val allowed = backfireSettings.allowedSamples.sorted()
        if (allowed.isEmpty()) return -1
        val sample = allowed[nextBackfireSampleCursor % allowed.size]
        nextBackfireSampleCursor = (nextBackfireSampleCursor + 1) % allowed.size
        return sample
    }

    private fun coastTorque(rpm: Double): Double {
        val engine = physics.engine
        if (rpm <= engine.idleRpm) return 0.0
        val denominator = (1.0 - engine.coastNonLinearity) * engine.coastReferenceRpm - engine.idleRpm
        val linear = if (denominator == 0.0) 0.0 else -engine.coastReferenceTorque / denominator
        val nonlinearRpm = engine.coastNonLinearity * engine.coastReferenceRpm
        val quadratic = if (nonlinearRpm == 0.0) 0.0 else engine.coastReferenceTorque / nonlinearRpm.pow(2)
        val delta = rpm - engine.idleRpm
        return linear * delta - quadratic * delta * delta
    }

    private fun updateAeroForSpeed(speed: Double) {
        val vehicle = physics.drivetrain.vehicle
        val speedKmh = speed * 3.6
        val pressure = 0.5 * vehicle.airDensityKgM3 * speed * speed
        var dragArea = 0.0
        var liftArea = 0.0
        vehicle.aeroSurfaces.forEach { surface ->
            val angle = surface.angleDegrees + if (surface.controllerSpeedCurve.isEmpty()) {
                0.0
            } else {
                interpolateAssettoCurve(surface.controllerSpeedCurve, speedKmh)
            }
            val area = surface.chord * surface.span
            dragArea += area * surface.dragGain * interpolateAssettoCurve(surface.dragCurve, angle)
            liftArea += area * surface.liftGain * interpolateAssettoCurve(surface.liftCurve, angle)
        }
        aeroDrag = max(0.0, pressure * dragArea)
        downforce = max(0.0, pressure * liftArea)
    }

    private fun drivenAxle(vehicle: AssettoVehicleSpec): DrivenAxle = when {
        physics.drivetrain.traction.equals("FWD", ignoreCase = true) -> DrivenAxle(
            vehicle.frontWheelRadiusMeters.coerceAtLeast(1e-6),
            vehicle.frontWeightFraction,
            vehicle.frontGripCoefficient,
        )
        physics.drivetrain.traction.startsWith("AWD", ignoreCase = true) -> DrivenAxle(
            0.5 * (vehicle.frontWheelRadiusMeters + vehicle.rearWheelRadiusMeters),
            1.0,
            vehicle.frontWeightFraction * vehicle.frontGripCoefficient +
                (1.0 - vehicle.frontWeightFraction) * vehicle.rearGripCoefficient,
        )
        else -> DrivenAxle(
            vehicle.rearWheelRadiusMeters.coerceAtLeast(1e-6),
            1.0 - vehicle.frontWeightFraction,
            vehicle.rearGripCoefficient,
        )
    }

    private fun snapshot() = AssettoDrivetrainFrame(
        rpm = rpm,
        speedMetersPerSecond = speedMetersPerSecond,
        gear = gear,
        drivetrainSpeedRadiansPerSecond = 0.0,
        driverThrottle = 0.0,
        effectiveThrottle = 0.0,
        brake = 0.0,
        clutch = clutchSignal,
        boost = boost,
        bov = bov,
        bovDecaySeconds = bovDecay,
        limiterPulse = false,
        backfireTriggered = false,
        shiftStarted = false,
        shiftRejected = false,
        shifting = shifting,
        shiftDirection = shiftDirection,
        shiftProgress = 0.0,
        tractionLimitActive = false,
        tractionLimitPulse = false,
    )

    private class EngineTorqueFrame(
        var torque: Double = 0.0,
        var effectiveThrottle: Double = 0.0,
        var limiterActive: Boolean = false,
        var backfire: Boolean = false,
        var naturalBankBackfire: Boolean = false,
    )

    private data class DrivenAxle(val radius: Double, val normalFraction: Double, val grip: Double)

    private companion object {
        const val GRAVITY = 9.81
        const val STOPPED_CLUTCH_RELEASE_BRAKE = 0.2
        const val STOPPED_CLUTCH_RELEASE_SPEED_MPS = 1.0
        // A brief automatic-only gap keeps hard braking audible as separate shifts instead of
        // chaining two downshifts immediately after one another. Authored shift duration is kept.
        const val AUTOMATIC_DOWNSHIFT_CHAIN_COOLDOWN_SECONDS = 0.12
        const val FIXED_UPSHIFT_SECONDS = 0.10
        const val FIXED_DOWNSHIFT_SECONDS = 0.15
        /** Downshift duration used only during the racing-to-cruising return window. */
        const val CRUISING_RETURN_DOWNSHIFT_SECONDS = 1.0
        const val RPM_PER_RADIAN_SECOND = 60.0 / (2.0 * PI)
        const val RADIAN_SECONDS_PER_RPM = 1.0 / RPM_PER_RADIAN_SECOND
        const val SIMPLIFIED_DISENGAGED_CLUTCH = true
    }
}

private fun fixedClutchSequence(durationSeconds: Double): List<AssettoCurvePoint> {
    val duration = durationSeconds.coerceAtLeast(0.003)
    return listOf(
        AssettoCurvePoint(0.0, 1.0),
        AssettoCurvePoint((duration * 0.10).coerceAtLeast(0.003), 0.0),
        AssettoCurvePoint(duration * 0.60, 0.0),
        AssettoCurvePoint(duration, 1.0),
    )
}

private fun f32(value: Double): Double = value.toFloat().toDouble()

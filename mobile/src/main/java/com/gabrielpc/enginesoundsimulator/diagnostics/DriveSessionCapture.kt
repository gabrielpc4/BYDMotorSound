package com.gabrielpc.enginesoundsimulator.diagnostics

import android.content.Context
import com.gabrielpc.enginesoundsimulator.simulation.DrivetrainState
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Temporary high-rate file capture of pedals and automatic-transmission state.
 *
 * One row per physics step while armed, plus EVENT lines when the driver or the mode machine
 * changes something we need to replay later.
 */
internal class DriveSessionCapture(context: Context) {
    private val capturesDirectory = File(
        context.applicationContext.getExternalFilesDir(CAPTURES_DIR),
        "",
    )
    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var startedAtElapsedMs = 0L
    private var lastSample: Sample? = null
    private var samplesSinceFlush = 0
    var activeFile: File? = null
        private set

    val isCapturing: Boolean
        get() = synchronized(lock) { writer != null }

    fun start(): File {
        synchronized(lock) {
            writer?.close()
            capturesDirectory.mkdirs()
            val stamp = FILE_TIME.format(Date())
            val file = File(capturesDirectory, "capture-$stamp.tsv")
            val out = file.bufferedWriter()
            out.appendLine("# Drive session capture $stamp")
            out.appendLine("# ${HEADER}")
            writer = out
            activeFile = file
            startedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
            lastSample = null
            samplesSinceFlush = 0
            out.appendLine(eventLine(0L, "CAPTURE_START", "file=${file.name}"))
            out.flush()
            return file
        }
    }

    fun stop(): File? {
        synchronized(lock) {
            val file = activeFile
            val elapsed = elapsedMs()
            writer?.appendLine(eventLine(elapsed, "CAPTURE_STOP", "file=${file?.name}"))
            writer?.flush()
            writer?.close()
            writer = null
            return file
        }
    }

    fun record(throttle: Double, brake: Double, drivetrain: DrivetrainState) {
        synchronized(lock) {
            val out = writer ?: return
            val elapsed = elapsedMs()
            val sample = Sample.from(throttle, brake, drivetrain)
            val previous = lastSample
            if (previous != null) {
                emitEdges(out, elapsed, previous, sample)
            }
            out.appendLine(sampleLine(elapsed, sample))
            lastSample = sample
            samplesSinceFlush += 1
            if (samplesSinceFlush >= FLUSH_EVERY_SAMPLES) {
                out.flush()
                samplesSinceFlush = 0
            }
        }
    }

    private fun emitEdges(
        out: BufferedWriter,
        elapsed: Long,
        previous: Sample,
        sample: Sample,
    ) {
        if (abs(sample.throttle - previous.throttle) >= PEDAL_EDGE) {
            out.appendLine(
                eventLine(
                    elapsed,
                    "THROTTLE",
                    "${fmt(previous.throttle)}->${fmt(sample.throttle)}",
                ),
            )
        }
        if (abs(sample.brake - previous.brake) >= PEDAL_EDGE) {
            out.appendLine(
                eventLine(elapsed, "BRAKE", "${fmt(previous.brake)}->${fmt(sample.brake)}"),
            )
        }
        if (sample.gear != previous.gear) {
            out.appendLine(eventLine(elapsed, "GEAR", "${previous.gear}->${sample.gear}"))
        }
        if (sample.mode != previous.mode) {
            out.appendLine(eventLine(elapsed, "MODE", "${previous.mode}->${sample.mode}"))
        }
        if (sample.armed != previous.armed) {
            out.appendLine(eventLine(elapsed, "P_CRUISING", "${previous.armed}->${sample.armed}"))
        }
        if (sample.returnActive != previous.returnActive) {
            out.appendLine(
                eventLine(elapsed, "RETURN", "${previous.returnActive}->${sample.returnActive}"),
            )
        }
        if (sample.shifting && !previous.shifting) {
            out.appendLine(eventLine(elapsed, "SHIFT_START", sample.shiftDirection))
        }
        if (sample.returnFinished) {
            out.appendLine(eventLine(elapsed, "RETURN_FINISHED", "gear=${sample.gear} rpm=${fmt(sample.rpm)}"))
        }
        if (sample.launchPhase != previous.launchPhase) {
            out.appendLine(eventLine(elapsed, "LAUNCH", "${previous.launchPhase}->${sample.launchPhase}"))
        }
        if (sample.requestUpshift && !previous.requestUpshift) {
            out.appendLine(eventLine(elapsed, "RETURN_UPSHIFT", "target=${sample.targetGear}"))
        }
    }

    private fun elapsedMs(): Long {
        return android.os.SystemClock.elapsedRealtime() - startedAtElapsedMs
    }

    private fun sampleLine(elapsed: Long, sample: Sample): String {
        return listOf(
            elapsed.toString(),
            "SAMPLE",
            fmt(sample.throttle),
            fmt(sample.brake),
            fmt(sample.rpm),
            sample.gear.toString(),
            if (sample.shifting) "1" else "0",
            sample.shiftDirection,
            sample.mode,
            if (sample.armed) "1" else "0",
            if (sample.returnActive) "1" else "0",
            sample.targetGear.toString(),
            sample.computedTargetGear.toString(),
            fmt(sample.chaseRpm),
            fmt(sample.liveTargetRpm),
            fmt(sample.coupledCurrent),
            fmt(sample.nextGearCoupled),
            fmt(sample.glideRpmPerSecond),
            fmt(sample.upshiftRpm),
            fmt(sample.vehicleKmh),
            fmt(sample.fmodKmh),
            sample.launchPhase,
            if (sample.requestUpshift) "1" else "0",
            if (sample.returnFinished) "1" else "0",
        ).joinToString("\t")
    }

    private fun eventLine(elapsed: Long, name: String, detail: String): String {
        return "$elapsed\tEVENT\t$name\t$detail"
    }

    private data class Sample(
        val throttle: Double,
        val brake: Double,
        val rpm: Double,
        val gear: Int,
        val shifting: Boolean,
        val shiftDirection: String,
        val mode: String,
        val armed: Boolean,
        val returnActive: Boolean,
        val targetGear: Int,
        val computedTargetGear: Int,
        val chaseRpm: Double,
        val liveTargetRpm: Double,
        val coupledCurrent: Double,
        val nextGearCoupled: Double,
        val glideRpmPerSecond: Double,
        val upshiftRpm: Double,
        val vehicleKmh: Double,
        val fmodKmh: Double,
        val launchPhase: String,
        val requestUpshift: Boolean,
        val returnFinished: Boolean,
    ) {
        companion object {
            fun from(throttle: Double, brake: Double, drive: DrivetrainState): Sample {
                return Sample(
                    throttle = throttle,
                    brake = brake,
                    rpm = drive.rpm,
                    gear = drive.gear,
                    shifting = drive.isShifting,
                    shiftDirection = drive.shiftDirection.name,
                    mode = drive.automaticTransmissionMode.name,
                    armed = drive.racingReturnArmed,
                    returnActive = drive.cruisingReturnActive,
                    targetGear = drive.cruisingReturnTargetGear,
                    computedTargetGear = drive.cruisingReturnComputedTargetGear,
                    chaseRpm = drive.cruisingReturnChaseRpm,
                    liveTargetRpm = drive.cruisingReturnLiveTargetRpm,
                    coupledCurrent = drive.coupledRpmCurrentGear,
                    nextGearCoupled = drive.cruisingReturnNextGearCoupledRpm,
                    glideRpmPerSecond = drive.cruisingReturnGlideRpmPerSecond,
                    upshiftRpm = drive.effectiveAutomaticUpshiftRpm,
                    vehicleKmh = drive.realOrDocumentedRawSpeedKmh,
                    fmodKmh = drive.fmodDrivetrainSpeedKmh,
                    launchPhase = drive.launchControlPhaseName,
                    requestUpshift = drive.cruisingReturnRequestUpshift,
                    returnFinished = drive.cruisingReturnFinishedThisStep,
                )
            }
        }
    }

    private companion object {
        const val CAPTURES_DIR = "captures"
        const val PEDAL_EDGE = 0.02
        const val FLUSH_EVERY_SAMPLES = 15
        const val HEADER =
            "t_ms\tkind\tthrottle\tbrake\trpm\tgear\tshifting\tshiftDir\tmode\tarmed\treturn\t" +
                "tgtGear\tcomputedGear\tchaseRpm\tliveTarget\tcoupledNow\tnextCoupled\tglideRps\t" +
                "upshiftRpm\tvehKmh\tfmodKmh\tlaunchPhase\treqUp\tfinished"

        val FILE_TIME = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

        fun fmt(value: Double): String {
            return String.format(Locale.US, "%.2f", value)
        }
    }
}

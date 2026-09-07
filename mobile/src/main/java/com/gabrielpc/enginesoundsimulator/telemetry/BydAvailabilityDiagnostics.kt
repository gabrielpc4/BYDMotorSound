package com.gabrielpc.enginesoundsimulator.telemetry

/**
 * Real-pedal mode is useful only when one coherent vehicle frame is available. A valid
 * accelerator/brake pair without a valid speed would make the drivetrain silently switch back to
 * its own synthetic speed integration, which is not what the real car reports. Requiring all
 * three signals keeps REAL and SIMULATED selection explicit and prevents that mixed-frame path.
 *
 * SIMULATED pedal mode can still blend any individually valid accelerator or brake sample on top
 * of touch input while keeping the Seal road-speed model authoritative.
 */
fun TelemetrySnapshot.vehicleDriveSignalsAvailable(): Boolean =
    readerState == ReaderState.ACTIVE && accelerator.isValid && brake.isValid && speed.isValid

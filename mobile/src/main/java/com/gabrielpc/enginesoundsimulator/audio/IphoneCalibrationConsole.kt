package com.gabrielpc.enginesoundsimulator.audio

import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal enum class IphoneCalibrationLogLevel {
    INFO,
    OK,
    WARN,
    ERROR,
    ;

    val prefix: String
        get() = when (this) {
            INFO -> "INFO"
            OK -> " OK "
            WARN -> "WARN"
            ERROR -> " ERR"
        }
}

internal typealias IphoneCalibrationLogger = (IphoneCalibrationLogLevel, String) -> Unit

internal class IphoneCalibrationConsole(
    private val maxLines: Int = 300,
) {
    private val lines = ArrayDeque<String>()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun append(level: IphoneCalibrationLogLevel, message: String) {
        synchronized(lines) {
            val timestamp = LocalTime.now().format(timeFormatter)
            lines.addLast("[$timestamp] ${level.prefix}  $message")
            while (lines.size > maxLines) {
                lines.removeFirst()
            }
        }
    }

    fun clear() {
        synchronized(lines) {
            lines.clear()
        }
    }

    fun snapshot(): List<String> {
        synchronized(lines) {
            return lines.toList()
        }
    }
}

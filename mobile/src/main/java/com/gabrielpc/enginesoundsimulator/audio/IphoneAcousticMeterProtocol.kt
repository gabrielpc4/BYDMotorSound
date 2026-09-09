package com.gabrielpc.enginesoundsimulator.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max

internal object IphoneAcousticMeterProtocol {
    const val VERSION: Byte = 2
    val SERVICE_UUID: UUID = UUID.fromString("88d9e8d0-540a-4a1a-aad8-e8732d7ebf01")
    val COMMAND_UUID: UUID = UUID.fromString("88d9e8d1-540a-4a1a-aad8-e8732d7ebf01")
    val EVENT_UUID: UUID = UUID.fromString("88d9e8d2-540a-4a1a-aad8-e8732d7ebf01")

    enum class Type(val id: Byte) {
        HELLO(1),
        HELLO_RESPONSE(2),
        PAIR(3),
        PAIR_RESULT(4),
        AUTH(5),
        AUTH_RESULT(6),
        CLOCK_PING(7),
        CLOCK_PONG(8),
        ARM_MEASUREMENT(9),
        ARMED(10),
        RESULT(11),
        CANCEL(12),
        ERROR(13),
        ARM_LATENCY(14),
        LATENCY_RESULT(15),
        STOP(16);

        companion object {
            fun fromId(id: Byte): Type? = entries.firstOrNull { it.id == id }
        }
    }

    sealed interface Message {
        val sequence: Int
    }

    data class Hello(override val sequence: Int) : Message

    data class HelloResponse(
        override val sequence: Int,
        val meterInstanceId: UUID,
        val meterModel: String,
        val hasPairingToken: Boolean,
        val authenticationChallenge: ByteArray,
    ) : Message

    data class PairRequest(override val sequence: Int, val code: Int) : Message

    data class PairResult(
        override val sequence: Int,
        val accepted: Boolean,
        val token: ByteArray,
    ) : Message

    data class AuthenticationRequest(
        override val sequence: Int,
        val challenge: ByteArray,
        val hmac: ByteArray,
    ) : Message

    data class AuthenticationResult(override val sequence: Int, val accepted: Boolean) : Message

    data class ClockPing(override val sequence: Int, val androidSendNanos: Long) : Message

    data class ClockPong(
        override val sequence: Int,
        val androidSendNanos: Long,
        val iphoneReceiveNanos: Long,
        val iphoneSendNanos: Long,
    ) : Message

    data class ArmMeasurement(
        override val sequence: Int,
        val measurementId: Long,
        val ambientBeforeStartNanos: Long,
        val engineStartNanos: Long,
        val sweepStartNanos: Long,
        val sweepEndNanos: Long,
        val engineEndNanos: Long,
        val ambientAfterEndNanos: Long,
        val carName: String,
        val perspective: EngineSoundPerspective,
    ) : Message

    data class Armed(override val sequence: Int, val measurementId: Long) : Message

    data class MeasurementResult(
        override val sequence: Int,
        val measurementId: Long,
        val metrics: AcousticMeasurementMetrics,
    ) : Message

    data class ArmLatency(
        override val sequence: Int,
        val measurementId: Long,
        val chirpTimesNanos: LongArray,
    ) : Message

    data class LatencyResult(
        override val sequence: Int,
        val measurementId: Long,
        val detectedTimesNanos: LongArray,
        val confidence: Double,
    ) : Message

    data class Cancel(override val sequence: Int) : Message

    data class Stop(override val sequence: Int) : Message

    data class Error(override val sequence: Int, val message: String) : Message

    fun encode(message: Message): ByteArray {
        val payload = ByteBuffer.allocate(MAX_FRAME_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        payload.put(VERSION)
        payload.put(typeOf(message).id)
        payload.putShort(message.sequence.toShort())
        when (message) {
            is Hello, is Cancel, is Stop -> Unit
            is HelloResponse -> {
                payload.putUuid(message.meterInstanceId)
                payload.putSizedString(message.meterModel)
                payload.put(if (message.hasPairingToken) 1 else 0)
                payload.putFixed(message.authenticationChallenge, CHALLENGE_SIZE)
            }
            is PairRequest -> payload.putInt(message.code)
            is PairResult -> {
                payload.put(if (message.accepted) 1 else 0)
                payload.putFixed(message.token, TOKEN_SIZE)
            }
            is AuthenticationRequest -> {
                payload.putFixed(message.challenge, CHALLENGE_SIZE)
                payload.putFixed(message.hmac, HMAC_SIZE)
            }
            is AuthenticationResult -> payload.put(if (message.accepted) 1 else 0)
            is ClockPing -> payload.putLong(message.androidSendNanos)
            is ClockPong -> {
                payload.putLong(message.androidSendNanos)
                payload.putLong(message.iphoneReceiveNanos)
                payload.putLong(message.iphoneSendNanos)
            }
            is ArmMeasurement -> {
                payload.putLong(message.measurementId)
                payload.putLong(message.ambientBeforeStartNanos)
                payload.putLong(message.engineStartNanos)
                payload.putLong(message.sweepStartNanos)
                payload.putLong(message.sweepEndNanos)
                payload.putLong(message.engineEndNanos)
                payload.putLong(message.ambientAfterEndNanos)
                payload.putSizedString(message.carName)
                payload.put(message.perspective.ordinal.toByte())
            }
            is Armed -> payload.putLong(message.measurementId)
            is MeasurementResult -> {
                payload.putLong(message.measurementId)
                with(message.metrics) {
                    payload.putDouble(weightedEngineEnergy)
                    payload.putDouble(lowBandEngineEnergy)
                    payload.putDouble(presenceBandEngineEnergy)
                    payload.putDouble(weightedAmbientBeforeEnergy)
                    payload.putDouble(weightedAmbientAfterEnergy)
                    payload.putDouble(lowBandAmbientBeforeEnergy)
                    payload.putDouble(lowBandAmbientAfterEnergy)
                    payload.putDouble(presenceBandAmbientBeforeEnergy)
                    payload.putDouble(presenceBandAmbientAfterEnergy)
                    payload.putDouble(peakLinear)
                    payload.putLong(sampleCount)
                    payload.putDouble(sampleRateHz)
                    payload.putInt(channelCount)
                    payload.putDouble(channelBalanceDb ?: Double.NaN)
                    payload.putInt((if (routeValid) 1 else 0) or (if (windowValid) 2 else 0))
                }
            }
            is ArmLatency -> {
                require(message.chirpTimesNanos.size == CHIRP_COUNT)
                payload.putLong(message.measurementId)
                message.chirpTimesNanos.forEach(payload::putLong)
            }
            is LatencyResult -> {
                require(message.detectedTimesNanos.size == CHIRP_COUNT)
                payload.putLong(message.measurementId)
                message.detectedTimesNanos.forEach(payload::putLong)
                payload.putDouble(message.confidence)
            }
            is Error -> payload.putSizedString(message.message)
        }

        return payload.array().copyOf(payload.position())
    }

    fun decode(bytes: ByteArray): Message? = runCatching {
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (input.get() != VERSION) return null
        val type = Type.fromId(input.get()) ?: return null
        val sequence = input.short.toInt() and 0xffff
        when (type) {
            Type.HELLO -> Hello(sequence)
            Type.HELLO_RESPONSE -> HelloResponse(
                sequence = sequence,
                meterInstanceId = input.uuid(),
                meterModel = input.sizedString(),
                hasPairingToken = input.get().toInt() != 0,
                authenticationChallenge = input.fixed(CHALLENGE_SIZE),
            )
            Type.PAIR -> PairRequest(sequence, input.int)
            Type.PAIR_RESULT -> PairResult(sequence, input.get().toInt() != 0, input.fixed(TOKEN_SIZE))
            Type.AUTH -> AuthenticationRequest(sequence, input.fixed(CHALLENGE_SIZE), input.fixed(HMAC_SIZE))
            Type.AUTH_RESULT -> AuthenticationResult(sequence, input.get().toInt() != 0)
            Type.CLOCK_PING -> ClockPing(sequence, input.long)
            Type.CLOCK_PONG -> ClockPong(sequence, input.long, input.long, input.long)
            Type.ARM_MEASUREMENT -> ArmMeasurement(
                sequence = sequence,
                measurementId = input.long,
                ambientBeforeStartNanos = input.long,
                engineStartNanos = input.long,
                sweepStartNanos = input.long,
                sweepEndNanos = input.long,
                engineEndNanos = input.long,
                ambientAfterEndNanos = input.long,
                carName = input.sizedString(),
                perspective = EngineSoundPerspective.entries.getOrElse(input.get().toInt()) {
                    EngineSoundPerspective.CABIN
                },
            )
            Type.ARMED -> Armed(sequence, input.long)
            Type.RESULT -> {
                val id = input.long
                val values = DoubleArray(10) { input.double }
                val samples = input.long
                val sampleRate = input.double
                val channelCount = input.int
                val channelBalance = input.double
                val flags = input.int
                MeasurementResult(
                    sequence = sequence,
                    measurementId = id,
                    metrics = AcousticMeasurementMetrics(
                        weightedEngineEnergy = values[0],
                        lowBandEngineEnergy = values[1],
                        presenceBandEngineEnergy = values[2],
                        weightedAmbientBeforeEnergy = values[3],
                        weightedAmbientAfterEnergy = values[4],
                        lowBandAmbientBeforeEnergy = values[5],
                        lowBandAmbientAfterEnergy = values[6],
                        presenceBandAmbientBeforeEnergy = values[7],
                        presenceBandAmbientAfterEnergy = values[8],
                        peakLinear = values[9],
                        sampleCount = samples,
                        sampleRateHz = sampleRate,
                        channelCount = channelCount,
                        channelBalanceDb = channelBalance.takeIf(Double::isFinite),
                        routeValid = flags and 1 != 0,
                        windowValid = flags and 2 != 0,
                    ),
                )
            }
            Type.CANCEL -> Cancel(sequence)
            Type.ERROR -> Error(sequence, input.sizedString())
            Type.ARM_LATENCY -> ArmLatency(
                sequence,
                input.long,
                LongArray(CHIRP_COUNT) { input.long },
            )
            Type.LATENCY_RESULT -> LatencyResult(
                sequence,
                input.long,
                LongArray(CHIRP_COUNT) { input.long },
                input.double,
            )
            Type.STOP -> Stop(sequence)
        }
    }.getOrNull()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(SecureRandom()::nextBytes)

    fun hmac(token: ByteArray, challenge: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token, "HmacSHA256"))

        return mac.doFinal(challenge)
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun hexBytes(value: String): ByteArray? = runCatching {
        require(value.length % 2 == 0)
        ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }.getOrNull()

    private fun typeOf(message: Message): Type = when (message) {
        is Hello -> Type.HELLO
        is HelloResponse -> Type.HELLO_RESPONSE
        is PairRequest -> Type.PAIR
        is PairResult -> Type.PAIR_RESULT
        is AuthenticationRequest -> Type.AUTH
        is AuthenticationResult -> Type.AUTH_RESULT
        is ClockPing -> Type.CLOCK_PING
        is ClockPong -> Type.CLOCK_PONG
        is ArmMeasurement -> Type.ARM_MEASUREMENT
        is Armed -> Type.ARMED
        is MeasurementResult -> Type.RESULT
        is Cancel -> Type.CANCEL
        is Error -> Type.ERROR
        is ArmLatency -> Type.ARM_LATENCY
        is LatencyResult -> Type.LATENCY_RESULT
        is Stop -> Type.STOP
    }

    private fun ByteBuffer.putUuid(value: UUID) {
        putUuidHalf(value.mostSignificantBits)
        putUuidHalf(value.leastSignificantBits)
    }

    private fun ByteBuffer.uuid(): UUID = UUID(uuidHalf(), uuidHalf())

    private fun ByteBuffer.putUuidHalf(value: Long) {
        for (shift in 56 downTo 0 step 8) {
            put((value ushr shift).toByte())
        }
    }

    private fun ByteBuffer.uuidHalf(): Long {
        var value = 0L
        repeat(Long.SIZE_BYTES) {
            value = (value shl 8) or (get().toLong() and 0xffL)
        }

        return value
    }

    private fun ByteBuffer.putSizedString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8).take(MAX_STRING_SIZE).toByteArray()
        put(bytes.size.toByte())
        put(bytes)
    }

    private fun ByteBuffer.sizedString(): String {
        val size = get().toInt() and 0xff

        return fixed(size).toString(Charsets.UTF_8)
    }

    private fun ByteBuffer.putFixed(value: ByteArray, requiredSize: Int) {
        require(value.size == requiredSize)
        put(value)
    }

    private fun ByteBuffer.fixed(size: Int): ByteArray = ByteArray(size).also(::get)

    const val CHALLENGE_SIZE = 16
    const val TOKEN_SIZE = 32
    const val HMAC_SIZE = 32
    const val CHIRP_COUNT = 3
    private const val MAX_STRING_SIZE = 80
    private const val MAX_FRAME_SIZE = 256
}

internal data class BleClockSample(
    val androidSendNanos: Long,
    val iphoneReceiveNanos: Long,
    val iphoneSendNanos: Long,
    val androidReceiveNanos: Long,
) {
    val roundTripNanos: Long
        get() = max(0L, (androidReceiveNanos - androidSendNanos) - (iphoneSendNanos - iphoneReceiveNanos))

    val iphoneMinusAndroidNanos: Long
        get() = ((iphoneReceiveNanos - androidSendNanos) + (iphoneSendNanos - androidReceiveNanos)) / 2L
}

internal data class BleClockAlignment(
    val iphoneMinusAndroidNanos: Long,
    val uncertaintyNanos: Long,
) {
    val valid: Boolean get() = uncertaintyNanos <= MAX_UNCERTAINTY_NANOS

    fun iphoneTime(androidTimeNanos: Long): Long = androidTimeNanos + iphoneMinusAndroidNanos

    companion object {
        const val MAX_UNCERTAINTY_NANOS = 20_000_000L
    }
}

internal object BleClockSynchronizer {
    const val EXCHANGE_COUNT = 7

    fun align(samples: List<BleClockSample>): BleClockAlignment? {
        if (samples.size < EXCHANGE_COUNT) return null
        val selected = samples.sortedBy(BleClockSample::roundTripNanos).take(3)
        val offsets = selected.map(BleClockSample::iphoneMinusAndroidNanos).sorted()

        return BleClockAlignment(
            iphoneMinusAndroidNanos = offsets[offsets.size / 2],
            uncertaintyNanos = selected.maxOf(BleClockSample::roundTripNanos) / 2L,
        )
    }
}

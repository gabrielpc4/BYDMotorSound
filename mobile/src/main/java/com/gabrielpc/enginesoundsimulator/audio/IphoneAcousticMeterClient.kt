package com.gabrielpc.enginesoundsimulator.audio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal data class ConnectedIphoneMeter(
    val instanceId: String,
    val model: String,
)

internal data class AcousticLatencyMeasurement(
    val latencyNanos: Long,
    val confidence: Double,
)

@SuppressLint("MissingPermission")
internal class IphoneAcousticMeterClient(
    context: Context,
    private val repository: IphoneAcousticMeterRepository,
    private val cancellationRequested: AtomicBoolean,
    private val onLog: IphoneCalibrationLogger? = null,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val connectionState = AtomicInteger(BluetoothProfile.STATE_DISCONNECTED)
    private val ready = AtomicBoolean(false)
    private val closing = AtomicBoolean(false)
    private val writeCompleted = LinkedBlockingQueue<Boolean>()
    private val messages = LinkedBlockingQueue<IphoneAcousticMeterProtocol.Message>()
    private val reassembler = BleFragmentReassembler()
    private val nextSequence = AtomicInteger(1)
    private val nextFragmentId = AtomicInteger(1)
    private val failure = AtomicReference<String?>(null)

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var commandCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var negotiatedMtu = DEFAULT_MTU
    @Volatile private var connectedAddress = ""

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            connectionState.set(newState)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failure.compareAndSet(null, "Bluetooth GATT error $status.")
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!gatt.requestMtu(PREFERRED_MTU)) gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                ready.set(false)
                if (!closing.get()) failure.compareAndSet(null, "The iPhone meter disconnected.")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failure.compareAndSet(null, "Could not discover the iPhone meter service.")
                return
            }
            val service: BluetoothGattService = gatt.getService(IphoneAcousticMeterProtocol.SERVICE_UUID)
                ?: run {
                    failure.compareAndSet(null, "The selected device is not running Engine Loudness Meter.")
                    return
                }
            commandCharacteristic = service.getCharacteristic(IphoneAcousticMeterProtocol.COMMAND_UUID)
            val event = service.getCharacteristic(IphoneAcousticMeterProtocol.EVENT_UUID)
            if (commandCharacteristic == null || event == null) {
                failure.compareAndSet(null, "The iPhone meter protocol is incomplete.")
                return
            }
            gatt.setCharacteristicNotification(event, true)
            val descriptor = event.getDescriptor(CLIENT_CONFIGURATION_UUID)
                ?: run {
                    failure.compareAndSet(null, "The iPhone notification descriptor is missing.")
                    return
                }
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (!gatt.writeDescriptor(descriptor)) {
                failure.compareAndSet(null, "Could not enable iPhone meter notifications.")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                ready.set(true)
            } else {
                failure.compareAndSet(null, "Could not enable iPhone meter notifications ($status).")
            }
        }

        @Deprecated("Used on Android 12 and older")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            receiveFragment(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            receiveFragment(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            writeCompleted.offer(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    fun probeNearbyMeter(): Boolean {
        logBlePrerequisites()
        log(
            IphoneCalibrationLogLevel.INFO,
            "Probing for iPhone meter (up to ${SCAN_TIMEOUT_SECONDS}s). Open Engine Loudness Meter on the phone.",
        )
        return runCatching {
            val device = scanForMeter()
            log(IphoneCalibrationLogLevel.OK, "Probe found iPhone meter at ${device.address}.")
            true
        }.getOrElse { error ->
            log(
                IphoneCalibrationLogLevel.WARN,
                error.message ?: "Probe did not find an iPhone meter.",
            )
            false
        }
    }

    fun connectAndAuthenticate(deviceAddress: String?, pairingCode: String?): ConnectedIphoneMeter {
        logBlePrerequisites()
        log(IphoneCalibrationLogLevel.INFO, "Connecting to iPhone meter…")
        val device = resolveDevice(deviceAddress)
        connectedAddress = device.address
        log(IphoneCalibrationLogLevel.INFO, "Opening GATT to ${device.address}.")
        gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        waitUntil(READY_TIMEOUT_SECONDS) { ready.get() }
        failure.get()?.let(::error)
        if (!ready.get()) error("Timed out while connecting to the iPhone meter.")
        log(IphoneCalibrationLogLevel.OK, "GATT ready (MTU $negotiatedMtu).")

        val hello = request(IphoneAcousticMeterProtocol.Hello(sequence())) as? IphoneAcousticMeterProtocol.HelloResponse
            ?: error("The iPhone did not answer the protocol handshake.")
        log(IphoneCalibrationLogLevel.OK, "Handshake OK: ${hello.meterModel}.")
        val savedLink = repository.loadLink()
        val existing = savedLink?.takeIf {
            hello.hasPairingToken && it.meterInstanceId == hello.meterInstanceId.toString()
        }
        if (savedLink != null && existing == null) repository.clear()
        val token = if (existing != null) {
            IphoneAcousticMeterProtocol.hexBytes(existing.tokenHex)
                ?: run {
                    repository.clear()
                    error("The saved iPhone link is corrupt. Pair again.")
                }
        } else {
            log(IphoneCalibrationLogLevel.INFO, "Pairing with six-digit code.")
            val code = pairingCode?.filter(Char::isDigit)?.toIntOrNull()
                ?: error("Enter the six-digit code shown on the iPhone.")
            val paired = request(IphoneAcousticMeterProtocol.PairRequest(sequence(), code))
                as? IphoneAcousticMeterProtocol.PairResult
                ?: error("The iPhone did not answer the pairing request.")
            if (!paired.accepted) error("The iPhone rejected the pairing code.")
            paired.token
        }
        val auth = IphoneAcousticMeterProtocol.AuthenticationRequest(
            sequence = sequence(),
            challenge = hello.authenticationChallenge,
            hmac = IphoneAcousticMeterProtocol.hmac(token, hello.authenticationChallenge),
        )
        val authenticated = request(auth) as? IphoneAcousticMeterProtocol.AuthenticationResult
            ?: error("The iPhone did not answer authentication.")
        if (!authenticated.accepted) {
            repository.clear()
            error("The iPhone authentication token was rejected. Pair again.")
        }
        repository.saveLink(
            AcousticMeterLink(
                meterInstanceId = hello.meterInstanceId.toString(),
                meterName = hello.meterModel,
                deviceAddress = connectedAddress,
                tokenHex = IphoneAcousticMeterProtocol.hex(token),
            ),
        )
        log(IphoneCalibrationLogLevel.OK, "iPhone link saved for ${hello.meterModel}.")

        return ConnectedIphoneMeter(hello.meterInstanceId.toString(), hello.meterModel)
    }

    fun synchronizeClocks(): BleClockAlignment {
        log(IphoneCalibrationLogLevel.INFO, "Synchronizing BLE clocks…")
        var lastAlignment: BleClockAlignment? = null
        repeat(CLOCK_SYNC_ATTEMPTS) { attempt ->
            checkCancelled()
            val samples = mutableListOf<BleClockSample>()
            repeat(BleClockSynchronizer.EXCHANGE_COUNT) {
                checkCancelled()
                val sendNanos = SystemClock.elapsedRealtimeNanos()
                val response = request(IphoneAcousticMeterProtocol.ClockPing(sequence(), sendNanos))
                    as? IphoneAcousticMeterProtocol.ClockPong
                    ?: error("The iPhone did not answer clock synchronization.")
                samples += BleClockSample(
                    androidSendNanos = response.androidSendNanos,
                    iphoneReceiveNanos = response.iphoneReceiveNanos,
                    iphoneSendNanos = response.iphoneSendNanos,
                    androidReceiveNanos = SystemClock.elapsedRealtimeNanos(),
                )
            }
            val alignment = BleClockSynchronizer.align(samples)
                ?: error("Not enough clock samples were received from the iPhone.")
            lastAlignment = alignment
            val uncertaintyMs = alignment.uncertaintyNanos / 1e6
            if (alignment.valid) {
                log(
                    IphoneCalibrationLogLevel.OK,
                    "Clock sync OK on attempt ${attempt + 1}: uncertainty %.1f ms.".format(uncertaintyMs),
                )
                return alignment
            }
            log(
                IphoneCalibrationLogLevel.WARN,
                "Clock sync attempt ${attempt + 1} uncertainty %.1f ms (max %.0f ms).".format(
                    uncertaintyMs,
                    BleClockAlignment.MAX_UNCERTAINTY_NANOS / 1e6,
                ),
            )
        }
        val uncertaintyMs = requireNotNull(lastAlignment).uncertaintyNanos / 1e6
        error(
            "Bluetooth clock uncertainty is %.1f ms; maximum is %.0f ms.".format(
                uncertaintyMs,
                BleClockAlignment.MAX_UNCERTAINTY_NANOS / 1e6,
            ),
        )
    }

    fun armMeasurement(request: IphoneAcousticMeterProtocol.ArmMeasurement) {
        val response = request(request) as? IphoneAcousticMeterProtocol.Armed
            ?: error("The iPhone did not arm the measurement window.")
        if (response.measurementId != request.measurementId) error("The iPhone armed a different measurement.")
    }

    fun awaitMeasurement(measurementId: Long, timeoutSeconds: Long): AcousticMeasurementMetrics {
        val result = awaitMessage(timeoutSeconds) { message ->
            message is IphoneAcousticMeterProtocol.MeasurementResult && message.measurementId == measurementId
        } as? IphoneAcousticMeterProtocol.MeasurementResult
            ?: error("The iPhone did not return measurement $measurementId.")

        return result.metrics
    }

    fun armLatency(
        request: IphoneAcousticMeterProtocol.ArmLatency,
    ) {
        val response = request(request) as? IphoneAcousticMeterProtocol.Armed
            ?: error("The iPhone did not arm acoustic-latency capture.")
        if (response.measurementId != request.measurementId) error("The iPhone armed a different latency capture.")
    }

    fun awaitLatency(measurementId: Long): IphoneAcousticMeterProtocol.LatencyResult {
        return awaitMessage(LATENCY_TIMEOUT_SECONDS) { message ->
            message is IphoneAcousticMeterProtocol.LatencyResult && message.measurementId == measurementId
        } as? IphoneAcousticMeterProtocol.LatencyResult
            ?: error("The iPhone did not return acoustic latency.")
    }

    fun cancelRemote() {
        val wasInterrupted = Thread.interrupted()
        runCatching {
            send(
                IphoneAcousticMeterProtocol.Cancel(sequence()),
                ignoreCancellation = true,
            )
        }
        if (wasInterrupted) Thread.currentThread().interrupt()
    }

    fun ensureConnected() {
        checkCancelled()
        failure.get()?.let(::error)
        if (!ready.get()) error("The iPhone meter disconnected.")
    }

    override fun close() {
        closing.set(true)
        runCatching { send(IphoneAcousticMeterProtocol.Stop(sequence())) }
        ready.set(false)
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        commandCharacteristic = null
    }

    private fun request(message: IphoneAcousticMeterProtocol.Message): IphoneAcousticMeterProtocol.Message {
        send(message)

        return awaitMessage(REQUEST_TIMEOUT_SECONDS) { it.sequence == message.sequence }
            ?: error("The iPhone did not answer ${message.javaClass.simpleName}.")
    }

    private fun send(
        message: IphoneAcousticMeterProtocol.Message,
        ignoreCancellation: Boolean = false,
    ) {
        if (!ignoreCancellation) checkCancelled()
        val encoded = IphoneAcousticMeterProtocol.encode(message)
        val fragments = BleFragmentCodec.fragment(
            payload = encoded,
            messageId = nextFragmentId.getAndUpdate { if (it == 0xffff) 1 else it + 1 },
            maximumPacketSize = (negotiatedMtu - ATT_HEADER_SIZE).coerceAtLeast(MINIMUM_PACKET_SIZE),
        )
        fragments.forEach(::writeFragment)
    }

    private fun writeFragment(fragment: ByteArray) {
        val currentGatt = gatt ?: error("The iPhone meter disconnected.")
        val characteristic = commandCharacteristic ?: error("The iPhone command channel is unavailable.")
        writeCompleted.clear()
        val started = if (Build.VERSION.SDK_INT >= 33) {
            currentGatt.writeCharacteristic(
                characteristic,
                fragment,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = fragment
            @Suppress("DEPRECATION")
            currentGatt.writeCharacteristic(characteristic)
        }
        val written = try {
            writeCompleted.poll(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw AcousticDiagnosticCancelledException()
        }
        if (!started || written != true) {
            error("Could not send data to the iPhone meter.")
        }
    }

    private fun receiveFragment(fragment: ByteArray) {
        val payload = reassembler.accept(fragment) ?: return
        IphoneAcousticMeterProtocol.decode(payload)?.let(messages::offer)
    }

    private fun awaitMessage(
        timeoutSeconds: Long,
        predicate: (IphoneAcousticMeterProtocol.Message) -> Boolean,
    ): IphoneAcousticMeterProtocol.Message? {
        val deadline = SystemClock.elapsedRealtimeNanos() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        val deferred = mutableListOf<IphoneAcousticMeterProtocol.Message>()
        try {
            while (SystemClock.elapsedRealtimeNanos() < deadline) {
                checkCancelled()
                failure.get()?.let(::error)
                val remaining = deadline - SystemClock.elapsedRealtimeNanos()
                val message = try {
                    messages.poll(remaining.coerceAtMost(250_000_000L), TimeUnit.NANOSECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw AcousticDiagnosticCancelledException()
                } ?: continue
                if (message is IphoneAcousticMeterProtocol.Error) error(message.message)
                if (predicate(message)) return message
                deferred += message
            }

            return null
        } finally {
            deferred.forEach(messages::offer)
        }
    }

    private fun resolveDevice(address: String?): BluetoothDevice {
        val adapter = bluetoothManager.adapter ?: error("Bluetooth is unavailable on this device.")
        if (!adapter.isEnabled) error("Turn Bluetooth on before measuring with the iPhone.")
        val cleanAddress = address?.takeIf(String::isNotBlank)
        if (cleanAddress != null) {
            log(IphoneCalibrationLogLevel.INFO, "Using saved/selected address $cleanAddress.")
            return runCatching { adapter.getRemoteDevice(cleanAddress) }.getOrElse {
                log(
                    IphoneCalibrationLogLevel.WARN,
                    "Saved address $cleanAddress is unavailable; scanning for the meter.",
                )
                scanForMeter()
            }
        }

        log(IphoneCalibrationLogLevel.INFO, "Scanning for iPhone meter service UUID.")
        return scanForMeter()
    }

    private fun scanForMeter(): BluetoothDevice {
        val scanner = bluetoothManager.adapter.bluetoothLeScanner
            ?: error("Bluetooth LE scanning is unavailable.")
        val result = LinkedBlockingQueue<BluetoothDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
                result.offer(scanResult.device)
            }

            override fun onScanFailed(errorCode: Int) {
                failure.compareAndSet(null, "Bluetooth scan failed ($errorCode).")
            }
        }
        scanner.startScan(
            listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(IphoneAcousticMeterProtocol.SERVICE_UUID))
                    .build(),
            ),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        return try {
            val deadlineMs = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(SCAN_TIMEOUT_SECONDS)
            var lastProgressLogMs = 0L
            var device: BluetoothDevice? = null
            while (SystemClock.elapsedRealtime() < deadlineMs && device == null) {
                checkCancelled()
                failure.get()?.let(::error)
                device = try {
                    result.poll(500L, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw AcousticDiagnosticCancelledException()
                }
                val now = SystemClock.elapsedRealtime()
                if (device == null && now - lastProgressLogMs >= 3_000L) {
                    log(IphoneCalibrationLogLevel.INFO, "Still scanning for iPhone meter…")
                    lastProgressLogMs = now
                }
            }
            failure.get()?.let(::error)
            if (device == null) {
                error("No iPhone running Engine Loudness Meter was found.")
            }
            log(IphoneCalibrationLogLevel.OK, "Found iPhone meter at ${device.address}.")
            device
        } finally {
            scanner.stopScan(callback)
        }
    }

    private fun logBlePrerequisites() {
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            log(IphoneCalibrationLogLevel.ERROR, "Bluetooth is turned off.")
            return
        }
        if (Build.VERSION.SDK_INT <= 30) {
            log(
                IphoneCalibrationLogLevel.INFO,
                "On Android 10, BLE scan needs location permission and Location usually ON in car settings.",
            )
        }
    }

    private fun log(level: IphoneCalibrationLogLevel, message: String) {
        onLog?.invoke(level, message)
    }

    private fun waitUntil(timeoutSeconds: Long, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtimeNanos() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (!predicate() && SystemClock.elapsedRealtimeNanos() < deadline) {
            checkCancelled()
            failure.get()?.let(::error)
            try {
                Thread.sleep(20L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw AcousticDiagnosticCancelledException()
            }
        }
    }

    private fun checkCancelled() {
        if (cancellationRequested.get() || Thread.currentThread().isInterrupted) {
            throw AcousticDiagnosticCancelledException()
        }
    }

    private fun sequence(): Int = nextSequence.getAndUpdate { if (it == 0xffff) 1 else it + 1 }

    private companion object {
        val CLIENT_CONFIGURATION_UUID: java.util.UUID =
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val DEFAULT_MTU = 23
        const val PREFERRED_MTU = 247
        const val ATT_HEADER_SIZE = 3
        const val MINIMUM_PACKET_SIZE = 20
        const val READY_TIMEOUT_SECONDS = 15L
        const val REQUEST_TIMEOUT_SECONDS = 10L
        const val LATENCY_TIMEOUT_SECONDS = 15L
        const val WRITE_TIMEOUT_SECONDS = 5L
        const val SCAN_TIMEOUT_SECONDS = 15L
        const val CLOCK_SYNC_ATTEMPTS = 3
    }
}

internal object BleFragmentCodec {
    private const val MAGIC: Byte = 0x7f
    private const val HEADER_SIZE = 7

    fun fragment(payload: ByteArray, messageId: Int, maximumPacketSize: Int): List<ByteArray> {
        val bodySize = (maximumPacketSize - HEADER_SIZE).coerceAtLeast(1)
        return payload.asList().chunked(bodySize).mapIndexed { index, chunk ->
            val offset = index * bodySize
            ByteBuffer.allocate(HEADER_SIZE + chunk.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(MAGIC)
                .putShort(messageId.toShort())
                .putShort(offset.toShort())
                .putShort(payload.size.toShort())
                .put(chunk.toByteArray())
                .array()
        }
    }

    fun header(packet: ByteArray): FragmentHeader? {
        if (packet.size < HEADER_SIZE) return null
        val input = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        if (input.get() != MAGIC) return null

        return FragmentHeader(
            messageId = input.short.toInt() and 0xffff,
            offset = input.short.toInt() and 0xffff,
            totalSize = input.short.toInt() and 0xffff,
            payload = ByteArray(packet.size - HEADER_SIZE).also(input::get),
        )
    }
}

internal data class FragmentHeader(
    val messageId: Int,
    val offset: Int,
    val totalSize: Int,
    val payload: ByteArray,
)

internal class BleFragmentReassembler {
    private var messageId = -1
    private var expectedOffset = 0
    private var buffer = ByteArray(0)

    @Synchronized
    fun accept(packet: ByteArray): ByteArray? {
        val fragment = BleFragmentCodec.header(packet) ?: return null
        if (fragment.totalSize <= 0 || fragment.totalSize > MAX_MESSAGE_SIZE) return null
        if (fragment.messageId != messageId || fragment.offset == 0) {
            if (fragment.offset != 0) return null
            messageId = fragment.messageId
            expectedOffset = 0
            buffer = ByteArray(fragment.totalSize)
        }
        if (fragment.offset != expectedOffset || fragment.offset + fragment.payload.size > buffer.size) {
            messageId = -1
            return null
        }
        fragment.payload.copyInto(buffer, fragment.offset)
        expectedOffset += fragment.payload.size
        if (expectedOffset != buffer.size) return null
        val complete = buffer
        messageId = -1
        expectedOffset = 0
        buffer = ByteArray(0)

        return complete
    }

    private companion object {
        const val MAX_MESSAGE_SIZE = 1024
    }
}

internal class AcousticDiagnosticCancelledException : RuntimeException()

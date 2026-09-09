import CoreBluetooth
import Foundation

final class MeterPeripheral: NSObject, CBPeripheralManagerDelegate {
    var onConnectionChanged: ((Bool) -> Void)?
    var onPairingChanged: ((Bool) -> Void)?
    var onActiveMeasurement: ((String?, String, String) -> Void)?
    var onError: ((String) -> Void)?

    private let audioEngine: AudioMeasurementEngine
    private let pairingCode: Int32
    private let instanceId: UUID
    private let model: String
    private let reassembler = BLEFragmentReassembler()
    private var manager: CBPeripheralManager?
    private var commandCharacteristic: CBMutableCharacteristic?
    private var eventCharacteristic: CBMutableCharacteristic?
    private var subscribedCentral: CBCentral?
    private var pendingNotifications: [Data] = []
    private var nextFragmentId: UInt16 = 1
    private var challenge = Data()
    private var authenticated = false

    init(
        audioEngine: AudioMeasurementEngine,
        pairingCode: Int32,
        instanceId: UUID,
        model: String
    ) {
        self.audioEngine = audioEngine
        self.pairingCode = pairingCode
        self.instanceId = instanceId
        self.model = model
        super.init()
        audioEngine.onMeasurementResult = { [weak self] sequence, measurementId, metrics in
            self?.send(MeterProtocol.result(sequence: sequence, measurementId: measurementId, metrics: metrics))
            self?.onActiveMeasurement?(nil, "", "Waiting")
        }
        audioEngine.onLatencyResult = { [weak self] sequence, measurementId, times, confidence in
            self?.send(
                MeterProtocol.latencyResult(
                    sequence: sequence,
                    measurementId: measurementId,
                    detectedTimesNanos: times,
                    confidence: confidence
                )
            )
        }
    }

    var hasSavedPairing: Bool { KeychainStore.loadToken()?.count == MeterProtocol.tokenSize }

    func start() {
        guard manager == nil else { return }
        manager = CBPeripheralManager(delegate: self, queue: .main)
    }

    func stop(reason: String? = nil) {
        if let reason {
            send(MeterProtocol.error(sequence: 0, message: reason))
        }
        audioEngine.cancel()
        manager?.stopAdvertising()
        manager?.removeAllServices()
        pendingNotifications.removeAll()
        subscribedCentral = nil
        authenticated = false
        onConnectionChanged?(false)
        manager = nil
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        guard peripheral.state == .poweredOn else {
            if peripheral.state != .unknown && peripheral.state != .resetting {
                onError?("Bluetooth must be enabled for the BYD connection.")
            }
            return
        }
        publishService(on: peripheral)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error {
            onError?(error.localizedDescription)
            return
        }
        peripheral.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [CBUUID(string: MeterProtocol.serviceUUID)],
            CBAdvertisementDataLocalNameKey: "Engine Loudness Meter",
        ])
    }

    func peripheralManager(
        _ peripheral: CBPeripheralManager,
        central: CBCentral,
        didSubscribeTo characteristic: CBCharacteristic
    ) {
        subscribedCentral = central
        authenticated = false
        onConnectionChanged?(true)
    }

    func peripheralManager(
        _ peripheral: CBPeripheralManager,
        central: CBCentral,
        didUnsubscribeFrom characteristic: CBCharacteristic
    ) {
        if subscribedCentral?.identifier == central.identifier {
            subscribedCentral = nil
            authenticated = false
            pendingNotifications.removeAll()
            audioEngine.cancel()
            onConnectionChanged?(false)
            onActiveMeasurement?(nil, "", "Waiting")
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            guard request.characteristic.uuid == CBUUID(string: MeterProtocol.commandUUID),
                  let value = request.value else {
                peripheral.respond(to: request, withResult: .requestNotSupported)
                continue
            }
            if let active = subscribedCentral, active.identifier != request.central.identifier {
                peripheral.respond(to: request, withResult: .insufficientAuthorization)
                continue
            }
            subscribedCentral = request.central
            peripheral.respond(to: request, withResult: .success)
            if let frame = reassembler.accept(value) {
                handle(frame)
            }
        }
    }

    func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
        flushNotifications()
    }

    private func publishService(on peripheral: CBPeripheralManager) {
        let command = CBMutableCharacteristic(
            type: CBUUID(string: MeterProtocol.commandUUID),
            properties: [.write],
            value: nil,
            permissions: [.writeable]
        )
        let event = CBMutableCharacteristic(
            type: CBUUID(string: MeterProtocol.eventUUID),
            properties: [.notify],
            value: nil,
            permissions: []
        )
        let service = CBMutableService(type: CBUUID(string: MeterProtocol.serviceUUID), primary: true)
        service.characteristics = [command, event]
        commandCharacteristic = command
        eventCharacteristic = event
        peripheral.add(service)
    }

    private func handle(_ frame: Data) {
        do {
            let command = try MeterProtocol.decodeCommand(frame)
            switch command {
            case let .hello(sequence):
                challenge = MeterProtocol.randomData(count: MeterProtocol.challengeSize)
                send(
                    MeterProtocol.helloResponse(
                        sequence: sequence,
                        instanceId: instanceId,
                        model: model,
                        hasToken: hasSavedPairing,
                        challenge: challenge
                    )
                )
            case let .pair(sequence, code):
                let accepted = code == pairingCode
                let token = accepted
                    ? MeterProtocol.randomData(count: MeterProtocol.tokenSize)
                    : Data(repeating: 0, count: MeterProtocol.tokenSize)
                if accepted {
                    KeychainStore.saveToken(token)
                    onPairingChanged?(true)
                }
                send(MeterProtocol.pairResult(sequence: sequence, accepted: accepted, token: token))
            case let .auth(sequence, candidateChallenge, hmac):
                let accepted = candidateChallenge == challenge &&
                    KeychainStore.loadToken().map {
                        MeterProtocol.authenticate(token: $0, challenge: challenge, candidate: hmac)
                    } == true
                authenticated = accepted
                send(MeterProtocol.authResult(sequence: sequence, accepted: accepted))
            case let .clockPing(sequence, androidSendNanos):
                try requireAuthentication()
                let received = hostNanos()
                let sent = hostNanos()
                send(
                    MeterProtocol.clockPong(
                        sequence: sequence,
                        androidSendNanos: androidSendNanos,
                        iphoneReceiveNanos: received,
                        iphoneSendNanos: sent
                    )
                )
            case let .armMeasurement(schedule):
                try requireAuthentication()
                try audioEngine.arm(schedule)
                onActiveMeasurement?(
                    schedule.carName,
                    schedule.exterior ? "EXTERIOR • PURE" : "CABIN",
                    "Armed"
                )
                send(MeterProtocol.armed(sequence: schedule.sequence, measurementId: schedule.id))
            case let .armLatency(schedule):
                try requireAuthentication()
                try audioEngine.armLatency(schedule)
                onActiveMeasurement?("Acoustic latency", "THREE CHIRPS", "Armed")
                send(MeterProtocol.armed(sequence: schedule.sequence, measurementId: schedule.id))
            case .cancel, .stop:
                audioEngine.cancel()
                onActiveMeasurement?(nil, "", "Waiting")
            }
        } catch {
            let message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            send(MeterProtocol.error(sequence: sequence(from: frame), message: message))
            onError?(message)
        }
    }

    private func requireAuthentication() throws {
        if !authenticated { throw MeterPeripheralError.authenticationRequired }
    }

    private func send(_ frame: Data) {
        guard let central = subscribedCentral else { return }
        let maximum = max(20, central.maximumUpdateValueLength)
        let fragments = BLEFragmentCodec.fragments(
            payload: frame,
            messageId: nextFragmentId,
            maximumPacketSize: maximum
        )
        nextFragmentId = nextFragmentId == .max ? 1 : nextFragmentId + 1
        pendingNotifications.append(contentsOf: fragments)
        flushNotifications()
    }

    private func flushNotifications() {
        guard let manager, let eventCharacteristic, let central = subscribedCentral else { return }
        while let first = pendingNotifications.first {
            if !manager.updateValue(first, for: eventCharacteristic, onSubscribedCentrals: [central]) {
                return
            }
            pendingNotifications.removeFirst()
        }
    }

    private func sequence(from frame: Data) -> UInt16 {
        guard frame.count >= 4 else { return 0 }
        return frame.subdata(in: 2..<4).withUnsafeBytes { raw in
            UInt16(littleEndian: raw.loadUnaligned(as: UInt16.self))
        }
    }
}

private enum MeterPeripheralError: LocalizedError {
    case authenticationRequired

    var errorDescription: String? {
        "The BYD must authenticate before starting a measurement."
    }
}

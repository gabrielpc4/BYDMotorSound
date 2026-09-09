import CryptoKit
import Foundation
import Security

enum MeterProtocol {
    static let version: UInt8 = 2
    static let serviceUUID = "88D9E8D0-540A-4A1A-AAD8-E8732D7EBF01"
    static let commandUUID = "88D9E8D1-540A-4A1A-AAD8-E8732D7EBF01"
    static let eventUUID = "88D9E8D2-540A-4A1A-AAD8-E8732D7EBF01"
    static let challengeSize = 16
    static let tokenSize = 32
    static let hmacSize = 32
    static let chirpCount = 3

    enum MessageType: UInt8 {
        case hello = 1
        case helloResponse = 2
        case pair = 3
        case pairResult = 4
        case auth = 5
        case authResult = 6
        case clockPing = 7
        case clockPong = 8
        case armMeasurement = 9
        case armed = 10
        case result = 11
        case cancel = 12
        case error = 13
        case armLatency = 14
        case latencyResult = 15
        case stop = 16
    }

    struct Envelope {
        let type: MessageType
        let sequence: UInt16
        let payload: Data
    }

    struct MeasurementSchedule {
        let sequence: UInt16
        let id: Int64
        let ambientBeforeStartNanos: UInt64
        let engineStartNanos: UInt64
        let sweepStartNanos: UInt64
        let sweepEndNanos: UInt64
        let engineEndNanos: UInt64
        let ambientAfterEndNanos: UInt64
        let carName: String
        let exterior: Bool
    }

    struct LatencySchedule {
        let sequence: UInt16
        let id: Int64
        let chirpTimesNanos: [UInt64]
    }

    struct MeasurementMetrics {
        let weightedEngineEnergy: Double
        let lowBandEngineEnergy: Double
        let presenceBandEngineEnergy: Double
        let weightedAmbientBeforeEnergy: Double
        let weightedAmbientAfterEnergy: Double
        let lowBandAmbientBeforeEnergy: Double
        let lowBandAmbientAfterEnergy: Double
        let presenceBandAmbientBeforeEnergy: Double
        let presenceBandAmbientAfterEnergy: Double
        let peakLinear: Double
        let sampleCount: Int64
        let sampleRateHz: Double
        let channelCount: Int32
        let channelBalanceDb: Double?
        let routeValid: Bool
        let windowValid: Bool
    }

    enum Command {
        case hello(sequence: UInt16)
        case pair(sequence: UInt16, code: Int32)
        case auth(sequence: UInt16, challenge: Data, hmac: Data)
        case clockPing(sequence: UInt16, androidSendNanos: Int64)
        case armMeasurement(MeasurementSchedule)
        case armLatency(LatencySchedule)
        case cancel(sequence: UInt16)
        case stop(sequence: UInt16)
    }

    static func decodeCommand(_ data: Data) throws -> Command {
        var reader = DataReader(data)
        guard try reader.uint8() == version else { throw ProtocolError.unsupportedVersion }
        guard let type = MessageType(rawValue: try reader.uint8()) else { throw ProtocolError.invalidMessage }
        let sequence = try reader.uint16()
        switch type {
        case .hello:
            return .hello(sequence: sequence)
        case .pair:
            return .pair(sequence: sequence, code: try reader.int32())
        case .auth:
            return .auth(
                sequence: sequence,
                challenge: try reader.data(count: challengeSize),
                hmac: try reader.data(count: hmacSize)
            )
        case .clockPing:
            return .clockPing(sequence: sequence, androidSendNanos: try reader.int64())
        case .armMeasurement:
            let schedule = MeasurementSchedule(
                sequence: sequence,
                id: try reader.int64(),
                ambientBeforeStartNanos: try reader.uint64(),
                engineStartNanos: try reader.uint64(),
                sweepStartNanos: try reader.uint64(),
                sweepEndNanos: try reader.uint64(),
                engineEndNanos: try reader.uint64(),
                ambientAfterEndNanos: try reader.uint64(),
                carName: try reader.sizedString(),
                exterior: try reader.uint8() == 1
            )
            guard schedule.ambientBeforeStartNanos < schedule.engineStartNanos,
                  schedule.engineStartNanos < schedule.sweepStartNanos,
                  schedule.sweepStartNanos < schedule.sweepEndNanos,
                  schedule.sweepEndNanos <= schedule.engineEndNanos,
                  schedule.engineEndNanos < schedule.ambientAfterEndNanos else {
                throw ProtocolError.invalidSchedule
            }
            return .armMeasurement(schedule)
        case .armLatency:
            return .armLatency(
                LatencySchedule(
                    sequence: sequence,
                    id: try reader.int64(),
                    chirpTimesNanos: try (0..<chirpCount).map { _ in try reader.uint64() }
                )
            )
        case .cancel:
            return .cancel(sequence: sequence)
        case .stop:
            return .stop(sequence: sequence)
        default:
            throw ProtocolError.invalidMessage
        }
    }

    static func helloResponse(
        sequence: UInt16,
        instanceId: UUID,
        model: String,
        hasToken: Bool,
        challenge: Data
    ) -> Data {
        var writer = DataWriter(type: .helloResponse, sequence: sequence)
        var uuid = instanceId.uuid
        withUnsafeBytes(of: &uuid) { writer.data.append(contentsOf: $0) }
        writer.append(string: model)
        writer.append(hasToken ? UInt8(1) : UInt8(0))
        writer.append(fixed: challenge, size: challengeSize)
        return writer.data
    }

    static func pairResult(sequence: UInt16, accepted: Bool, token: Data) -> Data {
        var writer = DataWriter(type: .pairResult, sequence: sequence)
        writer.append(accepted ? UInt8(1) : UInt8(0))
        writer.append(fixed: token, size: tokenSize)
        return writer.data
    }

    static func authResult(sequence: UInt16, accepted: Bool) -> Data {
        var writer = DataWriter(type: .authResult, sequence: sequence)
        writer.append(accepted ? UInt8(1) : UInt8(0))
        return writer.data
    }

    static func clockPong(
        sequence: UInt16,
        androidSendNanos: Int64,
        iphoneReceiveNanos: UInt64,
        iphoneSendNanos: UInt64
    ) -> Data {
        var writer = DataWriter(type: .clockPong, sequence: sequence)
        writer.append(androidSendNanos)
        writer.append(iphoneReceiveNanos)
        writer.append(iphoneSendNanos)
        return writer.data
    }

    static func armed(sequence: UInt16, measurementId: Int64) -> Data {
        var writer = DataWriter(type: .armed, sequence: sequence)
        writer.append(measurementId)
        return writer.data
    }

    static func result(sequence: UInt16, measurementId: Int64, metrics: MeasurementMetrics) -> Data {
        var writer = DataWriter(type: .result, sequence: sequence)
        writer.append(measurementId)
        writer.append(metrics.weightedEngineEnergy)
        writer.append(metrics.lowBandEngineEnergy)
        writer.append(metrics.presenceBandEngineEnergy)
        writer.append(metrics.weightedAmbientBeforeEnergy)
        writer.append(metrics.weightedAmbientAfterEnergy)
        writer.append(metrics.lowBandAmbientBeforeEnergy)
        writer.append(metrics.lowBandAmbientAfterEnergy)
        writer.append(metrics.presenceBandAmbientBeforeEnergy)
        writer.append(metrics.presenceBandAmbientAfterEnergy)
        writer.append(metrics.peakLinear)
        writer.append(metrics.sampleCount)
        writer.append(metrics.sampleRateHz)
        writer.append(metrics.channelCount)
        writer.append(metrics.channelBalanceDb ?? .nan)
        let flags: UInt32 = (metrics.routeValid ? 1 : 0) | (metrics.windowValid ? 2 : 0)
        writer.append(flags)
        return writer.data
    }

    static func latencyResult(
        sequence: UInt16,
        measurementId: Int64,
        detectedTimesNanos: [UInt64],
        confidence: Double
    ) -> Data {
        var writer = DataWriter(type: .latencyResult, sequence: sequence)
        writer.append(measurementId)
        detectedTimesNanos.prefix(chirpCount).forEach { writer.append($0) }
        while writer.data.count < 4 + 8 + chirpCount * 8 { writer.append(UInt64(0)) }
        writer.append(confidence)
        return writer.data
    }

    static func error(sequence: UInt16, message: String) -> Data {
        var writer = DataWriter(type: .error, sequence: sequence)
        writer.append(string: message)
        return writer.data
    }

    static func authenticate(token: Data, challenge: Data, candidate: Data) -> Bool {
        guard token.count == tokenSize, challenge.count == challengeSize, candidate.count == hmacSize else {
            return false
        }
        let key = SymmetricKey(data: token)
        let expected = Data(HMAC<SHA256>.authenticationCode(for: challenge, using: key))
        return expected == candidate
    }

    static func randomData(count: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        let result = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        precondition(result == errSecSuccess)
        return Data(bytes)
    }
}

enum ProtocolError: LocalizedError {
    case unsupportedVersion
    case invalidMessage
    case invalidSchedule
    case truncatedMessage

    var errorDescription: String? {
        switch self {
        case .unsupportedVersion: "Protocol version is not supported."
        case .invalidMessage: "The BYD sent an invalid command."
        case .invalidSchedule: "The BYD sent an invalid time window."
        case .truncatedMessage: "The Bluetooth message was incomplete."
        }
    }
}

private struct DataWriter {
    var data = Data()

    init(type: MeterProtocol.MessageType, sequence: UInt16) {
        append(MeterProtocol.version)
        append(type.rawValue)
        append(sequence)
    }

    mutating func append<T: FixedWidthInteger>(_ value: T) {
        var little = value.littleEndian
        withUnsafeBytes(of: &little) { data.append(contentsOf: $0) }
    }

    mutating func append(_ value: Double) {
        append(value.bitPattern)
    }

    mutating func append(string: String) {
        let bytes = Data(string.utf8.prefix(80))
        append(UInt8(bytes.count))
        data.append(bytes)
    }

    mutating func append(fixed value: Data, size: Int) {
        if value.count >= size {
            data.append(value.prefix(size))
        } else {
            data.append(value)
            data.append(Data(repeating: 0, count: size - value.count))
        }
    }
}

private struct DataReader {
    private let data: Data
    private var offset = 0

    init(_ data: Data) {
        self.data = data
    }

    mutating func uint8() throws -> UInt8 { try integer() }
    mutating func uint16() throws -> UInt16 { try integer() }
    mutating func uint32() throws -> UInt32 { try integer() }
    mutating func uint64() throws -> UInt64 { try integer() }
    mutating func int32() throws -> Int32 { try integer() }
    mutating func int64() throws -> Int64 { try integer() }

    mutating func sizedString() throws -> String {
        let count = Int(try uint8())
        let value = try self.data(count: count)
        guard let string = String(data: value, encoding: .utf8) else { throw ProtocolError.invalidMessage }
        return string
    }

    mutating func data(count: Int) throws -> Data {
        guard count >= 0, offset + count <= data.count else { throw ProtocolError.truncatedMessage }
        let value = data.subdata(in: offset..<(offset + count))
        offset += count
        return value
    }

    private mutating func integer<T: FixedWidthInteger>() throws -> T {
        let size = MemoryLayout<T>.size
        let bytes = try data(count: size)
        return bytes.withUnsafeBytes { raw in
            T(littleEndian: raw.loadUnaligned(as: T.self))
        }
    }
}

struct BLEFragmentCodec {
    static let magic: UInt8 = 0x7f
    static let headerSize = 7

    static func fragments(payload: Data, messageId: UInt16, maximumPacketSize: Int) -> [Data] {
        let bodySize = max(1, maximumPacketSize - headerSize)
        return stride(from: 0, to: payload.count, by: bodySize).map { offset in
            var writer = FragmentWriter()
            writer.append(magic)
            writer.append(messageId)
            writer.append(UInt16(offset))
            writer.append(UInt16(payload.count))
            writer.data.append(payload.subdata(in: offset..<min(payload.count, offset + bodySize)))
            return writer.data
        }
    }
}

final class BLEFragmentReassembler {
    private var messageId: UInt16?
    private var expectedOffset = 0
    private var buffer = Data()
    private var totalSize = 0

    func accept(_ packet: Data) -> Data? {
        guard packet.count >= BLEFragmentCodec.headerSize else { return nil }
        var reader = FragmentReader(packet)
        guard reader.uint8() == BLEFragmentCodec.magic,
              let id = reader.uint16(),
              let offset = reader.uint16(),
              let total = reader.uint16(),
              total > 0,
              total <= 1024 else { return nil }
        if messageId != id || offset == 0 {
            guard offset == 0 else { return nil }
            messageId = id
            expectedOffset = 0
            totalSize = Int(total)
            buffer = Data()
        }
        guard Int(offset) == expectedOffset else {
            reset()
            return nil
        }
        buffer.append(packet.dropFirst(BLEFragmentCodec.headerSize))
        expectedOffset = buffer.count
        guard buffer.count <= totalSize else {
            reset()
            return nil
        }
        guard buffer.count == totalSize else { return nil }
        let complete = buffer
        reset()
        return complete
    }

    private func reset() {
        messageId = nil
        expectedOffset = 0
        totalSize = 0
        buffer = Data()
    }
}

private struct FragmentWriter {
    var data = Data()
    mutating func append<T: FixedWidthInteger>(_ value: T) {
        var little = value.littleEndian
        withUnsafeBytes(of: &little) { data.append(contentsOf: $0) }
    }
}

private struct FragmentReader {
    let data: Data
    var offset = 0
    init(_ data: Data) { self.data = data }
    mutating func uint8() -> UInt8? { integer() }
    mutating func uint16() -> UInt16? { integer() }
    private mutating func integer<T: FixedWidthInteger>() -> T? {
        let size = MemoryLayout<T>.size
        guard offset + size <= data.count else { return nil }
        let value = data.subdata(in: offset..<(offset + size)).withUnsafeBytes { raw in
            T(littleEndian: raw.loadUnaligned(as: T.self))
        }
        offset += size
        return value
    }
}

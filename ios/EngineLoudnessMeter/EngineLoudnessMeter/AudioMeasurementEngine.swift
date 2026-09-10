import AVFAudio
import Accelerate
import Foundation
import UIKit

final class AudioMeasurementEngine {
    var onLiveLevel: ((Double) -> Void)?
    var onMeasurementResult: ((UInt16, Int64, MeterProtocol.MeasurementMetrics) -> Void)?
    var onLatencyResult: ((UInt16, Int64, [UInt64], Double) -> Void)?
    var onFatalError: ((String) -> Void)?

    private let engine = AVAudioEngine()
    private let lock = NSLock()
    private var measurement: ActiveMeasurement?
    private var latencyCapture: ActiveLatencyCapture?
    private var lastLiveUpdateNanos: UInt64 = 0
    private var tapInstalled = false
    private(set) var sampleRate: Double = 0
    private(set) var channelCount: AVAudioChannelCount = 0
    private(set) var routeValid = false

    var routeDescription: String {
        AVAudioSession.sharedInstance().currentRoute.inputs.first?.portName ?? "No microphone"
    }

    func requestPermissionAndStart(completion: @escaping (Result<Void, Error>) -> Void) {
        AVAudioApplication.requestRecordPermission { [weak self] granted in
            guard granted else {
                DispatchQueue.main.async { completion(.failure(AudioMeterError.microphonePermissionDenied)) }
                return
            }
            do {
                try self?.start()
                DispatchQueue.main.async { completion(.success(())) }
            } catch {
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
    }

    func arm(_ schedule: MeterProtocol.MeasurementSchedule) throws {
        guard routeValid else { throw AudioMeterError.builtInMicrophoneRequired }
        guard schedule.ambientBeforeStartNanos >= hostNanos() + 250_000_000 else {
            throw AudioMeterError.scheduleTooLate
        }
        lock.lock()
        measurement = ActiveMeasurement(
            schedule: schedule,
            sampleRate: sampleRate,
            channelCount: Int32(channelCount)
        )
        latencyCapture = nil
        lock.unlock()
    }

    func armLatency(_ schedule: MeterProtocol.LatencySchedule) throws {
        guard routeValid else { throw AudioMeterError.builtInMicrophoneRequired }
        guard schedule.chirpTimesNanos.first ?? 0 >= hostNanos() + 250_000_000 else {
            throw AudioMeterError.scheduleTooLate
        }
        lock.lock()
        latencyCapture = ActiveLatencyCapture(schedule: schedule, sampleRate: sampleRate)
        measurement = nil
        lock.unlock()
    }

    func cancel() {
        lock.lock()
        measurement = nil
        latencyCapture = nil
        lock.unlock()
    }

    func stop() {
        cancel()
        NotificationCenter.default.removeObserver(
            self,
            name: AVAudioSession.routeChangeNotification,
            object: AVAudioSession.sharedInstance()
        )
        if tapInstalled {
            engine.inputNode.removeTap(onBus: 0)
            tapInstalled = false
        }
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        routeValid = false
    }

    private func start() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: [])
        try session.setPreferredSampleRate(48_000)
        try session.setPreferredIOBufferDuration(0.01)
        try session.setActive(true)
        guard let builtIn = session.availableInputs?.first(where: { $0.portType == .builtInMic }) else {
            throw AudioMeterError.builtInMicrophoneRequired
        }
        try session.setPreferredInput(builtIn)
        requestStereoInputIfAvailable(session: session, input: builtIn)
        guard session.currentRoute.inputs.count == 1,
              session.currentRoute.inputs.first?.portType == .builtInMic else {
            throw AudioMeterError.builtInMicrophoneRequired
        }
        routeValid = true
        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        guard format.commonFormat == .pcmFormatFloat32, format.sampleRate > 0, format.channelCount > 0 else {
            throw AudioMeterError.unsupportedCaptureFormat
        }
        sampleRate = format.sampleRate
        channelCount = format.channelCount
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, time in
            self?.process(buffer: buffer, time: time)
        }
        tapInstalled = true
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(audioRouteChanged),
            name: AVAudioSession.routeChangeNotification,
            object: session
        )
        engine.prepare()
        try engine.start()
    }

    private func requestStereoInputIfAvailable(
        session: AVAudioSession,
        input: AVAudioSessionPortDescription
    ) {
        guard let dataSource = input.dataSources?.first(where: {
            $0.supportedPolarPatterns?.contains(.stereo) == true
        }) else { return }

        do {
            try dataSource.setPreferredPolarPattern(.stereo)
            try input.setPreferredDataSource(dataSource)
            try session.setPreferredInputOrientation(.portrait)
            if session.maximumInputNumberOfChannels >= 2 {
                try session.setPreferredInputNumberOfChannels(2)
            }
        } catch {
            // Stereo is opportunistic. A valid built-in mono route remains fully supported.
        }
    }

    private func process(buffer: AVAudioPCMBuffer, time: AVAudioTime) {
        guard let channels = buffer.floatChannelData, buffer.frameLength > 0 else { return }
        let frameCount = Int(buffer.frameLength)
        let rate = buffer.format.sampleRate
        let timingValid = time.isHostTimeValid && time.isSampleTimeValid
        let startNanos = timingValid
            ? UInt64(max(0, AVAudioTime.seconds(forHostTime: time.hostTime) * 1_000_000_000))
            : hostNanos()
        let nanosPerFrame = 1_000_000_000.0 / rate
        var liveEnergy = 0.0
        var frameSamples = [Double](repeating: 0, count: Int(buffer.format.channelCount))
        lock.lock()
        measurement?.observeBuffer(
            sampleTime: timingValid ? time.sampleTime : nil,
            frameCount: buffer.frameLength
        )
        for index in 0..<frameCount {
            var frameEnergy = 0.0
            var latencySample = 0.0
            for channel in 0..<Int(buffer.format.channelCount) {
                let sample = Double(channels[channel][index])
                frameSamples[channel] = sample
                frameEnergy += sample * sample
                latencySample += sample
            }
            latencySample /= Double(max(1, buffer.format.channelCount))
            let sampleNanos = startNanos + UInt64(Double(index) * nanosPerFrame)
            liveEnergy += frameEnergy / Double(buffer.format.channelCount)
            measurement?.consume(samples: frameSamples, at: sampleNanos)
            latencyCapture?.consume(sample: latencySample, at: sampleNanos)
        }
        let completedMeasurement = measurement?.isComplete(at: startNanos + UInt64(Double(frameCount) * nanosPerFrame)) == true
            ? measurement : nil
        if completedMeasurement != nil { measurement = nil }
        let completedLatency = latencyCapture?.isComplete(at: startNanos + UInt64(Double(frameCount) * nanosPerFrame)) == true
            ? latencyCapture : nil
        if completedLatency != nil { latencyCapture = nil }
        lock.unlock()

        let endNanos = startNanos + UInt64(Double(frameCount) * nanosPerFrame)
        if endNanos - lastLiveUpdateNanos >= 100_000_000 {
            lastLiveUpdateNanos = endNanos
            let rms = sqrt(liveEnergy / Double(frameCount))
            let db = 20 * log10(max(rms, 1e-9))
            DispatchQueue.main.async { [weak self] in self?.onLiveLevel?(db) }
        }
        if let completedMeasurement {
            let metrics = completedMeasurement.metrics(routeValid: routeValid)
            DispatchQueue.main.async { [weak self] in
                self?.onMeasurementResult?(
                    completedMeasurement.schedule.sequence,
                    completedMeasurement.schedule.id,
                    metrics
                )
            }
        }
        if let completedLatency {
            let result = completedLatency.result()
            DispatchQueue.main.async { [weak self] in
                self?.onLatencyResult?(
                    completedLatency.schedule.sequence,
                    completedLatency.schedule.id,
                    result.times,
                    result.confidence
                )
            }
        }
    }

    @objc private func audioRouteChanged() {
        let session = AVAudioSession.sharedInstance()
        routeValid = session.currentRoute.inputs.count == 1 &&
            session.currentRoute.inputs.first?.portType == .builtInMic
        if !routeValid {
            cancel()
            DispatchQueue.main.async { [weak self] in
                self?.onFatalError?(AudioMeterError.builtInMicrophoneRequired.localizedDescription)
            }
        }
    }
}

private final class ActiveMeasurement {
    let schedule: MeterProtocol.MeasurementSchedule
    private let sampleRate: Double
    private let channelCount: Int32
    private var before: [PhaseAccumulator]
    private var engine: [PhaseAccumulator]
    private var after: [PhaseAccumulator]
    private var expectedSampleTime: AVAudioFramePosition?
    private var sampleTimelineValid = true

    init(schedule: MeterProtocol.MeasurementSchedule, sampleRate: Double, channelCount: Int32) {
        self.schedule = schedule
        self.sampleRate = sampleRate
        self.channelCount = channelCount
        let channels = max(1, Int(channelCount))
        before = Array(repeating: PhaseAccumulator(), count: channels)
        engine = Array(repeating: PhaseAccumulator(), count: channels)
        after = Array(repeating: PhaseAccumulator(), count: channels)
    }

    func observeBuffer(sampleTime: AVAudioFramePosition?, frameCount: AVAudioFrameCount) {
        guard let sampleTime else {
            sampleTimelineValid = false
            return
        }
        if let expectedSampleTime, sampleTime != expectedSampleTime {
            sampleTimelineValid = false
        }
        expectedSampleTime = sampleTime + AVAudioFramePosition(frameCount)
    }

    func consume(samples: [Double], at nanos: UInt64) {
        guard samples.count == engine.count else {
            sampleTimelineValid = false
            return
        }
        switch nanos {
        case schedule.ambientBeforeStartNanos..<schedule.engineStartNanos:
            consume(samples: samples, into: &before, trackPeak: false)
        case schedule.engineStartNanos..<schedule.engineEndNanos:
            consume(samples: samples, into: &engine, trackPeak: true)
        case schedule.engineEndNanos..<schedule.ambientAfterEndNanos:
            consume(samples: samples, into: &after, trackPeak: false)
        default:
            break
        }
    }

    func isComplete(at nanos: UInt64) -> Bool {
        nanos >= schedule.ambientAfterEndNanos
    }

    func metrics(routeValid: Bool) -> MeterProtocol.MeasurementMetrics {
        let expectedEngineSamples = Double(schedule.engineEndNanos - schedule.engineStartNanos) / 1e9 * sampleRate
        let engineFrames = engine.first?.count ?? 0
        let windowValid = sampleTimelineValid && engineFrames >= Int64(expectedEngineSamples * 0.97) &&
            (before.first?.count ?? 0) > 0 && (after.first?.count ?? 0) > 0
        return MeterProtocol.MeasurementMetrics(
            weightedEngineEnergy: meanEnergy(engine, keyPath: \.weightedEnergy),
            lowBandEngineEnergy: meanEnergy(engine, keyPath: \.lowEnergy),
            presenceBandEngineEnergy: meanEnergy(engine, keyPath: \.presenceEnergy),
            weightedAmbientBeforeEnergy: meanEnergy(before, keyPath: \.weightedEnergy),
            weightedAmbientAfterEnergy: meanEnergy(after, keyPath: \.weightedEnergy),
            lowBandAmbientBeforeEnergy: meanEnergy(before, keyPath: \.lowEnergy),
            lowBandAmbientAfterEnergy: meanEnergy(after, keyPath: \.lowEnergy),
            presenceBandAmbientBeforeEnergy: meanEnergy(before, keyPath: \.presenceEnergy),
            presenceBandAmbientAfterEnergy: meanEnergy(after, keyPath: \.presenceEnergy),
            peakLinear: engine.map(\.peak).max() ?? 0,
            sampleCount: engineFrames * Int64(channelCount),
            sampleRateHz: sampleRate,
            channelCount: channelCount,
            channelBalanceDb: channelBalanceDb(),
            routeValid: routeValid,
            windowValid: windowValid
        )
    }

    private func consume(
        samples: [Double],
        into accumulators: inout [PhaseAccumulator],
        trackPeak: Bool
    ) {
        for channel in accumulators.indices {
            accumulators[channel].consume(
                sample: samples[channel],
                sampleRate: sampleRate,
                trackPeak: trackPeak
            )
        }
    }

    private func meanEnergy(
        _ accumulators: [PhaseAccumulator],
        keyPath: KeyPath<PhaseAccumulator, Double>
    ) -> Double {
        accumulators.map { $0[keyPath: keyPath] }.reduce(0, +) / Double(accumulators.count)
    }

    private func channelBalanceDb() -> Double? {
        guard engine.count >= 2 else { return nil }
        let left = 10 * log10(max(engine[0].weightedEnergy, 1e-15))
        let right = 10 * log10(max(engine[1].weightedEnergy, 1e-15))

        return right - left
    }
}

private final class ActiveLatencyCapture {
    private static let capturePrerollNanos: UInt64 = 250_000_000
    private static let captureTailNanos: UInt64 = 1_200_000_000

    let schedule: MeterProtocol.LatencySchedule
    private let sampleRate: Double
    private var capturedSamples: [[Float]]
    private var firstSampleTimes: [UInt64?]

    init(schedule: MeterProtocol.LatencySchedule, sampleRate: Double) {
        self.schedule = schedule
        self.sampleRate = sampleRate
        capturedSamples = Array(repeating: [], count: schedule.chirpTimesNanos.count)
        firstSampleTimes = Array(repeating: nil, count: schedule.chirpTimesNanos.count)
    }

    func consume(sample: Double, at nanos: UInt64) {
        for index in schedule.chirpTimesNanos.indices {
            let start = schedule.chirpTimesNanos[index]
            let captureStart = start > Self.capturePrerollNanos ? start - Self.capturePrerollNanos : 0
            guard nanos >= captureStart, nanos <= start + Self.captureTailNanos else { continue }
            if firstSampleTimes[index] == nil { firstSampleTimes[index] = nanos }
            capturedSamples[index].append(Float(sample))
        }
    }

    func isComplete(at nanos: UInt64) -> Bool {
        nanos >= (schedule.chirpTimesNanos.last ?? 0) + 1_000_000_000
    }

    func result() -> (times: [UInt64], confidence: Double) {
        let matches = capturedSamples.enumerated().map { index, samples in
            correlateChirp(samples: samples, firstSampleNanos: firstSampleTimes[index] ?? schedule.chirpTimesNanos[index])
        }
        return (
            matches.map(\.timeNanos),
            matches.map(\.confidence).min() ?? 0
        )
    }

    private func correlateChirp(samples: [Float], firstSampleNanos: UInt64) -> (timeNanos: UInt64, confidence: Double) {
        let decimation = 2
        let signal = stride(from: 0, to: samples.count, by: decimation).map { samples[$0] }
        let reducedRate = sampleRate / Double(decimation)
        let chirpCount = Int(reducedRate * 0.1)
        guard signal.count > chirpCount, chirpCount > 0 else { return (firstSampleNanos, 0) }
        var template = [Float](repeating: 0, count: chirpCount)
        var phase = 0.0
        for index in template.indices {
            let progress = Double(index) / Double(chirpCount - 1)
            let frequency = 1_200 * pow(6_000.0 / 1_200.0, progress)
            phase += 2 * .pi * frequency / reducedRate
            let envelope = sin(.pi * progress)
            template[index] = Float(envelope * envelope * sin(phase))
        }
        let reversed = Array(template.reversed())
        let outputCount = signal.count - template.count + 1
        var correlation = [Float](repeating: 0, count: outputCount)
        vDSP_conv(
            signal,
            1,
            reversed,
            1,
            &correlation,
            1,
            vDSP_Length(outputCount),
            vDSP_Length(template.count)
        )
        var templateEnergy: Float = 0
        vDSP_svesq(template, 1, &templateEnergy, vDSP_Length(template.count))
        var prefix = [Double](repeating: 0, count: signal.count + 1)
        for index in signal.indices {
            prefix[index + 1] = prefix[index] + Double(signal[index] * signal[index])
        }
        var bestIndex = 0
        var bestScore = 0.0
        for index in correlation.indices {
            let signalEnergy = prefix[index + template.count] - prefix[index]
            let denominator = sqrt(max(signalEnergy * Double(templateEnergy), 1e-20))
            let score = abs(Double(correlation[index])) / denominator
            if score > bestScore {
                bestScore = score
                bestIndex = index
            }
        }
        let offsetNanos = UInt64(Double(bestIndex * decimation) / sampleRate * 1_000_000_000)
        return (firstSampleNanos + offsetNanos, min(1, bestScore))
    }
}

private struct PhaseAccumulator {
    private var weighting = AWeightingFilter()
    private var lowBand = BandPassFilter(lowHz: 80, highHz: 500)
    private var presenceBand = BandPassFilter(lowHz: 1_000, highHz: 5_000)
    private var weightedSum = 0.0
    private var lowSum = 0.0
    private var presenceSum = 0.0
    private(set) var peak = 0.0
    private(set) var count: Int64 = 0

    var weightedEnergy: Double { count == 0 ? 0 : weightedSum / Double(count) }
    var lowEnergy: Double { count == 0 ? 0 : lowSum / Double(count) }
    var presenceEnergy: Double { count == 0 ? 0 : presenceSum / Double(count) }

    mutating func consume(sample: Double, sampleRate: Double, trackPeak: Bool) {
        let weighted = weighting.process(sample, sampleRate: sampleRate)
        let low = lowBand.process(sample, sampleRate: sampleRate)
        let presence = presenceBand.process(sample, sampleRate: sampleRate)
        weightedSum += weighted * weighted
        lowSum += low * low
        presenceSum += presence * presence
        if trackPeak { peak = max(peak, abs(sample)) }
        count += 1
    }
}

private struct AWeightingFilter {
    private var highPass20 = Biquad()
    private var highPass108 = FirstOrderHighPass()
    private var highPass738 = FirstOrderHighPass()
    private var lowPass12k = Biquad()
    private var configuredRate = 0.0

    mutating func process(_ input: Double, sampleRate: Double) -> Double {
        if sampleRate != configuredRate {
            highPass20.configure(type: .highPass, frequency: 20.598997, q: 0.5, sampleRate: sampleRate)
            highPass108.configure(frequency: 107.65265, sampleRate: sampleRate)
            highPass738.configure(frequency: 737.86223, sampleRate: sampleRate)
            lowPass12k.configure(type: .lowPass, frequency: 12_194.217, q: 0.5, sampleRate: sampleRate)
            configuredRate = sampleRate
        }
        let value = highPass20.process(input)
        return lowPass12k.process(highPass738.process(highPass108.process(value))) * 1.2588966
    }
}

private struct BandPassFilter {
    private let lowHz: Double
    private let highHz: Double
    private var highPass = Biquad()
    private var lowPass = Biquad()
    private var configuredRate = 0.0

    init(lowHz: Double, highHz: Double) {
        self.lowHz = lowHz
        self.highHz = highHz
    }

    mutating func process(_ input: Double, sampleRate: Double) -> Double {
        if sampleRate != configuredRate {
            highPass.configure(type: .highPass, frequency: lowHz, q: 0.70710678, sampleRate: sampleRate)
            lowPass.configure(type: .lowPass, frequency: highHz, q: 0.70710678, sampleRate: sampleRate)
            configuredRate = sampleRate
        }
        return lowPass.process(highPass.process(input))
    }
}

private struct FirstOrderHighPass {
    private var b0 = 1.0
    private var b1 = 0.0
    private var a1 = 0.0
    private var x1 = 0.0
    private var y1 = 0.0

    mutating func configure(frequency: Double, sampleRate: Double) {
        let k = tan(.pi * frequency / sampleRate)
        b0 = 1 / (1 + k)
        b1 = -b0
        a1 = (k - 1) / (k + 1)
        x1 = 0
        y1 = 0
    }

    mutating func process(_ input: Double) -> Double {
        let output = b0 * input + b1 * x1 - a1 * y1
        x1 = input
        y1 = output
        return output
    }
}

private struct Biquad {
    enum FilterType { case lowPass, highPass }
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    mutating func configure(type: FilterType, frequency: Double, q: Double, sampleRate: Double) {
        let k = tan(.pi * min(frequency, sampleRate * 0.45) / sampleRate)
        let norm = 1 / (1 + k / q + k * k)
        if type == .lowPass {
            b0 = k * k * norm
            b1 = 2 * b0
            b2 = b0
        } else {
            b0 = norm
            b1 = -2 * norm
            b2 = norm
        }
        a1 = 2 * (k * k - 1) * norm
        a2 = (1 - k / q + k * k) * norm
        x1 = 0; x2 = 0; y1 = 0; y2 = 0
    }

    mutating func process(_ input: Double) -> Double {
        let output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = input; y2 = y1; y1 = output
        return output
    }
}

func hostNanos() -> UInt64 {
    UInt64(max(0, AVAudioTime.seconds(forHostTime: mach_absolute_time()) * 1_000_000_000))
}

enum AudioMeterError: LocalizedError {
    case microphonePermissionDenied
    case builtInMicrophoneRequired
    case unsupportedCaptureFormat
    case scheduleTooLate

    var errorDescription: String? {
        switch self {
        case .microphonePermissionDenied: "Microphone permission is required."
        case .builtInMicrophoneRequired: "Disconnect AirPods and the car microphone. The built-in iPhone microphone is required."
        case .unsupportedCaptureFormat: "The built-in microphone returned an unsupported audio format."
        case .scheduleTooLate: "The Bluetooth command arrived too late to arm the requested window."
        }
    }
}

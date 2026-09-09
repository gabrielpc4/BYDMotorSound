import Foundation
import UIKit

@MainActor
final class MeterViewModel: ObservableObject {
    @Published private(set) var measurementModeActive = false
    @Published private(set) var connected = false
    @Published private(set) var paired = KeychainStore.loadToken() != nil
    @Published private(set) var liveLevelDb = -70.0
    @Published private(set) var activeCar: String?
    @Published private(set) var activePerspective = ""
    @Published private(set) var measurementState = "Waiting"
    @Published private(set) var routeDescription = "Built-in microphone"
    @Published private(set) var captureFormat = "Capture not started"
    @Published private(set) var errorMessage: String?

    let pairingCode: String

    private let instanceId: UUID
    private var audioEngine: AudioMeasurementEngine?
    private var peripheral: MeterPeripheral?
    private var openingMeasurementMode = false

    init() {
        let savedId = UserDefaults.standard.string(forKey: "meterInstanceId").flatMap(UUID.init(uuidString:))
        let id = savedId ?? UUID()
        instanceId = id
        UserDefaults.standard.set(id.uuidString, forKey: "meterInstanceId")
        pairingCode = String(Int.random(in: 100_000...999_999))
    }

    func openMeasurementMode() {
        guard !openingMeasurementMode && !measurementModeActive else { return }
        openingMeasurementMode = true
        errorMessage = nil
        let audio = AudioMeasurementEngine()
        audio.onLiveLevel = { [weak self] value in self?.liveLevelDb = value }
        audio.onFatalError = { [weak self] error in
            self?.stopMeasurementMode(error: error)
        }
        audio.requestPermissionAndStart { [weak self] result in
            guard let self else { return }
            self.openingMeasurementMode = false
            switch result {
            case .success:
                self.audioEngine = audio
                self.routeDescription = audio.routeDescription
                self.captureFormat = String(
                    format: "%.0f kHz • %@",
                    audio.sampleRate / 1_000,
                    audio.channelCount >= 2 ? "stereo" : "mono"
                )
                self.startPeripheral(audio: audio)
                self.measurementModeActive = true
                UIApplication.shared.isIdleTimerDisabled = true
            case let .failure(error):
                self.errorMessage = error.localizedDescription
                audio.stop()
            }
        }
    }

    func emergencyStop() {
        stopMeasurementMode(error: "Measurement stopped on the iPhone.")
        measurementState = "Stopped"
    }

    func applicationEnteredBackground() {
        guard measurementModeActive else { return }
        stopMeasurementMode(error: "Measurement stopped because the app left the foreground.")
    }

    private func startPeripheral(audio: AudioMeasurementEngine) {
        let peripheral = MeterPeripheral(
            audioEngine: audio,
            pairingCode: Int32(pairingCode) ?? 0,
            instanceId: instanceId,
            model: hardwareModel()
        )
        peripheral.onConnectionChanged = { [weak self] value in self?.connected = value }
        peripheral.onPairingChanged = { [weak self] value in self?.paired = value }
        peripheral.onActiveMeasurement = { [weak self] car, perspective, state in
            self?.activeCar = car
            self?.activePerspective = perspective
            self?.measurementState = state
        }
        peripheral.onError = { [weak self] message in self?.errorMessage = message }
        self.peripheral = peripheral
        peripheral.start()
    }

    private func stopMeasurementMode(error: String) {
        openingMeasurementMode = false
        peripheral?.stop(reason: error)
        peripheral = nil
        audioEngine?.stop()
        audioEngine = nil
        connected = false
        activeCar = nil
        measurementModeActive = false
        errorMessage = error
        UIApplication.shared.isIdleTimerDisabled = false
    }

    private func hardwareModel() -> String {
        var size = 0
        sysctlbyname("hw.machine", nil, &size, nil, 0)
        var machine = [CChar](repeating: 0, count: size)
        sysctlbyname("hw.machine", &machine, &size, nil, 0)
        return String(cString: machine)
    }
}

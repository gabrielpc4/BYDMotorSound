import SwiftUI

struct MeterView: View {
    @ObservedObject var model: MeterViewModel

    var body: some View {
        ZStack {
            Color(red: 0.035, green: 0.045, blue: 0.055).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 24) {
                    header
                    if model.measurementModeActive {
                        activeContent
                    } else {
                        setupContent
                    }
                }
                .padding(24)
            }
        }
        .preferredColorScheme(.dark)
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)) { _ in
            model.applicationEnteredBackground()
        }
    }

    private var header: some View {
        VStack(spacing: 8) {
            Text("ENGINE LOUDNESS")
                .font(.system(size: 13, weight: .black, design: .rounded))
                .foregroundStyle(.orange)
                .tracking(3)
            Text("iPhone Meter")
                .font(.system(size: 34, weight: .bold, design: .rounded))
            Text("Audio stays on this iPhone and is never saved.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
    }

    private var setupContent: some View {
        VStack(spacing: 18) {
            Image(systemName: "iphone.gen3.radiowaves.left.and.right")
                .font(.system(size: 66))
                .foregroundStyle(.orange)
            Text("Place the iPhone upright at driver head height with its microphones unobstructed.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            Button("OPEN MEASUREMENT MODE") {
                model.openMeasurementMode()
            }
            .buttonStyle(.borderedProminent)
            .tint(.orange)
            model.errorMessage.map {
                Text($0).font(.footnote).foregroundStyle(.red).multilineTextAlignment(.center)
            }
        }
        .padding(24)
        .background(.white.opacity(0.055), in: RoundedRectangle(cornerRadius: 20))
    }

    private var activeContent: some View {
        VStack(spacing: 18) {
            statusCard
            pairingCard
            liveMeter
            currentPair
            if let error = model.errorMessage {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            }
            Button(role: .destructive) {
                model.emergencyStop()
            } label: {
                Label("EMERGENCY STOP", systemImage: "stop.circle.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
        }
    }

    private var statusCard: some View {
        HStack {
            Circle()
                .fill(model.connected ? Color.green : Color.orange)
                .frame(width: 12, height: 12)
            VStack(alignment: .leading, spacing: 3) {
                Text(model.connected ? "CONNECTED TO BYD" : "WAITING FOR BYD")
                    .font(.caption.weight(.black))
                Text(model.routeDescription)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(model.captureFormat)
                .font(.caption2.monospaced())
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .background(.white.opacity(0.055), in: RoundedRectangle(cornerRadius: 16))
    }

    private var pairingCard: some View {
        VStack(spacing: 8) {
            Text("PAIRING CODE")
                .font(.caption2.weight(.black))
                .foregroundStyle(.secondary)
            Text(model.pairingCode)
                .font(.system(size: 36, weight: .black, design: .monospaced))
                .tracking(7)
            Text(model.paired ? "This iPhone has a saved secure link." : "Enter this code on the BYD screen.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(18)
        .background(.orange.opacity(0.10), in: RoundedRectangle(cornerRadius: 16))
    }

    private var liveMeter: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack {
                Text("LIVE LEVEL").font(.caption.weight(.black)).foregroundStyle(.secondary)
                Spacer()
                Text(String(format: "%.1f dBFS", model.liveLevelDb))
                    .font(.body.monospacedDigit().weight(.bold))
            }
            ProgressView(value: min(max((model.liveLevelDb + 70) / 70, 0), 1))
                .tint(model.liveLevelDb > -3 ? .red : .orange)
        }
        .padding(16)
        .background(.white.opacity(0.055), in: RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private var currentPair: some View {
        if let car = model.activeCar {
            VStack(spacing: 7) {
                Text(car).font(.title3.weight(.bold)).multilineTextAlignment(.center)
                Text(model.activePerspective)
                    .font(.caption.weight(.black))
                    .foregroundStyle(.orange)
                Text(model.measurementState)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(18)
            .background(.white.opacity(0.055), in: RoundedRectangle(cornerRadius: 16))
        }
    }
}

import SwiftUI

@main
struct EngineLoudnessMeterApp: App {
    @StateObject private var model = MeterViewModel()

    var body: some Scene {
        WindowGroup {
            MeterView(model: model)
        }
    }
}

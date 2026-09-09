# Engine Loudness Meter

Private iOS 17 companion for the Android modded-car acoustic calibration. The iPhone acts as a Bluetooth LE peripheral and uses only its built-in microphone. It requests the built-in stereo polar pattern when the hardware exposes it and otherwise uses mono. Audio is analyzed per channel in memory; the app sends aggregate energy, channel balance, peak, timing, sample-count, and capture-format values, and never writes or transfers PCM.

Open `EngineLoudnessMeter.xcodeproj` in Xcode, connect the iPhone, and run the `EngineLoudnessMeter` scheme. Automatic signing uses the configured local development team. The project can be regenerated with `xcodegen generate` from this directory.

In the app, open measurement mode and leave it in the foreground. On the BYD screen, open Settings → Loudness, pair the advertised iPhone, enter its six-digit code on first use, and choose **Calibrate with iPhone**. The resulting acoustic adjustment applies the full inverse difference from the catalog median on top of the valid FMOD loudness normalization for each modded car and perspective.

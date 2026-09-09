# Handoff: APP VOLUME and engine loudness normalization

## Goal

Replace the mixer’s current global `OVERALL`/`GLOBAL GAIN` behavior with a real
application-level output volume, independent from Android’s shared multimedia volume.

The new system must also provide a manual batch calibration that makes different cars
and listening perspectives play at a comparable perceived engine loudness.

Baseline when this handoff was written: `main` at commit `8e95f41`.

## Product decisions

### APP VOLUME

- Replace the global mixer `OVERALL` / `GLOBAL GAIN`.
- Range: **0–200%**.
- Default: **100%**.
- `100%` is unity gain.
- `200%` is `2.0x`, approximately `+6.02 dB`.
- Apply it once to the complete FMOD output, after events and one-shots are mixed.
- It must affect Studio events and Core one-shots, including bundled shift and backfire
  WAV files.
- It must not change `AudioManager.STREAM_MUSIC`. Android multimedia volume remains a
  separate device-level control.
- Protect final output with a master hard limiter.

The per-car/per-perspective `SPECIFIC > OVERALL` control remains available as an
intentional manual trim, but it must also be applied exactly once at the master stage.

### Loudness calibration

- Calibration starts only when the user presses **CALIBRATE ALL CARS**.
- Do not start calibration automatically on app launch.
- Once started, it automatically processes every installed car and both perspectives:
  `CABIN` and `EXTERIOR`.
- The output must remain silent throughout calibration.
- Show current car, perspective, completed/total count, progress, and a cancel action.
- Save each completed measurement immediately so an interrupted run can resume.
- Restore the previously selected car, perspective, audio state, and live drive state
  after completion or cancellation.

### Engine-only measurement

Calibration must measure **only the selected engine sound**:

- `CABIN`: measure `engine_int`.
- `EXTERIOR`: measure `engine_ext`.
- Exclude transmission, transmission exterior, gear shifts, gear grind, turbo,
  supercharger, limiter event, backfires, traction control, ignition/start event, and
  bundled override WAV files.
- Exclude app-side host/category/speed gains and per-car manual trims during measurement.
- Preserve the authored internal layers that are genuinely part of the selected engine
  event. Embedded sources already classified by the app as a separate supercharger
  category must be muted for calibration.

The calibration stimulus is a deterministic, full-throttle, five-second sweep from the
car’s landing RPM to its limiter. There is no additional landing-to-limiter boost in
normal playback.

## Why the current volume path must be replaced

The current global value is not a real output volume. It is pre-multiplied into gains in
[`AudioMixGains.kt`](../mobile/src/main/java/com/gabrielpc/enginesoundsimulator/audio/AudioMixGains.kt)
and then sent to individual FMOD events/channels.

There is also an existing multiplication defect:

1. `effectiveHostGains()` includes global and specific `overall`.
2. `effectiveCategoryGains()` includes the same values again.
3. Native effects use `effectsHost × categoryGain`.

As a result, engine events receive `overall` once, while many effects receive it twice.
Bundled Core shift/backfire WAV files bypass the current overall value entirely.

The replacement must remove both overall values from host/category/idle calculations and
use this one final formula:

```text
masterLinear =
    appVolumeLinear
    × calibratedNormalizationLinear
    × carSpecificOverallLinear
```

Category trims remain category trims. APP VOLUME and per-car OVERALL must not be folded
back into `setHostGains()`, `setCategoryGains()`, or embedded-channel multipliers.

## Relevant architecture

The normal audio path is:

```text
DriveController
  → EngineAudioEngine control worker
  → NativeFmodBankBridge JNI
  → FmodRuntime
  → FMOD Studio events + FMOD Core one-shots
  → Android multimedia stream
```

Important files:

- [`DriveController.kt`](../mobile/src/main/java/com/gabrielpc/enginesoundsimulator/drive/DriveController.kt):
  UI state, persistence coordination, car/perspective changes, and mixer synchronization.
- [`EngineAudioEngine.kt`](../mobile/src/main/java/com/gabrielpc/enginesoundsimulator/audio/EngineAudioEngine.kt):
  owns FMOD’s control worker and serializes JNI calls.
- [`NativeFmodBankBridge.kt`](../mobile/src/main/java/com/gabrielpc/enginesoundsimulator/audio/NativeFmodBankBridge.kt):
  Kotlin JNI boundary.
- [`fmod_bank_bridge.cpp`](../mobile/src/main/cpp/fmod_bank_bridge.cpp):
  owns `FMOD::Studio::System` and `FMOD::System`.
- [`MixerGlobalGains.kt`](../mobile/src/main/java/com/gabrielpc/enginesoundsimulator/audio/MixerGlobalGains.kt):
  current global model and repository.
- [`MixerCarSpecificGains.kt`](../mobile/src/main/java/com/gabrielpc/enginesoundsimulator/audio/MixerCarSpecificGains.kt):
  per-car/per-perspective controls.
- [`DashboardScreens.kt`](../mobile/src/main/java/com/gabrielpc/enginesoundsimulator/DashboardScreens.kt):
  mixer and Settings UI.
- [`SettingsExporter.kt`](../mobile/src/main/java/com/gabrielpc/enginesoundsimulator/drive/SettingsExporter.kt):
  persisted-settings export.
- [`AppPreferenceStores.kt`](../mobile/src/main/java/com/gabrielpc/enginesoundsimulator/AppPreferenceStores.kt):
  preference namespaces.
- [`AssettoPhysics.kt`](../mobile/src/main/java/com/gabrielpc/enginesoundsimulator/simulation/AssettoPhysics.kt):
  limiter, automatic-shift thresholds, ratios, and final drive.
- [`AutomaticTransmissionPolicy.kt`](../mobile/src/main/java/com/gabrielpc/enginesoundsimulator/simulation/AutomaticTransmissionPolicy.kt):
  relocated upshift threshold used by normal driving.

FMOD is version **2.03.14**. The generated SDK headers are staged by
`prepareFmodSdk`; see [`mobile/build.gradle.kts`](../mobile/build.gradle.kts) and
[`docs/architecture.md`](architecture.md).

## Native master DSP design

After `studio_->initialize(...)`, obtain the Core master channel group with
`core_->getMasterChannelGroup(...)`.

Create an explicit ordered DSP chain:

```text
FMOD mixed signal
  → calibration loudness meter
  → master fader
  → hard limiter
  → calibration output gate
  → output meter
  → Android device
```

Recommended FMOD building blocks:

- `FMOD_DSP_TYPE_LOUDNESS_METER` for calibration.
- `FMOD_DSP_TYPE_FADER` for the combined master gain.
- `FMOD_DSP_TYPE_LIMITER` with linked stereo channels.
- A second loudness meter or DSP output metering after the limiter/gate for the dashboard.

Configure the limiter with:

- ceiling around `-1 dBFS`;
- linked stereo mode;
- a moderate release, initially `50 ms`;
- no limiter maximizer gain.

Use explicit DSP ordering rather than relying on event-level `setVolume()`. Release every
created DSP and clear pointers in all close/error paths.

Add native/JNI operations with semantic names, for example:

```text
setMasterOutputGain(appVolume, normalizationGain, carSpecificOverall)
setCalibrationOutputMuted(muted)
beginEngineLoudnessMeasurement(perspective)
resetEngineLoudnessMeasurement()
engineLoudnessMeasurement()
masterOutputMeter()
```

Exact API grouping may differ, but all calls must remain serialized by
`EngineAudioEngine`’s worker. UI code must not call the native bridge directly.

The existing `masterOutputLevel()` sums `Channel::getAudibility()` values and labels the
result like a final output level. It is not PCM loudness or true dBFS. Replace its source
with post-limiter DSP metering while preserving the lightweight atomic value consumed by
the dashboard.

## Calibration stimulus

### Landing RPM

Use a stable bank-derived landing RPM, independent from the dashboard’s virtual 6/10/15
gear selection:

1. Resolve the same relocated racing upshift RPM used by
   `AutomaticTransmissionPolicy.relocatedShiftThresholds(...)`.
2. If the bank has at least two authored forward ratios:

```text
landingRpm =
    relocatedUpshiftRpm
    × abs(secondGearRatio / firstGearRatio)
```

3. Clamp it to `[idleRpm, relocatedUpshiftRpm]`.
4. For a malformed/single-ratio bank, fall back to the resolved downshift threshold,
   clamped to the same range.

Put this derivation in a shared, testable helper rather than duplicating private
`AssettoDrivetrain` logic.

### Sweep

For each car and perspective:

1. Load the bank and wait for `engineSampleDataReady()`.
2. Select only `engine_int` or `engine_ext`.
3. Gate final output to zero.
4. Hold landing RPM briefly to let samples and authored automation settle.
5. Reset the loudness meter.
6. Sweep linearly from landing RPM to limiter for five seconds at full throttle.
7. Feed deterministic drivetrain speed consistent with the authored second-gear ratio.
8. Keep all pulse counters and auxiliary triggers at zero.
9. Read integrated LUFS and maximum true peak.
10. Persist the result before continuing.

The meter must be before APP VOLUME, per-car OVERALL, normalization, limiter, and silent
output gate. This makes every measurement comparable while the user hears nothing.

## Normalization math

Store raw measurements first. After the batch has valid results:

1. Build one set containing every valid `{car, CABIN}` and `{car, EXTERIOR}` integrated
   LUFS value.
2. Use the median as the catalog reference because it is resistant to unusually loud or
   quiet banks.
3. Cap the reference at a safe fixed ceiling, initially `-18 LUFS`:

```text
targetLufs = min(catalogMedianLufs, -18)
normalizationDb = targetLufs - measuredLufs
normalizationLinear = 10 ^ (normalizationDb / 20)
```

4. Bound pathological amplification, initially at `+12 dB`. Loud cars may be attenuated
   further because attenuation does not add noise or compression.
5. Keep maximum true peak in the record for diagnostics and verification.
6. Let the final limiter catch remaining peaks, especially when APP VOLUME exceeds 100%.

Do not implement a continuously adapting automatic-gain controller. It would react to
throttle changes and create audible pumping, defeating the engine’s authored dynamics.

## Cache and invalidation

Create a dedicated normalization repository. Do not write generated normalization gains
into `MixerCarSpecificGains`.

Each entry needs at least:

```text
profileId
perspective
algorithmVersion
bankFingerprint
measuredIntegratedLufs
measuredMaxTruePeak
normalizationDb
measuredAtEpochMs
```

The fingerprint must change when any input affecting calibration changes. Include the
car bank, shared common banks, physics file, and calibration algorithm version. Prefer
the SHA-256 values already present in validated package manifests over hashing large bank
files on every app launch.

Runtime behavior:

- valid record: apply its normalization;
- missing/stale record: use unity normalization and show it as uncalibrated;
- cancelled batch: retain completed entries;
- reset-all: reset APP VOLUME and clear calibration records;
- imported/replaced bank: mark only affected entries stale.

## Batch ownership and concurrency

The FMOD Android wrapper and native runtime are process-global. Calibration must not run
beside normal playback.

A suitable design is a dedicated calibration worker owned by `EngineAudioEngine`:

1. Snapshot whether normal audio is running.
2. Stop/join the normal FMOD control worker.
3. Initialize FMOD once for the batch.
4. Open/close each car bank serially; measure both perspectives before moving on.
5. Close FMOD cleanly.
6. Restart normal playback if it was previously active.

Do not mutate car-picker navigation/history just to calibrate. Pass the installed profile
list directly to the worker and expose progress through immutable state/atomics or a
thread-safe callback.

## Models and persistence

Add small single-purpose types rather than extending mixer gain objects with unrelated
calibration state:

- `AppVolumeSettings` and `AppVolumeRepository`;
- `LoudnessCalibrationRecord`;
- `LoudnessNormalizationRepository`;
- `LoudnessCalibrationProgress`;
- `FmodLoudnessCalibrationRunner` or an equivalent worker owned by `EngineAudioEngine`;
- pure helper for calibration target/gain math;
- pure helper for landing-RPM calculation.

Migration:

- Read the old global `MixerGlobalGains.overall`.
- Convert `oldOverall × 100` to APP VOLUME percent.
- Clamp to `0–200%`.
- Mark migration complete so resetting the new value does not re-import the old one.
- Remove `overall` from `MixerGlobalGains` after call sites and exported JSON are updated.
- Keep `MixerCarSpecificGains.overall`, but move its effect to the master formula so it is
  no longer squared for effects.

Export APP VOLUME and calibration records/metadata through `SettingsExporter`.

## UI behavior

In the GLOBAL mixer scope:

- Replace `GLOBAL GAIN` with `APP VOLUME`.
- Display values as percentages, not `x`.
- Keep it at the top because it affects all app audio.
- Show `0%`, `100%`, and `200%` clearly.

In Settings, add a loudness-normalization section containing:

- calibrated count versus total car/perspective pairs;
- stale/missing count;
- `CALIBRATE ALL CARS`;
- confirmation with estimated duration;
- active car and perspective;
- progress bar;
- cancel action;
- completion/failure summary.

The calibration action is manual. Do not start it simply because results are missing.

## Acceptance criteria

### APP VOLUME

- At 100%, master gain is unity before normalization and specific trim.
- At 200%, a non-limited test signal rises by approximately 6 dB.
- At 0%, all app audio is silent.
- Studio events and Core shift/backfire one-shots respond to APP VOLUME.
- Android multimedia volume is unchanged when moving APP VOLUME.
- APP VOLUME never changes event/category gain values.
- Per-car OVERALL affects the final mix exactly once.
- Effects no longer receive squared overall multiplication.
- The final limiter prevents output from exceeding its configured ceiling.

### Calibration

- The user hears silence for the full batch.
- Source diagnostics prove only the active engine event contributes to calibration.
- Every installed car is measured in CABIN and EXTERIOR.
- The measured interval is landing RPM through limiter.
- No shift, backfire, limiter-event, turbo, supercharger, transmission, start, or override
  source contributes.
- Cancelling preserves completed records and restores normal audio.
- Changed banks invalidate their records without invalidating unrelated cars.
- Replaying the calibration sweep after normalization places representative cars within
  approximately `±1.5 LU` of the chosen target.

## Verification

Add focused unit tests for:

- APP VOLUME percentage/linear conversion and migration;
- master composition with normalization and specific overall;
- removal of overall from host/category calculations;
- landing-RPM derivation and fallback;
- median target and LUFS-to-linear conversion;
- correction bounds;
- fingerprint and algorithm-version invalidation;
- resumable progress bookkeeping.

Exercise the native path on the emulator with at least:

- one naturally loud bank;
- one naturally quiet bank;
- one turbo/supercharger bank to prove auxiliary sources are excluded;
- CABIN and EXTERIOR;
- APP VOLUME at 0%, 100%, and 200%;
- bundled shift/backfire overrides after calibration.

Use the repository’s normal finish workflow after implementation.

# Engine Sounds Simulator for BYD

A private Android dashboard experiment for a BYD Seal DiLink head unit. It reads vehicle signals
only when the vendor API permits them, or uses built-in simulator pedals, to drive a fictional
combustion-engine tachometer and direct FMOD Studio-bank audio. It does not control the vehicle.

The app is designed for the 1920 × 942 px safe dashboard area on the BYD multimedia panel.
The local BYD AVD uses 70% of that canvas (1344 × 659) so the emulator matches the head-unit scaling.
Audio is intentionally delivered as fixed true stereo; the vehicle DSP distributes that route to
the factory speakers.

## Start here

The durable context for a future developer or LLM is in [docs/README.md](docs/README.md):

- [Architecture](docs/architecture.md)
- [Vehicle integration and local assets](docs/vehicle-integration-and-assets.md)
- [FMOD bank installation](docs/fmod-bank-installation.md)

Those documents state the project boundaries and point back to the code as the source of truth.

## Build

Set `fmod.sdk.dir` in `local.properties` to the supplied Android FMOD Studio API. Generate bank
packages first:

```sh
python3 tools/build_fmod_bank_packs.py
```

### Unified dashboard

One app, both catalogs. The car picker switches Original / Modded next to search.

```sh
./gradlew :mobile:assembleSeparateRelease --no-daemon
```

- Dashboard: `com.gabrielpc.enginesoundsimulator`

### Bank installer APKs

Two companion APKs write banks into the unified dashboard through its Content Provider
(`content://com.gabrielpc.enginesoundsimulator.fmodbanks`). Install the dashboard first, then
each installer and press Install.

```sh
./gradlew :audio-installer:assembleOriginalRelease :audio-installer:assembleModdedRelease --no-daemon
```

- Original banks: `com.gabrielpc.enginesoundsinstaller.original`
- Modded banks: `com.gabrielpc.enginesoundsinstaller.modded`

File-manager import remains available as an alternative:

```sh
python3 tools/export_file_manager_car_packs.py --groups all
```

## Install (emulator / test device)

```sh
adb install --bypass-low-target-sdk-block -r mobile/build/outputs/apk/<flavor>/<type>/engine-sounds-simulator-build-<number>-<variant>.apk
```

Use the app only while parked or in a controlled environment. Its audio can mask navigation,
alerts, and other safety cues.

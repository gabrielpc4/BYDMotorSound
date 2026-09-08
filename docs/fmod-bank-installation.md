# FMOD bank installation

## Unified dashboard

The product app is the `separate` variant. It exposes both catalogs and switches them in the car
picker. Banks do not ship inside this APK.

| Piece | Application ID | Role |
| --- | --- | --- |
| Dashboard | `com.gabrielpc.enginesoundsimulator` | UI, FMOD playback, private bank store |
| Original installer | `com.gabrielpc.enginesoundsinstaller.original` | Publishes original cars plus shared banks |
| Modded installer | `com.gabrielpc.enginesoundsinstaller.modded` | Publishes modded cars plus shared banks |

```sh
python3 tools/build_fmod_bank_packs.py
./gradlew :mobile:assembleSeparateRelease --no-daemon
./gradlew :audio-installer:assembleOriginalRelease :audio-installer:assembleModdedRelease --no-daemon
```

The dashboard APK is in `mobile/build/outputs/apk/separate/release/`. The installers are
`engine-sounds-audio-installer-originalRelease.apk` and
`engine-sounds-audio-installer-moddedRelease.apk`.

Install the dashboard first. Each installer streams its `.bydbank` archives through

`content://com.gabrielpc.enginesoundsimulator.fmodbanks`

into the dashboard's private store (`files/fmod-banks/`). That is the same checksum and atomic
rename path as file-manager import. A later dashboard update keeps those files. The standalone
`.original` / `.modded` dashboard flavors are not this path: their Content Provider authorities
do not match the installers.

The picker shows every car whose pack and both shared dependencies are already published.
Shared banks are dependencies and never appear as cars.

## Switching to file-manager import

Stage archives beneath `Android/data/com.gabrielpc.enginesoundsimulator/files/fmod-bank-import/`.
A car becomes available once its own pack and both shared dependencies are in the private store.

## Package groups

`tools/build_fmod_bank_packs.py` reads official cars only from
`assetto_corsa_installation/content/cars`. It creates:

- `original_cars_pack`: official cars sourced from the Assetto Corsa installation.
- `modded_car_packs`: profiles discovered under `modded_cars`.

Both groups are importable independently. The generated modded bundle also includes the shared
original FMOD dependencies required by every selected car. Copy both bundles to make both catalogs
available. The current original-bank audit covers the usable official profiles, not the modded
group.

Each active car package contains one source `.bank`, an optional preview selected from
`preview1`, a default skin preview, or (as a last resort) `ui/dlc_preview.png`, and that car's exported
`profiles/<id>/physics.json`. Shared original `common.strings.bank` and `common.bank` packages are
included as dependencies. The generated `fmod_bank_packs/` directory is ignored and must not be
committed.

```sh
python3 tools/build_fmod_bank_packs.py --force
./gradlew :mobile:assembleSeparateRelease --no-daemon
python3 tools/export_file_manager_car_packs.py --groups all
```

## BYD file-manager installation

`manual_car_pack_bundles/` is the complete file-manager delivery folder. First install the signed dashboard APK
from `DASHBOARD_APK/` through the vehicle's enabled USB APK route; the folder explains that this
APK must not be copied to `Android/data`. Then choose either
`AUDIO_PACKS/ORIGINAL_CARS` or `AUDIO_PACKS/MODDED_CARS`, start with `BATCH_01`, and read that
batch's `COPY_TO_BYD_INTERNAL_STORAGE.txt`. The required final path is:

```text
Internal storage/Android/data/com.gabrielpc.enginesoundsimulator/files/fmod-bank-import/
```

Copy the entire `fmod-bank-import` folder there, preserving its group subfolders. Open the
dashboard while parked, wait for the import-complete message, then repeat with the next batch.
Each batch is capped at 512 MiB so importing a full catalog does not require staging the entire
catalog alongside its installed copy. The background importer validates the schema, paths, byte
counts, and SHA-256 hashes, then atomically publishes every valid archive into private app storage.
Successful staging archives are deleted from the car to avoid retaining a second multi-gigabyte
copy. A broken archive remains in the staging folder and is reported in the dashboard message.

For a single-copy USB delivery, the same `fmod-bank-import` tree may be placed directly under
`Android/data/com.gabrielpc.enginesoundsimulator/files/` before copying the whole `Android`
folder to the root of the vehicle's Internal storage. The dashboard accepts all active archives
in one pass; no installer APK or batch folders are required.

Do not copy files to `/data/user/0`; that is private Android storage and is inaccessible to a normal
file manager. The `Android/data/.../files` destination is the app-owned portion of shared internal
storage and needs no broad storage permission.

## Runtime

Common original Assetto banks are loaded before the selected car bank. The dashboard rejects old,
modified, or missing packs rather than selecting another car. FMOD starts at authored idle and
plays only the engine and drivetrain events actually present in that bank; tires, wind, chassis,
and doors are excluded. The mixer shows the resulting FMOD hierarchy without changing its gain or
routing.
## Installer APKs

The original installer now embeds the full original catalog (not a single test car) plus the
shared `assetto-common` banks. The modded installer embeds the modded catalog and the same
shared banks. Both can be installed together. Press Install after the dashboard is present.
`DELETE ALL` in either installer clears the dashboard store.

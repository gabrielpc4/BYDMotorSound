#!/bin/zsh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
AVD_NAME="BYD_Multimedia_with_Hardware_Controls"
# Main panel matches the app's 70% of 1920x1080 layout canvas (1344x756).
EMULATOR_BIN="/Users/gabrielcarvalho/Library/Android/sdk/emulator/emulator"
ADB_BIN="/Users/gabrielcarvalho/Library/Android/sdk/platform-tools/adb"
PACKAGE_ORIGINAL="com.gabrielpc.enginesoundsimulator.original"
PACKAGE_MODDED="com.gabrielpc.enginesoundsimulator.modded"
MAIN_ACTIVITY="com.gabrielpc.enginesoundsimulator.MainActivity"

if [[ ! -x "$EMULATOR_BIN" ]]; then
  echo "Android emulator not found at: $EMULATOR_BIN" >&2
  exit 1
fi

latest_debug_apk() {
  local flavor="$1"
  ls -t "$ROOT/mobile/build/outputs/apk/$flavor/debug/"*.apk(N) 2>/dev/null | head -1
}

build_debug_apk() {
  local flavor="$1"
  case "$flavor" in
    original) (cd "$ROOT" && ./gradlew :mobile:assembleOriginalDebug --quiet) ;;
    modded) (cd "$ROOT" && ./gradlew :mobile:assembleModdedDebug --quiet) ;;
    *) echo "Unknown flavor: $flavor" >&2; return 1 ;;
  esac
}

resolve_debug_apk() {
  local flavor="$1"
  local apk
  apk="$(latest_debug_apk "$flavor")"
  if [[ -n "$apk" && -f "$apk" ]]; then
    print -r "$apk"
    return 0
  fi

  echo "No ${flavor}Debug APK found. Building..." >&2
  build_debug_apk "$flavor"
  apk="$(latest_debug_apk "$flavor")"
  if [[ -z "$apk" || ! -f "$apk" ]]; then
    echo "Could not build ${flavor}Debug APK." >&2
    return 1
  fi
  print -r "$apk"
}

wait_for_boot() {
  "$ADB_BIN" wait-for-device
  until [[ "$("$ADB_BIN" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 1
  done
}

install_debug_apk() {
  local package="$1"
  local flavor="$2"
  local apk
  apk="$(resolve_debug_apk "$flavor")"

  echo "Installing ${flavor} app from $(basename "$apk")..."
  "$ADB_BIN" install --bypass-low-target-sdk-block -r "$apk" >/dev/null
  if ! "$ADB_BIN" shell pm path "$package" >/dev/null 2>&1; then
    echo "Install reported success but $package is missing." >&2
    return 1
  fi
}

ensure_debug_apps() {
  wait_for_boot
  install_debug_apk "$PACKAGE_ORIGINAL" original
  install_debug_apk "$PACKAGE_MODDED" modded
  # Debug APKs omit embedded banks; opt in when packs changed or the AVD was reset:
  #   BYD_INSTALL_BANKS=1 ./run.sh
  if [[ "${BYD_INSTALL_BANKS:-0}" == "1" ]]; then
    python3 "$ROOT/tools/install_emulator_banks.py"
  fi
  "$ADB_BIN" shell am start -n "$PACKAGE_ORIGINAL/$MAIN_ACTIVITY" >/dev/null
  echo "Dashboard apps installed and launched."
}

running_avd_serial() {
  while read -r serial; do
    [[ -z "$serial" ]] && continue

    local running_avd
    running_avd="$("$ADB_BIN" -s "$serial" emu avd name 2>/dev/null | head -n 1 | tr -d '\r')"
    if [[ "$running_avd" == "$AVD_NAME" ]]; then
      print -r "$serial"
      return 0
    fi
  done < <("$ADB_BIN" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }')
  return 1
}

if serial="$(running_avd_serial)"; then
  echo "The BYD test emulator is already running on $serial."
  ensure_debug_apps
  exit 0
fi

# Prefer the host Metal-backed renderer on this Mac. SwiftShader makes the QEMU process consume
# several whole CPU cores while rendering the 1344x756 Compose dashboard. The explicit override
# keeps the old software path available if a host GPU driver ever regresses:
#   BYD_EMULATOR_GPU=swiftshader ./run.sh
GPU_MODE="${BYD_EMULATOR_GPU:-host}"

emulator_args=(-avd "$AVD_NAME" -no-boot-anim -gpu "$GPU_MODE")
if [[ "${BYD_EMULATOR_COLD_BOOT:-0}" == "1" ]]; then
  # Cold boot wipes user-installed apps on this AVD. Only use after AVD hardware changes.
  emulator_args+=(-no-snapshot-load)
fi

"$EMULATOR_BIN" "${emulator_args[@]}" &
EMULATOR_PID=$!

cleanup() {
  if kill -0 "$EMULATOR_PID" 2>/dev/null; then
    wait "$EMULATOR_PID"
  fi
}
trap cleanup EXIT

ensure_debug_apps
wait "$EMULATOR_PID"

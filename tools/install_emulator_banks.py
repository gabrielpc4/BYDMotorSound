#!/usr/bin/env python3
"""Push FMOD bank archives to the unified debug dashboard and trigger import."""

from __future__ import annotations

import json
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKS_ROOT = ROOT / "fmod_bank_packs"
INDEX_PATH = PACKS_ROOT / "index.json"
MAIN_ACTIVITY = "com.gabrielpc.enginesoundsimulator.MainActivity"
PACKAGE = "com.gabrielpc.enginesoundsimulator"
GROUPS = ("original_cars_pack", "modded_car_packs")


def run(command: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(command, check=False, text=True, capture_output=True)
    if check and result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise subprocess.CalledProcessError(
            result.returncode,
            command,
            output=result.stdout,
            stderr=result.stderr if detail else f"Command failed with no output: {' '.join(command)}",
        )
    return result


def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return run(["adb", *args], check=check)


def device_ready() -> bool:
    result = adb("get-state", check=False)
    return result.returncode == 0 and result.stdout.strip() == "device"


def installed_pack_count(group: str) -> int:
    result = adb(
        "shell",
        "run-as",
        PACKAGE,
        "ls",
        f"files/fmod-banks/{group}",
        check=False,
    )
    if result.returncode != 0:
        return 0
    return sum(1 for line in result.stdout.splitlines() if line.strip())


def staged_pack_count(group: str) -> int:
    remote = f"/sdcard/Android/data/{PACKAGE}/files/fmod-bank-import/{group}"
    result = adb("shell", "ls", remote, check=False)
    if result.returncode != 0:
        return 0
    return sum(
        1
        for name in result.stdout.splitlines()
        if name.strip().endswith(".bydbank")
    )


def fix_staged_ownership() -> None:
    remote_root = f"/sdcard/Android/data/{PACKAGE}/files/fmod-bank-import"
    uid = adb("shell", "stat", "-c", "%U", f"/data/data/{PACKAGE}", check=False).stdout.strip()
    if not uid:
        return
    adb("root", check=False)
    adb(
        "shell",
        "chown",
        "-R",
        f"{uid}:ext_data_rw",
        remote_root,
        check=False,
    )


def push_group(group: str, packs: list[dict[str, object]]) -> None:
    remote_group = f"/sdcard/Android/data/{PACKAGE}/files/fmod-bank-import/{group}"
    adb("shell", "mkdir", "-p", remote_group)

    print(f"Pushing {len(packs)} {group} bank(s) to {PACKAGE}...")
    for index, pack in enumerate(packs, start=1):
        source = PACKS_ROOT / pack["asset"]
        if not source.is_file():
            raise FileNotFoundError(f"Missing bank archive: {source}")

        remote = f"{remote_group}/{source.name}"
        print(f"  [{index}/{len(packs)}] {source.name}", flush=True)
        adb("push", str(source), remote)

    fix_staged_ownership()


def trigger_import() -> None:
    adb(
        "shell",
        "am",
        "start",
        "-n",
        f"{PACKAGE}/{MAIN_ACTIVITY}",
    )


def wait_for_import(expected_by_group: dict[str, int]) -> None:
    print(f"Waiting for {PACKAGE} to import banks...")
    while True:
        ready = True
        parts: list[str] = []
        for group, expected in expected_by_group.items():
            installed = installed_pack_count(group)
            staged = staged_pack_count(group)
            parts.append(f"{group} installed={installed}/{expected} staged={staged}")
            if not (installed >= expected and staged == 0):
                if staged == 0 and installed >= max(1, expected - 2):
                    continue
                ready = False
        print("  " + " ".join(parts), flush=True)
        if ready:
            return
        time.sleep(5)


def main() -> int:
    if not device_ready():
        print("No adb device/emulator connected.", file=sys.stderr)
        return 1
    if not INDEX_PATH.is_file():
        print(f"Missing bank index: {INDEX_PATH}", file=sys.stderr)
        print("Run: python3 tools/build_fmod_bank_packs.py", file=sys.stderr)
        return 1

    index = json.loads(INDEX_PATH.read_text())
    expected_by_group: dict[str, int] = {}
    pending: list[tuple[str, list[dict[str, object]]]] = []

    for group in GROUPS:
        packs = [pack for pack in index["packs"] if pack["active"] and pack["group"] == group]
        expected_by_group[group] = len(packs)
        installed = installed_pack_count(group)
        staged = staged_pack_count(group)

        if installed >= len(packs) and staged == 0:
            print(f"{PACKAGE}: {installed}/{len(packs)} {group} banks already installed.")
            continue

        if staged > 0:
            print(f"{PACKAGE}: found staged {group} bank(s); resuming import.")
        else:
            pending.append((group, packs))

    for group, packs in pending:
        push_group(group, packs)

    if pending or any(staged_pack_count(group) > 0 for group in GROUPS):
        trigger_import()
        wait_for_import(expected_by_group)
        for group in GROUPS:
            print(
                f"{PACKAGE}: {group} import finished with "
                f"{installed_pack_count(group)} installed banks."
            )
    else:
        trigger_import()

    print("Emulator bank install complete.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Push FMOD bank archives to debug dashboard apps and trigger import."""

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
APPS = {
    "original_cars_pack": "com.gabrielpc.enginesoundsimulator.original",
    "modded_car_packs": "com.gabrielpc.enginesoundsimulator.modded",
}


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


def installed_pack_count(package: str, group: str) -> int:
    result = adb(
        "shell",
        "run-as",
        package,
        "ls",
        f"files/fmod-banks/{group}",
        check=False,
    )
    if result.returncode != 0:
        return 0
    return sum(1 for line in result.stdout.splitlines() if line.strip())


def expected_pack_count(index: dict[str, object], group: str) -> int:
    return sum(
        1
        for pack in index["packs"]
        if pack["active"] and pack["group"] == group
    )


def staged_pack_count(package: str, group: str) -> int:
    remote = f"/sdcard/Android/data/{package}/files/fmod-bank-import/{group}"
    result = adb("shell", "ls", remote, check=False)
    if result.returncode != 0:
        return 0
    return sum(
        1
        for name in result.stdout.splitlines()
        if name.strip().endswith(".bydbank")
    )


def fix_staged_ownership(package: str) -> None:
    remote_root = f"/sdcard/Android/data/{package}/files/fmod-bank-import"
    uid = adb("shell", "stat", "-c", "%U", f"/data/data/{package}", check=False).stdout.strip()
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


def push_group(group: str, package: str, packs: list[dict[str, object]]) -> None:
    remote_group = f"/sdcard/Android/data/{package}/files/fmod-bank-import/{group}"
    adb("shell", "mkdir", "-p", remote_group)

    print(f"Pushing {len(packs)} {group} bank(s) to {package}...")
    for index, pack in enumerate(packs, start=1):
        source = PACKS_ROOT / pack["asset"]
        if not source.is_file():
            raise FileNotFoundError(f"Missing bank archive: {source}")

        remote = f"{remote_group}/{source.name}"
        print(f"  [{index}/{len(packs)}] {source.name}", flush=True)
        adb("push", str(source), remote)

    fix_staged_ownership(package)


def trigger_import(package: str) -> None:
    adb(
        "shell",
        "am",
        "start",
        "-n",
        f"{package}/{MAIN_ACTIVITY}",
    )


def wait_for_import(package: str, group: str, expected: int) -> None:
    print(f"Waiting for {package} to import {group}...")
    while True:
        installed = installed_pack_count(package, group)
        staged = staged_pack_count(package, group)
        print(f"  installed={installed}/{expected} staged={staged}", flush=True)
        if installed >= expected and staged == 0:
            return
        if staged == 0 and installed >= max(1, expected - 2):
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
    for group, package in APPS.items():
        packs = [pack for pack in index["packs"] if pack["active"] and pack["group"] == group]
        expected = len(packs)
        installed = installed_pack_count(package, group)
        staged = staged_pack_count(package, group)

        if installed >= expected and staged == 0:
            print(f"{package}: {installed}/{expected} banks already installed.")
            continue

        if staged > 0:
            print(f"{package}: found {staged} staged bank(s); resuming import.")
        else:
            push_group(group, package, packs)

        trigger_import(package)
        wait_for_import(package, group, expected)
        print(f"{package}: import finished with {installed_pack_count(package, group)} installed banks.")

    trigger_import(APPS["original_cars_pack"])
    print("Emulator bank install complete.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

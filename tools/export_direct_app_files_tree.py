#!/usr/bin/env python3
"""Expand every generated .bydbank archive into the dashboard external files tree.

The output folder mirrors what Engine Sounds Simulator keeps at runtime after a
successful import:

  files/fmod-banks/<group>/<pack-id>/manifest.json
  files/fmod-banks/<group>/<pack-id>/bank/...
  files/fmod-banks/<group>/<pack-id>/profiles/...
  files/fmod-banks/<group>/<pack-id>/preview/...

Copy the generated ``fmod-banks`` directory into the vehicle app folder shown by
the BYD file manager:

  Internal storage/Android/data/com.gabrielpc.enginesoundsimulator/files/
"""

from __future__ import annotations

import argparse
import json
import shutil
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "fmod_bank_packs"
DEFAULT_OUTPUT = ROOT / "com.gabrielpc.enginesoundsimulator"
MANIFEST_NAME = "manifest.json"
SCHEMA = "byd-fmod-bank-pack-v3"


def active_packs(index: dict[str, object]) -> list[dict[str, object]]:
    packs = index.get("packs")
    if not isinstance(packs, list):
        raise RuntimeError("Bank-pack index has no packs list")

    selected = [
        pack
        for pack in packs
        if isinstance(pack, dict) and pack.get("active") is True
    ]
    return sorted(
        selected,
        key=lambda pack: (
            str(pack.get("group", "")),
            not bool(pack.get("dependency")),
            str(pack.get("id", "")),
        ),
    )


def pack_source(pack: dict[str, object]) -> Path:
    asset = pack.get("asset")
    if not isinstance(asset, str):
        raise RuntimeError(f"Invalid pack index entry: {pack}")
    source = SOURCE / asset
    if not source.is_file():
        raise RuntimeError(f"Missing generated bank archive: {source}")
    return source


def extract_archive(archive: Path, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(archive) as bundle:
        manifest = json.loads(bundle.read(MANIFEST_NAME).decode("utf-8"))
        if manifest.get("schema") != SCHEMA:
            raise RuntimeError(f"{archive.name} uses unsupported schema {manifest.get('schema')!r}")

        pack_id = manifest.get("id")
        group = manifest.get("group")
        if destination.name != pack_id or destination.parent.name != group:
            raise RuntimeError(
                f"{archive.name} manifest id/group ({group}/{pack_id}) "
                f"does not match destination {destination.parent.name}/{destination.name}"
            )

        for entry in manifest.get("files", []):
            if not isinstance(entry, dict):
                raise RuntimeError(f"{archive.name} has invalid manifest file entry")
            relative_path = entry.get("path")
            if not isinstance(relative_path, str):
                raise RuntimeError(f"{archive.name} has invalid manifest path")
            target = destination / relative_path
            target.parent.mkdir(parents=True, exist_ok=True)
            with bundle.open(relative_path) as source, target.open("wb") as output:
                shutil.copyfileobj(source, output)
            expected_bytes = entry.get("bytes")
            if isinstance(expected_bytes, int) and target.stat().st_size != expected_bytes:
                raise RuntimeError(f"{archive.name} size mismatch for {relative_path}")

        manifest_target = destination / MANIFEST_NAME
        with bundle.open(MANIFEST_NAME) as source, manifest_target.open("wb") as output:
            shutil.copyfileobj(source, output)


def write_instructions(output_root: Path, pack_count: int, total_bytes: int) -> None:
    (output_root / "COPY_TO_BYD.txt").write_text(
        "\n".join(
            [
                "ENGINE SOUNDS SIMULATOR — DIRECT BANK COPY",
                "",
                "This folder contains the fully expanded runtime bank store. No installer APK",
                "and no fmod-bank-import staging step are required.",
                "",
                "1. Install and open Engine Sounds Simulator once on the BYD, then close it.",
                "2. In the BYD file manager, open the app-specific external files folder:",
                "   Internal storage/Android/data/com.gabrielpc.enginesoundsimulator/files/",
                "3. Copy this folder's entire contents of:",
                "   files/fmod-banks/",
                "   into:",
                "   Internal storage/Android/data/com.gabrielpc.enginesoundsimulator/files/fmod-banks/",
                "4. Preserve the group subfolders:",
                "   original_cars_pack/",
                "   modded_car_packs/",
                "5. Reopen Engine Sounds Simulator while parked. Cars with a complete pack plus",
                "   both shared original banks should appear immediately in the picker.",
                "6. If they do not, open Settings > BANK IMPORT and press RESCAN BANKS. The",
                "   diagnostic log reports both storage paths and the first invalid pack.",
                "",
                "Archive import remains available as an alternative:",
                "   Internal storage/Android/data/com.gabrielpc.enginesoundsimulator/files/fmod-bank-import/",
                "Run:",
                "   python3 tools/export_file_manager_car_packs.py --groups all",
                "and copy each batch's fmod-bank-import tree to that Android/data location.",
                "",
                f"Expanded packs in this tree: {pack_count}",
                f"Total bytes: {total_bytes}",
                "",
                "Do not copy the dashboard APK into this files tree.",
            ]
        )
        + "\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"Output root (default: {DEFAULT_OUTPUT.name}/)",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Replace an existing output tree",
    )
    arguments = parser.parse_args()

    index_path = SOURCE / "index.json"
    if not index_path.is_file():
        raise RuntimeError("Generate fmod_bank_packs first with tools/build_fmod_bank_packs.py")

    output_root = arguments.output.resolve()
    banks_root = output_root / "files" / "fmod-banks"
    if output_root.exists():
        if not arguments.force:
            raise RuntimeError(f"{output_root} already exists. Re-run with --force to replace it.")
        shutil.rmtree(output_root)

    packs = active_packs(json.loads(index_path.read_text(encoding="utf-8")))
    total_bytes = 0
    for pack in packs:
        source = pack_source(pack)
        group = str(pack["group"])
        pack_id = str(pack["id"])
        destination = banks_root / group / pack_id
        extract_archive(source, destination)
        total_bytes += sum(
            file.stat().st_size for file in destination.rglob("*") if file.is_file()
        )
        print(f"Expanded {group}/{pack_id}")

    write_instructions(output_root, len(packs), total_bytes)
    print(f"Prepared {banks_root} ({len(packs)} packs, {total_bytes / (1024 ** 3):.2f} GiB)")
    print(f"Instructions: {output_root / 'COPY_TO_BYD.txt'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

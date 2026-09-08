#!/usr/bin/env python3
"""Stage bank archives and index for one audio-installer flavor."""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GROUPS = {"original": "original_cars_pack", "modded": "modded_car_packs"}


def prepare(group_name: str, output: Path) -> None:
    group = GROUPS[group_name]
    packs_root = ROOT / "fmod_bank_packs"
    index_path = packs_root / "index.json"
    if not index_path.is_file():
        raise FileNotFoundError(f"Missing {index_path}. Run python3 tools/build_fmod_bank_packs.py first.")

    index = json.loads(index_path.read_text())
    packs = [
        pack
        for pack in index["packs"]
        if pack["active"] and (pack["group"] == group or pack.get("dependency"))
    ]
    destination = output / "packs"
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    shutil.copy2(index_path, destination / "index.json")
    for pack in packs:
        source = packs_root / pack["asset"]
        if not source.is_file():
            raise FileNotFoundError(f"Missing bank archive: {source}")
        shutil.copy2(source, destination / pack["asset"])

    print(f"Prepared {len(packs)} packs for {group_name} installer in {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--group", choices=sorted(GROUPS), required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    prepare(arguments.group, arguments.output)

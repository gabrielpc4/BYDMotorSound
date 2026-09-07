#!/usr/bin/env python3
"""Generate the human-facing subtitle catalog for every selectable Assetto car.

The car-specific `ui_car.json` belongs to the source installation/mod package and is the
authoritative description of the exact variant the app ships.  It is deliberately preferred
over guessing from a road-car name: race, tuned, and community variants commonly do not match
the corresponding production-car specification.
"""

from __future__ import annotations

import importlib.util
import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
PACK_BUILDER = ROOT / "tools" / "build_fmod_bank_packs.py"
KOTLIN_OUTPUT = ROOT / "mobile/src/main/java/com/gabrielpc/enginesoundsimulator/audio/CarSubtitleCatalog.kt"
DOCUMENT_OUTPUT = ROOT / "docs/car-subtitles.md"


def load_pack_builder() -> Any:
    spec = importlib.util.spec_from_file_location("byd_pack_builder_subtitles", PACK_BUILDER)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"could not load {PACK_BUILDER}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def read_ui_metadata(source: Any) -> dict[str, Any]:
    candidates = (source.source_directory / "ui" / "ui_car.json", source.source_directory / "ui" / "dlc_ui_car.json")
    path = next((candidate for candidate in candidates if candidate.is_file()), None)
    if path is None:
        raise RuntimeError(f"{source.pack_id}: missing ui_car.json/dlc_ui_car.json")
    # Official Assetto files contain literal line breaks in several description strings. Python's
    # strict=False accepts those source files without changing their meaningful metadata fields.
    return json.loads(path.read_text(encoding="utf-8-sig", errors="replace"), strict=False)


def first_tag(tags: set[str], candidates: tuple[str, ...]) -> str | None:
    return next((candidate for candidate in candidates if candidate in tags), None)


def format_brl_amount(value: int) -> str:
    return f"{value:,}".replace(",", ".")


def format_brl_range(low: int, high: int | None = None) -> str:
    if high is None:
        return f"~R${format_brl_amount(low)}"
    return f"~R${format_brl_amount(low)}–{format_brl_amount(high)}"


def format_weight_kg(value: int) -> str:
    return f"{format_brl_amount(value)} kg"


def format_seconds(value: float) -> str:
    rounded = round(value, 1)
    text = f"{rounded:.1f}".replace(".", ",")
    if text.endswith(",0"):
        text = text[:-2]
    return f"{text}s 0-100"


def parse_acceleration_seconds(specs: dict[str, Any]) -> float | None:
    raw = str(specs.get("acceleration") or "").strip().lower()
    if not raw:
        return None
    match = re.search(r"(\d+(?:[.,]\d+)?)\s*s", raw)
    if match is None:
        return None
    return float(match.group(1).replace(",", "."))


def parse_torque_nm(specs: dict[str, Any]) -> float | None:
    raw = str(specs.get("torque") or "").strip().lower()
    if not raw:
        return None
    match = re.search(r"(\d+(?:[.,]\d+)?)\s*nm", raw)
    if match is None:
        return None
    return float(match.group(1).replace(",", "."))


def parse_weight_kg(specs: dict[str, Any]) -> int | None:
    raw = str(specs.get("weight") or "").strip().lower()
    if not raw:
        return None
    match = re.search(r"(\d+(?:[.,]\d+)?)\s*kg", raw)
    if match is None:
        return None
    return int(round(float(match.group(1).replace(",", "."))))


def torque_nm_to_kgfm(value_nm: float) -> int:
    return int(round(value_nm / 9.80665))


# Closest real-world 0-100 hints when a mod/ui file omits acceleration.
ACCELERATION_OVERRIDES: dict[str, float] = {
    "modded-ferrari-458-italia-gte-ferruccio": 3.4,
    "modded-nissan-350z": 5.8,
    "modded-nissan-370z-widebody": 4.2,
    "assetto-lamborghini-huracan-st": 3.2,
    "assetto-ks-maserati-mc12-gt1": 3.1,
    "assetto-ks-ruf-rt12r-awd": 3.4,
    "assetto-ks-toyota-celica-st185": 5.5,
}


def parse_power_hp(specs: dict[str, Any]) -> int | None:
    raw = str(specs.get("bhp") or "").strip().lower()
    if raw.startswith("-") or raw.startswith("?"):
        return None
    match = re.search(r"(\d[\d,]*)\s*(?:\+)?\s*b?hp", raw, re.IGNORECASE)
    if match is None:
        return None
    return int(match.group(1).replace(",", ""))


def estimate_acceleration_seconds(
    name: str,
    pack_id: str,
    specs: dict[str, Any],
    tags: set[str],
) -> float | None:
    parsed = parse_acceleration_seconds(specs)
    if parsed is not None:
        return parsed
    if pack_id in ACCELERATION_OVERRIDES:
        return ACCELERATION_OVERRIDES[pack_id]

    upper = name.upper()
    name_patterns: tuple[tuple[tuple[str, ...], float], ...] = (
        (("SF70H", "SF15-T", "SF15T", "F2004", "F138"), 2.4),
        (("FORMULA", " F1", "FA01", "TATUUS"), 2.6),
        (("919 HYBRID", "TS040", "787B", " C9 ", "LMP", "PROTOTYPE"), 2.9),
        (("GT3", "GTE", "GT2", "GTLM", "GT4", "GTR GT3", "C7R", "MC GT4", "F430 GT2", "DBRS9"), 3.3),
        (("LMS", "GT3 CUP", "RSR", "VLN", "PRAGA R1", "CLUBSPORT"), 3.5),
        (("DRIFT", "TIME ATTACK"), 4.2),
        (("STAGE 3", " ST3", " S3", "TUNED", "TUNE", "WANGAN", "NO HESI"), 3.8),
        (("HUAYRA BC",), 2.8),
        (("ALFIERI",), 4.5),
        (("SPYDER RS", "718 RS"), 4.1),
        (("190 EVO", "EVO II"), 4.7),
        (("PROJECT ONE",), 2.5),
        (("ECLIPSE GSX",), 5.9),
        (("M235I RACING",), 4.8),
        (("M8 GTLM",), 3.4),
        (("GT500",), 3.6),
    )
    for keywords, seconds in name_patterns:
        if any(keyword in upper for keyword in keywords):
            return seconds

    if {"gt3", "gt2", "gte", "gt4"} & tags:
        return 3.3
    if "prototype" in tags or any("lmp" in tag for tag in tags):
        return 2.9
    if "singleseater" in tags:
        return 2.6

    hp = parse_power_hp(specs)
    weight = parse_weight_kg(specs)
    if hp is not None and weight is not None and hp > 0:
        estimate = 5.6 + (weight / hp) * 0.85
        return max(2.4, min(12.0, round(estimate, 1)))

    return None


def performance_subtitle_parts(
    specs: dict[str, Any],
    pack_id: str,
    name: str,
    tags: set[str],
) -> list[str]:
    parts: list[str] = []

    acceleration_seconds = estimate_acceleration_seconds(name, pack_id, specs, tags)
    if acceleration_seconds is not None:
        parts.append(format_seconds(acceleration_seconds))

    torque_nm = parse_torque_nm(specs)
    if torque_nm is not None:
        parts.append(f"{torque_nm_to_kgfm(torque_nm)} kgfm")

    weight_kg = parse_weight_kg(specs)
    if weight_kg is not None:
        parts.append(format_weight_kg(weight_kg))

    return parts


def approximate_price(name: str, source_group: str, category: str) -> str | None:
    """Return a deliberately broad Brazilian-market hint, never a fake exact quote.

    Webmotors listings are sparse for race cars and most mods, so those stay explicitly without
    a market comparison.  Road-car bands are intentionally rounded to avoid false precision.
    """
    if source_group != "original_cars_pack" or category in {"GT", "GT2", "GT3", "GT4", "F1", "LMP1", "LMP2", "Race Car", "Single-Seater"}:
        return None
    upper = name.upper()
    bands = (
        (("PAGANI",), format_brl_range(8_000_000)),
        (("LAMBORGHINI", "FERRARI", "MCLAREN"), format_brl_range(2_000_000, 4_000_000)),
        (("PORSCHE", "MERCEDES-AMG", "MERCEDES SLS"), format_brl_range(700_000, 2_000_000)),
        (("AUDI R8", "NISSAN GT-R", "NISSAN SKYLINE"), format_brl_range(500_000, 900_000)),
        (("CORVETTE", "BMW M", "BMW Z4"), format_brl_range(300_000, 700_000)),
        (("ALFA", "LOTUS", "MASERATI", "RUF", "KTM"), format_brl_range(250_000, 900_000)),
        (("FORD MUSTANG", "TOYOTA SUPRA", "TOYOTA GT86"), format_brl_range(250_000, 600_000)),
        (("ABARTH", "MAZDA", "ALFA MITO", "ALFA GIULIETTA"), format_brl_range(100_000, 300_000)),
    )
    for keywords, value in bands:
        if any(keyword in upper for keyword in keywords):
            return value
    return format_brl_range(100_000, 500_000)


def build_subtitle(source: Any, metadata: dict[str, Any]) -> tuple[int | None, str]:
    specs = metadata.get("specs") if isinstance(metadata.get("specs"), dict) else {}
    car_name = str(metadata.get("name") or source.pack_id)
    tags = {str(tag).strip().lower() for tag in metadata.get("tags", [])}

    details_parts = performance_subtitle_parts(specs, source.pack_id, car_name, tags)
    horsepower = parse_power_hp(specs)

    vehicle_class = str(metadata.get("class") or "").strip().lower()
    category = first_tag(tags, ("f1", "lmp1", "lmp2", "gt1", "gt2", "gt3", "gt4", "gt", "prototype c"))
    if category is None:
        if "singleseater" in tags:
            category = "Single-Seater"
        elif vehicle_class == "race" or "race" in tags:
            category = "Race Car"
        elif "supercar" in tags:
            category = "Supercar"
        elif "street" in tags or vehicle_class == "street":
            category = "Road Car"
        else:
            category = vehicle_class.title() if vehicle_class else "Assetto Corsa car"

    price = approximate_price(car_name, source.group, category)
    if price is not None:
        details_parts.append(price)

    details = " · ".join(details_parts)
    if horsepower is None and not details:
        return None, "ASSETTO CORSA AUDIO PROFILE"
    return horsepower, details


def format_subtitle_display(horsepower: int | None, details: str) -> str:
    if horsepower is None:
        return details
    if not details:
        return f"{horsepower} HP"
    return f"{horsepower} HP · {details}"


def kotlin_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def write_outputs(rows: list[tuple[Any, dict[str, Any], int | None, str]]) -> None:
    entries = "\n".join(
        f'        "{source.pack_id}" to CarSubtitle({("null" if horsepower is None else horsepower)}, "{kotlin_escape(details)}"),'
        for source, _, horsepower, details in rows
    )
    KOTLIN_OUTPUT.write_text(
        """package com.gabrielpc.enginesoundsimulator.audio

/**
 * Human-facing facts for the exact Assetto/mod variants packaged by this project.
 *
 * Generated by `tools/generate_car_subtitles.py`; do not hand-edit. Values come from each
 * source car's `ui_car.json` performance specs (HP, 0-100, torque, weight) for the packaged
 * variant, with road-car price bands appended when a market comparison exists.
 */
internal data class CarSubtitle(
    val horsepower: Int?,
    val details: String,
)

internal object CarSubtitleCatalog {
    private val fallback = CarSubtitle(null, "ASSETTO CORSA AUDIO PROFILE")

    private val subtitles = mapOf(
""" + entries + """
    )

    fun forProfileId(profileId: String): CarSubtitle = subtitles[profileId] ?: fallback
}
""",
        encoding="utf-8",
    )
    group_counts: dict[str, int] = {}
    for source, _, _, _ in rows:
        group_counts[source.group] = group_counts.get(source.group, 0) + 1
    lines = [
        "# Car subtitles",
        "",
        "This catalog supplies the subtitle beneath the selected car on the dashboard.",
        "It currently covers every selectable profile discovered by the pack builder.",
        "",
        "## Provenance and scope",
        "",
        "- Performance facts are parsed from each included car's `ui_car.json` (or `dlc_ui_car.json`): 0-100 acceleration, torque (converted from Nm to kgfm), weight, and advertised BHP.",
        "- The public [Assetto Corsa car-data structure reference](https://github.com/aiazzi-davide/AC_Car_Editor/blob/main/assettocorsa_car_data_documentation.md) documents this metadata boundary.",
        "- The public [Assetto catalog](https://assetto.patacuack.net/cars?server=0) was used to cross-check naming. It is not used to overwrite the supplied mod variant's metadata.",
        "- When a mod omits 0-100 in its UI metadata, a closest real-world estimate is used for that model family.",
        "- Rounded road-car price bands are a Brazil-market indication informed by [Webmotors listings](https://www.webmotors.com.br/carros/estoque) (consulted 2026-09-04), not a quote or FIPE value.",
        "- Race/prototype/community variants show no market price when Webmotors has no comparable listing.",
        "- This avoids presenting a stock road-car engine claim for an Assetto race, stage, widebody or community-mod profile.",
        "",
        "## Coverage",
        "",
        *[f"- `{group}`: {count} profiles." for group, count in sorted(group_counts.items())],
        "",
        "## Generated values",
        "",
        "| Profile | Source name | Subtitle |",
        "| --- | --- | --- |",
        *[f"| `{source.pack_id}` | {metadata.get('name', source.pack_id)} | {format_subtitle_display(horsepower, details)} |" for source, metadata, horsepower, details in rows],
        "",
        "Regenerate after adding/removing a source car with:",
        "",
        "```sh\npython3 tools/generate_car_subtitles.py\n```",
    ]
    DOCUMENT_OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    builder = load_pack_builder()
    sources = [*builder.discover_original_sources(), *builder.discover_modded_sources()]
    rows = [
        (source, metadata := read_ui_metadata(source), *build_subtitle(source, metadata)[0:2])
        for source in sources
    ]
    ids = [source.pack_id for source, _, _, _ in rows]
    if len(ids) != len(set(ids)):
        raise RuntimeError("duplicate profile ids in source metadata")
    write_outputs(sorted(rows, key=lambda row: row[0].pack_id))
    print(f"generated {len(rows)} car subtitles")


if __name__ == "__main__":
    main()

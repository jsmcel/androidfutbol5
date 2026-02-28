#!/usr/bin/env python
"""Analyze likely ProManager offer input pools from current mapping + DBC slots."""

from __future__ import annotations

import argparse
import csv
import json
import struct
from collections import Counter
from pathlib import Path
from typing import Dict, List, Set


DEFAULT_MAPPING = Path(__file__).resolve().parent / "out" / "pcf55_full_mapping_global.csv"
DEFAULT_PKF = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT" / "EQUIPOS.PKF"
DEFAULT_LIGA = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT" / "LIGA.DBC"
DEFAULT_SCESPANA = (
    Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT" / "SCESPANA.DBC"
)
DEFAULT_OUT = Path(__file__).resolve().parent / "out" / "promanager_offer_inputs_report.json"

E3_COMPS = {"E3G1", "E3G2"}
SPANISH_COMPS = {"ES1", "ES2", "E3G1", "E3G2"}
CDM = b"Copyright (c)1996 Dinamic Multimedia"


def ids_in_dbc(path: Path) -> Set[int]:
    raw = path.read_bytes()
    n = len(raw) // 2
    vals = struct.unpack("<" + ("H" * n), raw[: n * 2]) if n > 0 else ()
    return {int(v) for v in vals if 1 <= v <= 260}


def load_mapping(path: Path) -> Dict[int, Dict[str, str]]:
    rows = list(csv.DictReader(path.open("r", encoding="utf-8", newline="")))
    return {int(r["pcf_index"]): r for r in rows}


def read_u8(chunk: bytes, i: int) -> tuple[int, int]:
    if i >= len(chunk):
        raise ValueError("u8 out of bounds")
    return chunk[i], i + 1


def read_u16(chunk: bytes, i: int) -> tuple[int, int]:
    if i + 1 >= len(chunk):
        raise ValueError("u16 out of bounds")
    return struct.unpack_from("<H", chunk, i)[0], i + 2


def skip_string(chunk: bytes, i: int) -> int:
    n, i = read_u16(chunk, i)
    if i + n > len(chunk):
        raise ValueError("string out of bounds")
    return i + n


def countries_from_pkf(path: Path) -> Dict[int, int]:
    blob = path.read_bytes()
    out: Dict[int, int] = {}

    i = 232
    idx = 1
    offset = 232
    while i < len(blob):
        b = blob[i]
        if b == 0x04:
            if i + 5 > len(blob):
                break
            jump_to = struct.unpack_from("<I", blob, i + 1)[0]
            i += 5
            skip = jump_to - offset - 5
            if skip < 0:
                break
            i += skip
            offset = jump_to
            continue
        if b == 0x02:
            if i + 38 > len(blob):
                break
            start = struct.unpack_from("<I", blob, i + 26)[0]
            size = struct.unpack_from("<I", blob, i + 30)[0]
            chunk = blob[start : start + size]
            try:
                if chunk.startswith(CDM):
                    j = len(CDM)
                    _, j = read_u16(chunk, j)
                    _, j = read_u16(chunk, j)
                    _, j = read_u8(chunk, j)
                    _, j = read_u8(chunk, j)
                    j = skip_string(chunk, j)
                    j = skip_string(chunk, j)
                    country, _ = read_u8(chunk, j)
                    out[idx] = int(country)
            except Exception:
                pass
            i += 38
            idx += 1
            offset += 38
            continue
        if b == 0x05 and blob[i + 1 : i + 5] == b"\x00\x00\x00\x00":
            break
        i += 1
    return out


def as_float(row: Dict[str, str], key: str) -> float:
    try:
        return float(row.get(key) or 0.0)
    except Exception:
        return 0.0


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    p.add_argument("--pkf", type=Path, default=DEFAULT_PKF)
    p.add_argument("--liga", type=Path, default=DEFAULT_LIGA)
    p.add_argument("--scespana", type=Path, default=DEFAULT_SCESPANA)
    p.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = p.parse_args()

    by_idx = load_mapping(args.mapping)
    countries = countries_from_pkf(args.pkf)
    liga_ids = ids_in_dbc(args.liga)
    sc_ids = ids_in_dbc(args.scespana)
    active_ids = sorted(liga_ids | sc_ids)

    def rows_for(ids: Set[int]) -> List[Dict[str, str]]:
        return [by_idx[i] for i in sorted(ids) if i in by_idx]

    active_rows = rows_for(set(active_ids))
    e3_rows_all = [r for r in by_idx.values() if (r.get("tm_competition") or "").upper() in E3_COMPS]
    e3_rows_active = [r for r in active_rows if (r.get("tm_competition") or "").upper() in E3_COMPS]
    spanish_rows_active = [r for r in active_rows if (r.get("tm_competition") or "").upper() in SPANISH_COMPS]
    spanish_country_vals = [
        countries[int(r["pcf_index"])]
        for r in by_idx.values()
        if (r.get("tm_competition") or "").upper() in SPANISH_COMPS and int(r["pcf_index"]) in countries
    ]
    inferred_spain_code = Counter(spanish_country_vals).most_common(1)[0][0] if spanish_country_vals else None
    active_e3_spain_country = [
        r for r in e3_rows_active
        if inferred_spain_code is not None and countries.get(int(r["pcf_index"])) == inferred_spain_code
    ]
    active_e3_non_spain_country = [
        r for r in e3_rows_active
        if inferred_spain_code is not None and countries.get(int(r["pcf_index"])) != inferred_spain_code
    ]

    # Weakness proxy by squad value (fallback team value).
    def value_row(r: Dict[str, str]) -> float:
        squad = as_float(r, "tm_squad_total_value_eur")
        team = as_float(r, "tm_team_market_value_eur")
        return squad if squad > 0 else team

    weakest_e3_active = sorted(e3_rows_active, key=value_row)[:20]
    weakest_spanish_active = sorted(spanish_rows_active, key=value_row)[:25]

    counts_active: Dict[str, int] = {}
    for r in active_rows:
        comp = (r.get("tm_competition") or "").upper()
        counts_active[comp] = counts_active.get(comp, 0) + 1

    report = {
        "inputs": {
            "mapping": str(args.mapping),
            "pkf": str(args.pkf),
            "liga": str(args.liga),
            "scespana": str(args.scespana),
        },
        "active_slot_counts": {
            "liga_ids": len(liga_ids),
            "scespana_ids": len(sc_ids),
            "union_active_ids": len(active_ids),
            "active_spanish_rows": len(spanish_rows_active),
            "active_e3_rows": len(e3_rows_active),
            "all_e3_rows": len(e3_rows_all),
            "inferred_spain_country_code": inferred_spain_code,
            "active_e3_with_spain_country": len(active_e3_spain_country),
            "active_e3_with_non_spain_country": len(active_e3_non_spain_country),
        },
        "active_counts_by_competition": dict(sorted(counts_active.items())),
        "weakest_e3_active": [
            {
                "pcf_index": int(r["pcf_index"]),
                "tm_team": r["tm_team"],
                "tm_competition": r["tm_competition"],
                "value_eur": int(value_row(r)),
            }
            for r in weakest_e3_active
        ],
        "weakest_spanish_active": [
            {
                "pcf_index": int(r["pcf_index"]),
                "tm_team": r["tm_team"],
                "tm_competition": r["tm_competition"],
                "value_eur": int(value_row(r)),
            }
            for r in weakest_spanish_active
        ],
    }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(
        "Active slots: "
        f"LIGA={len(liga_ids)}, SCESPANA={len(sc_ids)}, UNION={len(active_ids)}"
    )
    print(
        "Spanish pool: "
        f"active_spanish={len(spanish_rows_active)}, active_e3={len(e3_rows_active)}, all_e3={len(e3_rows_all)}"
    )
    print(
        "Country gate: "
        f"spain_code={inferred_spain_code}, "
        f"active_e3_spain={len(active_e3_spain_country)}, "
        f"active_e3_non_spain={len(active_e3_non_spain_country)}"
    )
    print(f"By competition: {dict(sorted(counts_active.items()))}")
    print(f"Report: {args.out}")


if __name__ == "__main__":
    main()

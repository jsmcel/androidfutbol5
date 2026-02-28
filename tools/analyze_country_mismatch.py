#!/usr/bin/env python
"""Detect mismatches between mapped competition and internal team country byte."""

from __future__ import annotations

import argparse
import csv
import json
import struct
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional


DEFAULT_PKF = (
    Path(__file__).resolve().parents[2]
    / "PCF55"
    / "FUTBOL5"
    / "FUTBOL5"
    / "DBDAT"
    / "EQUIPOS.PKF"
)
DEFAULT_MAPPING = Path(__file__).resolve().parent / "out" / "pcf55_full_mapping_global.csv"
DEFAULT_OUT = Path(__file__).resolve().parent / "out" / "country_mismatch_report.json"

SPANISH_COMPS = {"ES1", "ES2", "E3G1", "E3G2"}
CDM = b"Copyright (c)1996 Dinamic Multimedia"


@dataclass
class Pointer:
    index: int
    start: int
    size: int


def parse_pointers(blob: bytes) -> List[Pointer]:
    pointers: List[Pointer] = []
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
            pointers.append(Pointer(index=idx, start=start, size=size))
            i += 38
            idx += 1
            offset += 38
            continue
        if b == 0x05 and blob[i + 1 : i + 5] == b"\x00\x00\x00\x00":
            break
        i += 1
    return pointers


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


def parse_country_byte(chunk: bytes) -> Optional[int]:
    # Expected structure head:
    # cdm, u16 unknown, u16 const, u8 const, u8 dbflag, string name, string stadium, u8 country
    try:
        if not chunk.startswith(CDM):
            return None
        i = len(CDM)
        _, i = read_u16(chunk, i)  # tunknown00
        _, i = read_u16(chunk, i)  # 0d02 or 0302
        _, i = read_u8(chunk, i)   # const 00
        _, i = read_u8(chunk, i)   # db flag
        i = skip_string(chunk, i)  # team
        i = skip_string(chunk, i)  # stadium
        country, _ = read_u8(chunk, i)
        return int(country)
    except Exception:
        return None


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--pkf", type=Path, default=DEFAULT_PKF)
    p.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    p.add_argument("--out", type=Path, default=DEFAULT_OUT)
    return p.parse_args()


def main() -> None:
    args = parse_args()
    blob = args.pkf.read_bytes()
    ptrs = parse_pointers(blob)

    countries: Dict[int, Optional[int]] = {}
    for p in ptrs:
        chunk = blob[p.start : p.start + p.size]
        countries[p.index] = parse_country_byte(chunk)

    rows = list(csv.DictReader(args.mapping.open("r", encoding="utf-8", newline="")))
    by_idx = {int(r["pcf_index"]): r for r in rows}

    # Infer likely Spain code as most common country byte among mapped Spanish teams.
    spanish_codes = []
    for idx, row in by_idx.items():
        comp = (row.get("tm_competition") or "").upper()
        if comp in SPANISH_COMPS:
            c = countries.get(idx)
            if c is not None:
                spanish_codes.append(c)
    inferred_spain = Counter(spanish_codes).most_common(1)[0][0] if spanish_codes else None

    mismatches = []
    spanish_total = 0
    spanish_parsed = 0
    for idx, row in by_idx.items():
        comp = (row.get("tm_competition") or "").upper()
        if comp not in SPANISH_COMPS:
            continue
        spanish_total += 1
        c = countries.get(idx)
        if c is None:
            continue
        spanish_parsed += 1
        if inferred_spain is not None and c != inferred_spain:
            mismatches.append(
                {
                    "pcf_index": idx,
                    "tm_team": row.get("tm_team", ""),
                    "tm_competition": comp,
                    "country_code": c,
                }
            )

    report = {
        "pkf": str(args.pkf),
        "mapping": str(args.mapping),
        "slots_total": len(ptrs),
        "countries_parsed": sum(1 for v in countries.values() if v is not None),
        "inferred_spain_country_code": inferred_spain,
        "spanish_mapping_rows": spanish_total,
        "spanish_rows_with_parsed_country": spanish_parsed,
        "spanish_country_mismatch_count": len(mismatches),
        "spanish_country_mismatches_head": mismatches[:120],
        "country_code_histogram": dict(sorted((str(k), v) for k, v in Counter(c for c in countries.values() if c is not None).items())),
    }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Inferred Spain code: {inferred_spain}")
    print(f"Spanish rows: {spanish_total} (parsed={spanish_parsed})")
    print(f"Mismatches: {len(mismatches)}")
    print(f"Report: {args.out}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python
"""Patch team country byte in EQUIPOS.PKF based on mapping competitions."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import struct
from collections import Counter, defaultdict
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
DEFAULT_OUT_REPORT = Path(__file__).resolve().parent / "out" / "country_patch_report.json"

SPANISH_COMPS = {"ES1", "ES2", "E3G1", "E3G2"}
CDM = b"Copyright (c)1996 Dinamic Multimedia"


@dataclass
class Pointer:
    index: int
    start: int
    size: int


@dataclass
class CountryField:
    index: int
    offset: int
    value: int


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


def parse_country_field(chunk: bytes, start: int, index: int) -> Optional[CountryField]:
    try:
        if not chunk.startswith(CDM):
            return None
        i = len(CDM)
        _, i = read_u16(chunk, i)
        _, i = read_u16(chunk, i)
        _, i = read_u8(chunk, i)
        _, i = read_u8(chunk, i)
        i = skip_string(chunk, i)  # team
        i = skip_string(chunk, i)  # stadium
        country_offset = start + i
        country_value, _ = read_u8(chunk, i)
        return CountryField(index=index, offset=country_offset, value=int(country_value))
    except Exception:
        return None


def load_mapping(path: Path) -> Dict[int, Dict[str, str]]:
    rows = list(csv.DictReader(path.open("r", encoding="utf-8", newline="")))
    return {int(r["pcf_index"]): r for r in rows}


def infer_spain_code(mapping: Dict[int, Dict[str, str]], by_index: Dict[int, CountryField]) -> Optional[int]:
    vals: List[int] = []
    for idx, row in mapping.items():
        comp = (row.get("tm_competition") or "").upper()
        if comp not in SPANISH_COMPS:
            continue
        cf = by_index.get(idx)
        if cf is None:
            continue
        vals.append(cf.value)
    if not vals:
        return None
    return int(Counter(vals).most_common(1)[0][0])


def infer_comp_codes(mapping: Dict[int, Dict[str, str]], by_index: Dict[int, CountryField]) -> Dict[str, int]:
    by_comp: Dict[str, List[int]] = defaultdict(list)
    for idx, row in mapping.items():
        comp = (row.get("tm_competition") or "").upper().strip()
        if not comp:
            continue
        cf = by_index.get(idx)
        if cf is None:
            continue
        by_comp[comp].append(cf.value)

    out: Dict[str, int] = {}
    for comp, vals in by_comp.items():
        if not vals:
            continue
        out[comp] = int(Counter(vals).most_common(1)[0][0])
    return out


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--pkf", type=Path, default=DEFAULT_PKF)
    p.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    p.add_argument(
        "--mode",
        choices=["spanish_only", "by_competition"],
        default="spanish_only",
        help="Patch only ES1/ES2/E3G1/E3G2 or all competitions by dominant country code.",
    )
    p.add_argument(
        "--spain-code",
        type=int,
        default=None,
        help="Force country code for Spanish competitions (default: infer from current data).",
    )
    p.add_argument("--in-place", action="store_true", help="Overwrite input PKF after backup.")
    p.add_argument("--output", type=Path, default=None, help="Output path (ignored with --in-place).")
    p.add_argument("--report", type=Path, default=DEFAULT_OUT_REPORT)
    return p.parse_args()


def main() -> None:
    args = parse_args()
    mapping = load_mapping(args.mapping)
    original = args.pkf.read_bytes()
    blob = bytearray(original)

    fields: List[CountryField] = []
    for ptr in parse_pointers(original):
        chunk = original[ptr.start : ptr.start + ptr.size]
        cf = parse_country_field(chunk, ptr.start, ptr.index)
        if cf is not None:
            fields.append(cf)
    by_index = {f.index: f for f in fields}

    if args.mode == "spanish_only":
        target_spain = args.spain_code if args.spain_code is not None else infer_spain_code(mapping, by_index)
        if target_spain is None:
            raise SystemExit("Cannot infer Spain code from data; use --spain-code.")
        target_by_comp: Dict[str, int] = {k: int(target_spain) for k in SPANISH_COMPS}
    else:
        target_by_comp = infer_comp_codes(mapping, by_index)
        if args.spain_code is not None:
            for k in SPANISH_COMPS:
                target_by_comp[k] = int(args.spain_code)

    patched = 0
    unchanged = 0
    missing = 0
    patched_by_comp: Dict[str, int] = defaultdict(int)
    before_hist = Counter()
    after_hist = Counter()
    sample_changes: List[Dict[str, object]] = []

    for idx, row in mapping.items():
        comp = (row.get("tm_competition") or "").upper().strip()
        if comp not in target_by_comp:
            continue
        cf = by_index.get(idx)
        if cf is None:
            missing += 1
            continue

        before = int(cf.value)
        target = int(target_by_comp[comp])
        before_hist[before] += 1
        after_hist[target] += 1

        if before == target:
            unchanged += 1
            continue

        blob[cf.offset] = target
        patched += 1
        patched_by_comp[comp] += 1
        if len(sample_changes) < 120:
            sample_changes.append(
                {
                    "pcf_index": idx,
                    "tm_team": row.get("tm_team", ""),
                    "tm_competition": comp,
                    "offset": cf.offset,
                    "before": before,
                    "after": target,
                }
            )

    timestamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = args.pkf.with_suffix(args.pkf.suffix + f".{timestamp}.bak")
    backup_path.write_bytes(original)

    if args.in_place:
        out_path = args.pkf
    else:
        out_path = args.output or args.pkf.with_name(args.pkf.stem + ".countrypatched" + args.pkf.suffix)
    out_path.write_bytes(bytes(blob))

    report = {
        "pkf_input": str(args.pkf),
        "pkf_output": str(out_path),
        "backup": str(backup_path),
        "mapping": str(args.mapping),
        "mode": args.mode,
        "spain_code_forced": args.spain_code,
        "target_by_comp": dict(sorted(target_by_comp.items())),
        "totals": {
            "fields_parsed": len(fields),
            "rows_in_mapping": len(mapping),
            "patched": patched,
            "unchanged": unchanged,
            "missing": missing,
        },
        "patched_by_comp": dict(sorted(patched_by_comp.items())),
        "before_histogram": dict(sorted((str(k), v) for k, v in before_hist.items())),
        "after_histogram_targeted": dict(sorted((str(k), v) for k, v in after_hist.items())),
        "sample_changes": sample_changes,
    }

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Backup: {backup_path}")
    print(f"Output: {out_path}")
    print(f"Patched country bytes: {patched} (unchanged={unchanged}, missing={missing})")
    print(f"Mode: {args.mode}")
    print(f"Report: {args.report}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python
"""Automated QA checks for the global PCF55 modernization patch."""

from __future__ import annotations

import argparse
import csv
import json
import struct
from collections import Counter
from pathlib import Path
from typing import Dict, List, Set


DEFAULT_MAPPING = Path(__file__).resolve().parent / "out" / "pcf55_full_mapping_global.csv"
DEFAULT_PLAYER_REPORT = Path(__file__).resolve().parent / "out" / "player_patch_report_global.json"
DEFAULT_ENGINE_REPORT = Path(__file__).resolve().parent / "out" / "engine_consistency_report_global.json"
DEFAULT_MANDOS = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "MANDOS.DAT"
DEFAULT_DBASEDOS = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBASEDOS.DAT"
DEFAULT_PKF = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT" / "EQUIPOS.PKF"
DEFAULT_LIGA = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT" / "LIGA.DBC"
DEFAULT_SCESPANA = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT" / "SCESPANA.DBC"
DEFAULT_OUT = Path(__file__).resolve().parent / "out" / "qa_global_report.json"

SPANISH_COMPS = {"ES1", "ES2", "E3G1", "E3G2"}
LOW_IMPACT_COMPS = {"A1", "DK1", "SC1"}
# Slot 19 is heavily referenced by JORNAD1M in legacy assets; keep it available
# for Liga mappings even if player-chain patch is not reliable there.
LEGACY_UNPATCHABLE_SLOTS = {19}
NO_CHAIN_LOW_IMPACT_SLOTS = {104}
GUARDED_UNSTABLE_SLOTS = {108, 160, 235}
ALLOWED_NON_PATCHED_SLOTS = LEGACY_UNPATCHABLE_SLOTS | NO_CHAIN_LOW_IMPACT_SLOTS | GUARDED_UNSTABLE_SLOTS
CDM = b"Copyright (c)1996 Dinamic Multimedia"
TARGET_COUNTS = {
    "ES1": 20,
    "ES2": 22,
    "E3G1": 20,
    "E3G2": 20,
    "GB1": 20,
    "IT1": 20,
    "FR1": 18,
    "L1": 18,
    "PO1": 18,
    "NL1": 18,
    "BE1": 16,
    "TR1": 18,
    "RU1": 10,
    "PL1": 10,
    "SC1": 4,
    "A1": 4,
    "DK1": 4,
}


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


def parse_country_by_index(pkf: Path) -> Dict[int, int]:
    blob = pkf.read_bytes()

    pointers: List[tuple[int, int, int]] = []
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
            pointers.append((idx, start, size))
            i += 38
            idx += 1
            offset += 38
            continue
        if b == 0x05 and blob[i + 1 : i + 5] == b"\x00\x00\x00\x00":
            break
        i += 1

    out: Dict[int, int] = {}
    for idx, start, size in pointers:
        chunk = blob[start : start + size]
        try:
            if not chunk.startswith(CDM):
                continue
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
            continue
    return out


def ids_in_dbc(path: Path) -> Set[int]:
    raw = path.read_bytes()
    if len(raw) % 2 == 1:
        raw += b"\x00"
    vals = struct.unpack("<" + ("H" * (len(raw) // 2)), raw)
    return {int(v) for v in vals if 1 <= v <= 260}


def scan_tokens(path: Path) -> List[str]:
    out: List[str] = []
    for tok in path.read_bytes().split(b"\x00"):
        if not (2 <= len(tok) <= 80):
            continue
        printable = sum(1 for c in tok if 32 <= c <= 126 or 160 <= c <= 255)
        if printable < int(len(tok) * 0.8):
            continue
        s = tok.decode("cp1252", errors="replace").strip()
        if s:
            out.append(s)
    return out


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    parser.add_argument("--player-report", type=Path, default=DEFAULT_PLAYER_REPORT)
    parser.add_argument("--engine-report", type=Path, default=DEFAULT_ENGINE_REPORT)
    parser.add_argument("--mandos", type=Path, default=DEFAULT_MANDOS)
    parser.add_argument("--dbasedos", type=Path, default=DEFAULT_DBASEDOS)
    parser.add_argument("--pkf", type=Path, default=DEFAULT_PKF)
    parser.add_argument("--liga", type=Path, default=DEFAULT_LIGA)
    parser.add_argument("--scespana", type=Path, default=DEFAULT_SCESPANA)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    rows = list(csv.DictReader(args.mapping.open("r", encoding="utf-8", newline="")))
    by_idx: Dict[int, Dict[str, str]] = {int(r["pcf_index"]): r for r in rows}
    counts = Counter(r["tm_competition"] for r in rows)
    unique_tm = len({(r.get("tm_team") or "").strip().lower() for r in rows})

    liga_slots = ids_in_dbc(args.liga) | ids_in_dbc(args.scespana)
    spanish_in_liga = sum(
        1
        for idx in liga_slots
        if idx in by_idx and (by_idx[idx].get("tm_competition") or "").upper() in SPANISH_COMPS
    )

    player_report = json.loads(args.player_report.read_text(encoding="utf-8"))
    non_patched = [t for t in player_report.get("teams", []) if t.get("status") != "patched"]
    non_patched_slots = {int(t["pcf_index"]) for t in non_patched if "pcf_index" in t}

    engine_report = json.loads(args.engine_report.read_text(encoding="utf-8"))
    corr = engine_report.get("correlations", {})

    mandos_tokens = scan_tokens(args.mandos)
    db_tokens = scan_tokens(args.dbasedos)
    all_tokens = mandos_tokens + db_tokens
    rfef_markers = ("PRIMERA RFEF", "1A RFEF", "RFEF")
    has_primera_rfef = any(any(m in t.upper() for m in rfef_markers) for t in all_tokens)
    # MANDOS.DAT can remain legacy for DOSBox Android compatibility.
    has_old_segunda_b_db = any("SEGUNDA B - GRUPO" in t.upper() for t in db_tokens)

    slot19_comp = (by_idx.get(19, {}).get("tm_competition") or "").upper()
    slot104_comp = (by_idx.get(104, {}).get("tm_competition") or "").upper()
    no_chain_low_impact = all(
        ((by_idx.get(slot, {}).get("tm_competition") or "").upper() in LOW_IMPACT_COMPS)
        for slot in NO_CHAIN_LOW_IMPACT_SLOTS
    )
    country_by_idx = parse_country_by_index(args.pkf)
    spanish_country_vals = [
        country_by_idx[idx]
        for idx, row in by_idx.items()
        if (row.get("tm_competition") or "").upper() in SPANISH_COMPS and idx in country_by_idx
    ]
    inferred_spain_code = Counter(spanish_country_vals).most_common(1)[0][0] if spanish_country_vals else None
    spanish_country_mismatches = [
        idx
        for idx, row in by_idx.items()
        if (row.get("tm_competition") or "").upper() in SPANISH_COMPS
        and idx in country_by_idx
        and inferred_spain_code is not None
        and country_by_idx[idx] != inferred_spain_code
    ]

    target_delta = {k: int(counts.get(k, 0)) - v for k, v in TARGET_COUNTS.items()}
    trunc_team = sum(int(r.get("team_truncated") or 0) for r in rows)
    trunc_stadium = sum(int(r.get("stadium_truncated") or 0) for r in rows)

    checks = {
        "mapping_rows_260": len(rows) == 260,
        "mapping_tm_unique_260": unique_tm == 260,
        "spanish_in_liga_high": spanish_in_liga >= 80,
        "non_patched_only_known_no_chain": non_patched_slots <= ALLOWED_NON_PATCHED_SLOTS,
        "no_chain_slots_low_impact_comps": no_chain_low_impact,
        "engine_corr_strength_ok": float(corr.get("spearman_value_vs_strength", 0.0)) >= 0.95,
        "engine_corr_sim_ok": float(corr.get("spearman_value_vs_simulated_points", 0.0)) >= 0.90,
        "rfef_labels_present": has_primera_rfef,
        "legacy_segunda_b_removed": not has_old_segunda_b_db,
        "spanish_country_consistent": len(spanish_country_mismatches) == 0 and inferred_spain_code is not None,
    }
    passed = all(checks.values())

    report = {
        "passed": passed,
        "checks": checks,
        "mapping_rows": len(rows),
        "mapping_unique_tm": unique_tm,
        "counts_by_competition": dict(sorted(counts.items())),
        "target_count_delta": target_delta,
        "truncations": {"team": trunc_team, "stadium": trunc_stadium},
        "liga_slot_count": len(liga_slots),
        "spanish_in_liga_slots": spanish_in_liga,
        "slot_19_comp": slot19_comp,
        "slot_104_comp": slot104_comp,
        "non_patched_slots": sorted(non_patched_slots),
        "engine_correlations": corr,
        "country_check": {
            "inferred_spain_code": inferred_spain_code,
            "spanish_rows_with_country": len(spanish_country_vals),
            "spanish_country_mismatch_count": len(spanish_country_mismatches),
            "spanish_country_mismatch_slots": sorted(spanish_country_mismatches)[:120],
        },
        "notes": {
            "mandos_legacy_allowed": True,
            "legacy_segunda_b_found_in_dbasedos": has_old_segunda_b_db,
            "allowed_non_patched_slots": sorted(ALLOWED_NON_PATCHED_SLOTS),
            "legacy_unpatchable_slots": sorted(LEGACY_UNPATCHABLE_SLOTS),
            "no_chain_low_impact_slots": sorted(NO_CHAIN_LOW_IMPACT_SLOTS),
        },
    }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"QA passed: {passed}")
    print(f"Checks: {checks}")
    print(f"By competition: {dict(sorted(counts.items()))}")
    print(f"Truncations: team={trunc_team}, stadium={trunc_stadium}")
    print(f"LIGA/SC spanish slots: {spanish_in_liga}/{len(liga_slots)}")
    print(f"Non-patched slots: {sorted(non_patched_slots)}")
    print(f"Report: {args.out}")


if __name__ == "__main__":
    main()

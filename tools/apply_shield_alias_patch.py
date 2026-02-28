#!/usr/bin/env python
"""Patch shield PKFs by aliasing target slots to legacy-equivalent crest chunks."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import difflib
import json
import re
import struct
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Tuple

from extract_pcf55_teams import extract


BASE = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT"
DEFAULT_MAPPING = Path(__file__).resolve().parent / "out" / "pcf55_full_mapping_global.csv"
DEFAULT_LEGACY_PKF = BASE / "EQUIPOS.patched.PKF"  # legacy baseline names
DEFAULT_REPORT = Path(__file__).resolve().parent / "out" / "shield_alias_patch_report.json"

SHIELD_FILES = [
    BASE / "MINIESC.PKF",
    BASE / "NANOESC.PKF",
    BASE / "RIDIESC.PKF",
]


@dataclass
class PtrEntry:
    index: int
    start: int
    size: int
    entry_off: int


def parse_pointer_entries(blob: bytes) -> List[PtrEntry]:
    out: List[PtrEntry] = []
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
            out.append(PtrEntry(index=idx, start=start, size=size, entry_off=i))
            i += 38
            idx += 1
            offset += 38
            continue
        if b == 0x05 and blob[i + 1 : i + 5] == b"\x00\x00\x00\x00":
            break
        i += 1
    return out


def norm(s: str) -> str:
    s = (s or "").strip().lower()
    s = unicodedata.normalize("NFKD", s)
    s = "".join(ch for ch in s if not unicodedata.combining(ch))
    s = s.replace(".", " ").replace("-", " ")
    s = re.sub(r"\b(cf|fc|cd|ud|sd|rcd|real|club|deportivo|futbol|football|s a d|sad)\b", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def name_score(a: str, b: str) -> float:
    na = norm(a)
    nb = norm(b)
    if not na or not nb:
        return 0.0
    if na == nb:
        return 1.0
    if na in nb or nb in na:
        return 0.94
    ta = set(na.split())
    tb = set(nb.split())
    if ta and tb:
        j = len(ta & tb) / len(ta | tb)
    else:
        j = 0.0
    seq = difflib.SequenceMatcher(None, na, nb).ratio()
    return max(seq, 0.7 * j + 0.3 * seq)


def load_mapping(path: Path) -> List[Dict[str, str]]:
    return list(csv.DictReader(path.open("r", encoding="utf-8", newline="")))


def load_legacy_names(pkf: Path) -> Dict[int, str]:
    rows = extract(pkf, max_scan=260)
    return {int(r.index): (r.team_name or "") for r in rows}


def build_aliases(
    mapping_rows: List[Dict[str, str]],
    legacy_names: Dict[int, str],
    *,
    min_score: float,
) -> Tuple[Dict[int, int], List[Dict[str, object]]]:
    # Candidate pool from legacy names that have meaningful labels.
    candidates = [(idx, name) for idx, name in legacy_names.items() if len((name or "").strip()) >= 3]

    aliases: Dict[int, int] = {}
    evidence: List[Dict[str, object]] = []
    used_sources: set[int] = set()

    # Prioritize teams with higher mapping confidence.
    rows = sorted(mapping_rows, key=lambda r: float(r.get("score") or 0.0), reverse=True)
    for row in rows:
        target = int(row["pcf_index"])
        wanted = (row.get("tm_team_patch") or row.get("tm_team") or "").strip()
        if not wanted:
            continue

        best_src = None
        best_score = -1.0
        for src_idx, src_name in candidates:
            if src_idx in used_sources:
                continue
            sc = name_score(wanted, src_name)
            if sc > best_score:
                best_score = sc
                best_src = (src_idx, src_name)

        if best_src is None or best_score < min_score:
            continue

        src_idx, src_name = best_src
        aliases[target] = src_idx
        used_sources.add(src_idx)
        evidence.append(
            {
                "target_slot": target,
                "tm_team": wanted,
                "source_slot": src_idx,
                "legacy_team": src_name,
                "score": round(best_score, 4),
            }
        )

    return aliases, evidence


def patch_one_pkf(path: Path, aliases: Dict[int, int]) -> Dict[str, object]:
    original = path.read_bytes()
    blob = bytearray(original)
    ptrs = parse_pointer_entries(original)
    by_idx = {p.index: p for p in ptrs}

    changed = 0
    details: List[Dict[str, int]] = []
    for target, source in sorted(aliases.items()):
        pt = by_idx.get(target)
        ps = by_idx.get(source)
        if pt is None or ps is None:
            continue

        # Alias target slot to source chunk (start+size), no data movement required.
        struct.pack_into("<I", blob, pt.entry_off + 26, ps.start)
        struct.pack_into("<I", blob, pt.entry_off + 30, ps.size)
        changed += 1
        if len(details) < 200:
            details.append({"target": target, "source": source, "start": ps.start, "size": ps.size})

    ts = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    backup = path.with_suffix(path.suffix + f".{ts}.bak")
    backup.write_bytes(original)
    path.write_bytes(bytes(blob))

    return {
        "file": str(path),
        "backup": str(backup),
        "entries_total": len(ptrs),
        "aliases_applied": changed,
        "details_head": details,
    }


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    p.add_argument("--legacy-pkf", type=Path, default=DEFAULT_LEGACY_PKF)
    p.add_argument("--min-score", type=float, default=0.8)
    p.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    return p.parse_args()


def main() -> None:
    args = parse_args()
    rows = load_mapping(args.mapping)
    legacy_names = load_legacy_names(args.legacy_pkf)
    aliases, evidence = build_aliases(rows, legacy_names, min_score=args.min_score)

    file_reports = [patch_one_pkf(p, aliases) for p in SHIELD_FILES]

    report = {
        "mapping": str(args.mapping),
        "legacy_pkf": str(args.legacy_pkf),
        "min_score": args.min_score,
        "aliases_count": len(aliases),
        "aliases_head": evidence[:200],
        "files": file_reports,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Aliases built: {len(aliases)}")
    for fr in file_reports:
        print(f"{fr['file']}: aliases_applied={fr['aliases_applied']}")
    print(f"Report: {args.report}")


if __name__ == "__main__":
    main()

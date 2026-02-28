#!/usr/bin/env python
"""Rebalance mapping for competition coherence and problematic player slots."""

from __future__ import annotations

import argparse
import csv
import struct
from pathlib import Path
from typing import Dict, List, Set, Tuple


DEFAULT_MAPPING = Path(__file__).resolve().parent / "out" / "pcf55_full_mapping_global.csv"
DEFAULT_OUT = Path(__file__).resolve().parent / "out" / "pcf55_full_mapping_global_rebalanced.csv"
DEFAULT_LIGA = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT" / "LIGA.DBC"
DEFAULT_SCESPANA = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT" / "SCESPANA.DBC"

SPANISH_COMPS = {"ES1", "ES2", "E3G1", "E3G2"}
TEAM_FIELDS = [
    "tm_team",
    "tm_team_patch",
    "tm_competition",
    "tm_stadium",
    "tm_stadium_patch",
    "tm_team_market_value_eur",
    "tm_squad_total_value_eur",
    "score",
    "accepted",
    "mapping_mode",
]


def byte_len_cp1252(text: str) -> int:
    return len((text or "").encode("cp1252", errors="replace"))


def trunc_penalty(row: Dict[str, str], slot_team_len: int, slot_stadium_len: int) -> int:
    t = max(0, byte_len_cp1252(row.get("tm_team_patch") or row.get("tm_team") or "") - max(1, slot_team_len))
    s = max(0, byte_len_cp1252(row.get("tm_stadium_patch") or row.get("tm_stadium") or "") - max(1, slot_stadium_len))
    return t + s


def ids_in_dbc(path: Path) -> Set[int]:
    raw = path.read_bytes()
    if len(raw) % 2 == 1:
        raw += b"\x00"
    values = struct.unpack("<" + ("H" * (len(raw) // 2)), raw)
    return {int(v) for v in values if 1 <= v <= 260}


def is_spanish(row: Dict[str, str]) -> bool:
    return str(row.get("tm_competition", "")).upper() in SPANISH_COMPS


def swap_payload(a: Dict[str, str], b: Dict[str, str]) -> None:
    for key in TEAM_FIELDS:
        a[key], b[key] = b.get(key, ""), a.get(key, "")
    a["mapping_mode"] = "rebalanced-swap"
    b["mapping_mode"] = "rebalanced-swap"


def recompute_trunc_flags(rows: List[Dict[str, str]]) -> None:
    for row in rows:
        team_len = int(row.get("slot_team_len") or 0)
        stadium_len = int(row.get("slot_stadium_len") or 0)
        row["team_truncated"] = str(
            int(byte_len_cp1252(row.get("tm_team_patch") or row.get("tm_team") or "") > max(1, team_len))
        )
        row["stadium_truncated"] = str(
            int(byte_len_cp1252(row.get("tm_stadium_patch") or row.get("tm_stadium") or "") > max(1, stadium_len))
        )


def pick_best_swap(
    target: Dict[str, str],
    donors: List[Dict[str, str]],
    donor_filter,
) -> Tuple[Dict[str, str] | None, int]:
    best = None
    best_cost = 10**9
    t_team_len = int(target.get("slot_team_len") or 0)
    t_std_len = int(target.get("slot_stadium_len") or 0)
    for donor in donors:
        if not donor_filter(donor):
            continue
        d_team_len = int(donor.get("slot_team_len") or 0)
        d_std_len = int(donor.get("slot_stadium_len") or 0)
        cost = trunc_penalty(donor, t_team_len, t_std_len) + trunc_penalty(target, d_team_len, d_std_len)
        if cost < best_cost:
            best = donor
            best_cost = cost
    return best, best_cost


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    parser.add_argument("--liga-dbc", type=Path, default=DEFAULT_LIGA)
    parser.add_argument("--scespana-dbc", type=Path, default=DEFAULT_SCESPANA)
    parser.add_argument("--no-chain-slots", nargs="*", type=int, default=[19, 104])
    parser.add_argument("--low-impact-comps", nargs="*", default=["A1", "DK1", "SC1"])
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    rows = list(csv.DictReader(args.mapping.open("r", encoding="utf-8", newline="")))
    by_idx: Dict[int, Dict[str, str]] = {int(r["pcf_index"]): r for r in rows}
    liga_slots = ids_in_dbc(args.liga_dbc) | ids_in_dbc(args.scespana_dbc)

    moved_spanish = 0
    used_donors: Set[int] = set()
    inside_non_spanish = [by_idx[i] for i in sorted(liga_slots) if i in by_idx and not is_spanish(by_idx[i])]
    outside_spanish = [r for r in rows if int(r["pcf_index"]) not in liga_slots and is_spanish(r)]

    for target in inside_non_spanish:
        donor, _ = pick_best_swap(
            target=target,
            donors=outside_spanish,
            donor_filter=lambda d: int(d["pcf_index"]) not in used_donors,
        )
        if donor is None:
            continue
        used_donors.add(int(donor["pcf_index"]))
        swap_payload(target, donor)
        moved_spanish += 1

    moved_low_impact = 0
    low_impact = {c.upper() for c in args.low_impact_comps}
    for slot in args.no_chain_slots:
        target = by_idx.get(slot)
        if target is None:
            continue
        if str(target.get("tm_competition", "")).upper() in low_impact:
            continue
        donors = [r for r in rows if int(r["pcf_index"]) not in set(args.no_chain_slots)]
        donor, _ = pick_best_swap(
            target=target,
            donors=donors,
            donor_filter=lambda d: (
                int(d["pcf_index"]) not in used_donors
                and str(d.get("tm_competition", "")).upper() in low_impact
            ),
        )
        if donor is None:
            continue
        used_donors.add(int(donor["pcf_index"]))
        swap_payload(target, donor)
        moved_low_impact += 1

    recompute_trunc_flags(rows)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    spanish_in_liga = sum(1 for i in liga_slots if i in by_idx and is_spanish(by_idx[i]))
    print(f"LIGA/SC slots: {len(liga_slots)}")
    print(f"Spanish in LIGA/SC after rebalance: {spanish_in_liga}")
    print(f"Spanish moved into LIGA/SC: {moved_spanish}")
    print(f"No-chain slots reassigned to low-impact comps: {moved_low_impact}")
    print(f"Output: {args.out}")


if __name__ == "__main__":
    main()

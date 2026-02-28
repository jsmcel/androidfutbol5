#!/usr/bin/env python
"""Force actual LIGA slot sets (JORNAD1M/JORNAD2M) to ES1/ES2 teams via safe swaps."""

from __future__ import annotations

import argparse
import csv
import struct
from collections import Counter
from pathlib import Path
from typing import Dict, List, Set, Tuple


DEFAULT_MAPPING = Path(__file__).resolve().parent / "out" / "pcf55_full_mapping_global.csv"
DEFAULT_JORNAD1M = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT" / "JORNAD1M.DBC"
DEFAULT_JORNAD2M = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT" / "JORNAD2M.DBC"
DEFAULT_OUT = Path(__file__).resolve().parent / "out" / "pcf55_full_mapping_global_forced_liga.csv"

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
    team = row.get("tm_team_patch") or row.get("tm_team") or ""
    stadium = row.get("tm_stadium_patch") or row.get("tm_stadium") or ""
    t = max(0, byte_len_cp1252(team) - max(1, slot_team_len))
    s = max(0, byte_len_cp1252(stadium) - max(1, slot_stadium_len))
    return t + s


def swap_payload(a: Dict[str, str], b: Dict[str, str]) -> None:
    for key in TEAM_FIELDS:
        a[key], b[key] = b.get(key, ""), a.get(key, "")
    a["mapping_mode"] = "force-liga-swap"
    b["mapping_mode"] = "force-liga-swap"


def recompute_trunc_flags(rows: List[Dict[str, str]]) -> None:
    for row in rows:
        team_len = int(row.get("slot_team_len") or 0)
        stadium_len = int(row.get("slot_stadium_len") or 0)
        team = row.get("tm_team_patch") or row.get("tm_team") or ""
        stadium = row.get("tm_stadium_patch") or row.get("tm_stadium") or ""
        row["team_truncated"] = str(int(byte_len_cp1252(team) > max(1, team_len)))
        row["stadium_truncated"] = str(int(byte_len_cp1252(stadium) > max(1, stadium_len)))


def top_ids_from_dbc(path: Path, n: int) -> List[int]:
    raw = path.read_bytes()
    vals = struct.unpack("<" + ("H" * (len(raw) // 2)), raw[: (len(raw) // 2) * 2])
    cnt = Counter(v for v in vals if 1 <= v <= 260)
    return [int(i) for i, _ in cnt.most_common(n)]


def pick_best_donor(
    *,
    target: Dict[str, str],
    rows: List[Dict[str, str]],
    desired_comp: str,
    forbidden_slots: Set[int],
    used_donors: Set[int],
) -> Dict[str, str] | None:
    t_team_len = int(target.get("slot_team_len") or 0)
    t_std_len = int(target.get("slot_stadium_len") or 0)
    best: Dict[str, str] | None = None
    best_cost = 10**9

    for row in rows:
        idx = int(row["pcf_index"])
        if idx in forbidden_slots or idx in used_donors:
            continue
        if (row.get("tm_competition") or "").upper() != desired_comp:
            continue

        d_team_len = int(row.get("slot_team_len") or 0)
        d_std_len = int(row.get("slot_stadium_len") or 0)
        cost = trunc_penalty(row, t_team_len, t_std_len) + trunc_penalty(target, d_team_len, d_std_len)
        if cost < best_cost:
            best = row
            best_cost = cost
    return best


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    p.add_argument("--jornad1m", type=Path, default=DEFAULT_JORNAD1M)
    p.add_argument("--jornad2m", type=Path, default=DEFAULT_JORNAD2M)
    p.add_argument("--n1", type=int, default=20, help="Top N slots from JORNAD1M (Primera).")
    p.add_argument("--n2", type=int, default=22, help="Top N slots from JORNAD2M (Segunda).")
    p.add_argument("--out", type=Path, default=DEFAULT_OUT)
    return p.parse_args()


def main() -> None:
    args = parse_args()
    rows = list(csv.DictReader(args.mapping.open("r", encoding="utf-8", newline="")))
    by_idx: Dict[int, Dict[str, str]] = {int(r["pcf_index"]): r for r in rows}

    liga1_slots = top_ids_from_dbc(args.jornad1m, n=args.n1)
    liga2_slots = top_ids_from_dbc(args.jornad2m, n=args.n2)
    forbidden_slots = set(liga1_slots) | set(liga2_slots)
    used_donors: Set[int] = set()

    swaps_1 = 0
    swaps_2 = 0

    for slot in liga1_slots:
        target = by_idx.get(slot)
        if not target:
            continue
        if (target.get("tm_competition") or "").upper() == "ES1":
            continue
        donor = pick_best_donor(
            target=target,
            rows=rows,
            desired_comp="ES1",
            forbidden_slots=forbidden_slots,
            used_donors=used_donors,
        )
        if donor is None:
            continue
        used_donors.add(int(donor["pcf_index"]))
        swap_payload(target, donor)
        swaps_1 += 1

    for slot in liga2_slots:
        target = by_idx.get(slot)
        if not target:
            continue
        if (target.get("tm_competition") or "").upper() == "ES2":
            continue
        donor = pick_best_donor(
            target=target,
            rows=rows,
            desired_comp="ES2",
            forbidden_slots=forbidden_slots,
            used_donors=used_donors,
        )
        if donor is None:
            continue
        used_donors.add(int(donor["pcf_index"]))
        swap_payload(target, donor)
        swaps_2 += 1

    recompute_trunc_flags(rows)

    # Validation snapshot.
    es1_in_liga1 = sum(
        1 for slot in liga1_slots if slot in by_idx and (by_idx[slot].get("tm_competition") or "").upper() == "ES1"
    )
    es2_in_liga2 = sum(
        1 for slot in liga2_slots if slot in by_idx and (by_idx[slot].get("tm_competition") or "").upper() == "ES2"
    )

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    print(f"Liga1 slots (top {args.n1} JORNAD1M): {liga1_slots}")
    print(f"Liga2 slots (top {args.n2} JORNAD2M): {liga2_slots}")
    print(f"Swaps applied: ES1={swaps_1}, ES2={swaps_2}")
    print(f"After force: ES1 in Liga1 slots={es1_in_liga1}/{len(liga1_slots)}")
    print(f"After force: ES2 in Liga2 slots={es2_in_liga2}/{len(liga2_slots)}")
    print(f"Output: {args.out}")


if __name__ == "__main__":
    main()

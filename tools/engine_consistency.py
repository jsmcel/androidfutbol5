#!/usr/bin/env python
"""Generate a coherence report for PCF55 update vs. new external data."""

from __future__ import annotations

import argparse
import csv
import json
import math
import random
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Dict, List, Tuple


DEFAULT_MAPPING = Path(__file__).resolve().parent / "out" / "pcf55_transfermarkt_mapping.csv"
DEFAULT_TM_JSON = Path(__file__).resolve().parent / "out" / "transfermarkt_teams.json"
DEFAULT_OUT_JSON = Path(__file__).resolve().parent / "out" / "engine_consistency_report.json"
DEFAULT_OUT_CSV = Path(__file__).resolve().parent / "out" / "engine_strength_reference.csv"


@dataclass
class TeamStrength:
    pcf_index: int
    pcf_team: str
    tm_team: str
    competition: str
    score: float
    squad_value_eur: float
    team_value_eur: float
    strength_0_100: float


def load_tm(path: Path) -> Dict[str, Dict[str, object]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    out: Dict[str, Dict[str, object]] = {}
    for team in payload.get("teams", []):
        name = str(team.get("team_name", "")).strip().lower()
        if name:
            out[name] = team
    return out


def load_mapping(path: Path, min_score: float) -> List[Dict[str, str]]:
    rows: List[Dict[str, str]] = []
    with path.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            score = float(row.get("score", "0") or 0)
            accepted = row.get("accepted", "0") in ("1", "true", "True")
            if not accepted:
                continue
            if score < min_score:
                continue
            rows.append(row)
    return rows


def dedupe_best_by_tm(rows: List[Dict[str, str]]) -> Tuple[List[Dict[str, str]], List[Dict[str, str]]]:
    best: Dict[str, Dict[str, str]] = {}
    dupes: List[Dict[str, str]] = []
    for row in rows:
        tm = (row.get("tm_team") or "").strip().lower()
        if not tm:
            continue
        if tm not in best:
            best[tm] = row
            continue
        if float(row["score"]) > float(best[tm]["score"]):
            dupes.append(best[tm])
            best[tm] = row
        else:
            dupes.append(row)
    return list(best.values()), dupes


def build_strength_rows(mapping_rows: List[Dict[str, str]], tm_by_name: Dict[str, Dict[str, object]]) -> List[TeamStrength]:
    values = []
    enriched = []
    for row in mapping_rows:
        tm_name = (row["tm_team"] or "").strip().lower()
        tm = tm_by_name.get(tm_name)
        if not tm:
            continue
        squad = float(tm.get("squad_total_value_eur") or 0.0)
        team = float(tm.get("team_market_value_eur") or 0.0)
        val = squad if squad > 0 else team
        if val <= 0:
            continue
        values.append(val)
        enriched.append((row, tm, val))

    if not values:
        return []

    lo = min(values)
    hi = max(values)
    span = max(hi - lo, 1.0)

    out: List[TeamStrength] = []
    for row, tm, val in enriched:
        # Log scale avoids giant gaps between top and bottom clubs.
        lv = math.log10(max(val, 1.0))
        llo = math.log10(max(lo, 1.0))
        lhi = math.log10(max(hi, 1.0))
        lspan = max(lhi - llo, 1e-9)
        strength = 40.0 + 60.0 * ((lv - llo) / lspan)
        out.append(
            TeamStrength(
                pcf_index=int(row["pcf_index"]),
                pcf_team=row["pcf_team"],
                tm_team=row["tm_team"],
                competition=row["tm_competition"],
                score=float(row["score"]),
                squad_value_eur=float(tm.get("squad_total_value_eur") or 0.0),
                team_value_eur=float(tm.get("team_market_value_eur") or 0.0),
                strength_0_100=round(strength, 2),
            )
        )
    return sorted(out, key=lambda r: r.strength_0_100, reverse=True)


def rank(values: List[float]) -> List[float]:
    indexed = sorted(enumerate(values), key=lambda x: x[1])
    ranks = [0.0] * len(values)
    i = 0
    while i < len(indexed):
        j = i
        while j + 1 < len(indexed) and indexed[j + 1][1] == indexed[i][1]:
            j += 1
        avg = (i + j + 2) / 2.0
        for k in range(i, j + 1):
            ranks[indexed[k][0]] = avg
        i = j + 1
    return ranks


def spearman(x: List[float], y: List[float]) -> float:
    if len(x) != len(y) or len(x) < 2:
        return 0.0
    rx = rank(x)
    ry = rank(y)
    mx = sum(rx) / len(rx)
    my = sum(ry) / len(ry)
    num = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    denx = math.sqrt(sum((a - mx) ** 2 for a in rx))
    deny = math.sqrt(sum((b - my) ** 2 for b in ry))
    if denx == 0 or deny == 0:
        return 0.0
    return num / (denx * deny)


def simulate_points(teams: List[TeamStrength], seed: int = 42) -> Dict[int, float]:
    rnd = random.Random(seed)
    points = {t.pcf_index: 0.0 for t in teams}
    strengths = {t.pcf_index: t.strength_0_100 for t in teams}
    ids = [t.pcf_index for t in teams]

    # Single round-robin proxy. Engine-logic proxy:
    # outcome uses strength delta + home advantage + randomness.
    home_adv = 3.5
    for i, home in enumerate(ids):
        for away in ids[i + 1 :]:
            sh = strengths[home] + home_adv
            sa = strengths[away]
            p_home = 1.0 / (1.0 + math.exp(-(sh - sa) / 8.0))
            p_draw = max(0.12, 0.30 - abs(sh - sa) / 120.0)
            p_away = max(0.0, 1.0 - p_home - p_draw)
            total = p_home + p_draw + p_away
            p_home, p_draw, p_away = p_home / total, p_draw / total, p_away / total
            r = rnd.random()
            if r < p_home:
                points[home] += 3.0
            elif r < p_home + p_draw:
                points[home] += 1.0
                points[away] += 1.0
            else:
                points[away] += 3.0
    return points


def write_csv(rows: List[TeamStrength], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(
            [
                "pcf_index",
                "pcf_team",
                "tm_team",
                "competition",
                "mapping_score",
                "squad_value_eur",
                "team_value_eur",
                "strength_0_100",
            ]
        )
        for r in rows:
            w.writerow(
                [
                    r.pcf_index,
                    r.pcf_team,
                    r.tm_team,
                    r.competition,
                    f"{r.score:.4f}",
                    f"{r.squad_value_eur:.0f}",
                    f"{r.team_value_eur:.0f}",
                    f"{r.strength_0_100:.2f}",
                ]
            )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    parser.add_argument("--transfermarkt", type=Path, default=DEFAULT_TM_JSON)
    parser.add_argument("--min-score", type=float, default=0.72)
    parser.add_argument("--out-json", type=Path, default=DEFAULT_OUT_JSON)
    parser.add_argument("--out-csv", type=Path, default=DEFAULT_OUT_CSV)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    tm_by_name = load_tm(args.transfermarkt)
    mapped = load_mapping(args.mapping, args.min_score)
    unique_rows, dupes = dedupe_best_by_tm(mapped)
    strengths = build_strength_rows(unique_rows, tm_by_name)

    value_vec = [s.squad_value_eur if s.squad_value_eur > 0 else s.team_value_eur for s in strengths]
    strength_vec = [s.strength_0_100 for s in strengths]
    corr_value_strength = spearman(value_vec, strength_vec) if strengths else 0.0

    points = simulate_points(strengths) if strengths else {}
    sim_points = [points[s.pcf_index] for s in strengths] if strengths else []
    corr_value_points = spearman(value_vec, sim_points) if strengths else 0.0

    report = {
        "engine_logic_reference": {
            "sources": [
                "INS09.HTM (SIMULADOR/RESULTADO): outcome uses tactics + team/rival parameters",
                "INS09.HTM (VISIONADO): performance affected by parameters + morale + fitness",
                "INS09.HTM (TACTICAS/ROLES): wrong role-position lowers morale and performance",
            ],
            "coherence_rule": "new attributes must preserve realistic team strength ordering and avoid extreme gaps",
        },
        "input": {
            "mapping_rows": len(mapped),
            "unique_teams_after_dedupe": len(unique_rows),
            "duplicate_mappings_dropped": len(dupes),
        },
        "correlations": {
            "spearman_value_vs_strength": round(corr_value_strength, 4),
            "spearman_value_vs_simulated_points": round(corr_value_points, 4),
        },
        "quality_flags": {
            "ok_value_strength": corr_value_strength >= 0.9,
            "ok_value_simulation": corr_value_points >= 0.6,
        },
        "dropped_duplicates": [
            {
                "pcf_index": int(r["pcf_index"]),
                "pcf_team": r["pcf_team"],
                "tm_team": r["tm_team"],
                "score": float(r["score"]),
            }
            for r in dupes
        ],
        "strength_rows": [asdict(s) for s in strengths],
    }

    args.out_json.parent.mkdir(parents=True, exist_ok=True)
    args.out_json.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    write_csv(strengths, args.out_csv)

    print(f"Mapped rows: {len(mapped)}")
    print(f"Unique teams: {len(unique_rows)}")
    print(f"Dropped duplicate mappings: {len(dupes)}")
    print(f"Spearman value vs strength: {corr_value_strength:.4f}")
    print(f"Spearman value vs simulated points: {corr_value_points:.4f}")
    print(f"JSON: {args.out_json}")
    print(f"CSV : {args.out_csv}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python
"""Derive PCF-style player attributes from Transfermarkt squads/values."""

from __future__ import annotations

import argparse
import csv
import json
import math
from collections import defaultdict
from pathlib import Path
from statistics import mean
from typing import Dict, List, Tuple


DEFAULT_TM_JSON = Path(__file__).resolve().parent / "out" / "transfermarkt_teams.json"
DEFAULT_MAPPING = Path(__file__).resolve().parent / "out" / "pcf55_transfermarkt_mapping.csv"
DEFAULT_OUT_PLAYERS = Path(__file__).resolve().parent / "out" / "pcf55_derived_attributes.csv"
DEFAULT_OUT_TEAMS = Path(__file__).resolve().parent / "out" / "pcf55_derived_team_strength.csv"


def clamp(v: float, lo: int = 1, hi: int = 99) -> int:
    return int(max(lo, min(hi, round(v))))


def role_group(position: str) -> str:
    p = (position or "").lower()
    if "keeper" in p or "goal" in p:
        return "GK"
    if any(k in p for k in ("back", "defender", "sweeper")):
        return "DEF"
    if any(k in p for k in ("midfield", "winger")):
        return "MID"
    if any(k in p for k in ("forward", "striker", "centre-forward", "center-forward")):
        return "ATT"
    return "MID"


BASE = {
    "GK": {"VE": 46, "RE": 62, "AG": 65, "CA": 62, "PORTERO": 86, "ENTRADA": 44, "REGATE": 34, "REMATE": 28, "PASE": 56, "TIRO": 34},
    "DEF": {"VE": 63, "RE": 73, "AG": 75, "CA": 63, "PORTERO": 20, "ENTRADA": 79, "REGATE": 51, "REMATE": 46, "PASE": 59, "TIRO": 49},
    "MID": {"VE": 69, "RE": 71, "AG": 61, "CA": 73, "PORTERO": 15, "ENTRADA": 61, "REGATE": 75, "REMATE": 63, "PASE": 77, "TIRO": 66},
    "ATT": {"VE": 75, "RE": 67, "AG": 56, "CA": 75, "PORTERO": 10, "ENTRADA": 41, "REGATE": 77, "REMATE": 81, "PASE": 65, "TIRO": 79},
}


def load_tm(path: Path) -> Dict[str, Dict[str, object]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    out: Dict[str, Dict[str, object]] = {}
    for team in data.get("teams", []):
        name = str(team.get("team_name", "")).strip().lower()
        if name:
            out[name] = team
    return out


def load_unique_mapping(path: Path, min_score: float) -> List[Dict[str, str]]:
    rows: List[Dict[str, str]] = []
    with path.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row.get("accepted", "0") not in ("1", "true", "True"):
                continue
            if float(row.get("score", "0") or 0) < min_score:
                continue
            rows.append(row)

    # Keep best row per TM team.
    best: Dict[str, Dict[str, str]] = {}
    for row in rows:
        key = (row.get("tm_team") or "").strip().lower()
        if not key:
            continue
        if key not in best or float(row["score"]) > float(best[key]["score"]):
            best[key] = row
    return list(best.values())


def normalize_values(players: List[Dict[str, object]]) -> Tuple[float, float]:
    vals = [float(p.get("market_value_eur") or 0.0) for p in players if float(p.get("market_value_eur") or 0.0) > 0]
    if not vals:
        return 1.0, 10.0
    lo = math.log10(min(vals))
    hi = math.log10(max(vals))
    if hi <= lo:
        hi = lo + 1.0
    return lo, hi


def value_norm(v: float, lo: float, hi: float) -> float:
    if v <= 0:
        return 0.35
    lv = math.log10(v)
    return max(0.0, min(1.0, (lv - lo) / (hi - lo)))


def derive_attrs(position: str, market_value: float, age: int | None, lo: float, hi: float) -> Dict[str, int]:
    grp = role_group(position)
    b = BASE[grp]
    n = value_norm(market_value, lo, hi)
    boost = -12.0 + 28.0 * n

    age_adj = 0.0
    if age is not None:
        # Mild peak around 27 years old.
        age_adj = -0.45 * abs(age - 27)
        age_adj = max(-8.0, min(2.0, age_adj))

    ve = clamp(b["VE"] + 0.34 * boost + 0.15 * age_adj)
    re = clamp(b["RE"] + 0.28 * boost + 0.20 * age_adj)
    ag = clamp(b["AG"] + 0.22 * boost + 0.10 * age_adj)
    ca = clamp(b["CA"] + 0.40 * boost + 0.20 * age_adj)
    portero = clamp(b["PORTERO"] + (0.65 if grp == "GK" else 0.10) * boost + 0.10 * age_adj)
    entrada = clamp(b["ENTRADA"] + 0.30 * boost + 0.05 * age_adj)
    regate = clamp(b["REGATE"] + 0.36 * boost + 0.10 * age_adj)
    remate = clamp(b["REMATE"] + 0.40 * boost + 0.08 * age_adj)
    pase = clamp(b["PASE"] + 0.34 * boost + 0.10 * age_adj)
    tiro = clamp(b["TIRO"] + 0.40 * boost + 0.08 * age_adj)
    me = clamp((ve + re + ag + ca) / 4.0)

    return {
        "VE": ve,
        "RE": re,
        "AG": ag,
        "CA": ca,
        "ME": me,
        "PORTERO": portero,
        "ENTRADA": entrada,
        "REGATE": regate,
        "REMATE": remate,
        "PASE": pase,
        "TIRO": tiro,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--transfermarkt", type=Path, default=DEFAULT_TM_JSON)
    parser.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    parser.add_argument("--min-score", type=float, default=0.72)
    parser.add_argument("--out-players", type=Path, default=DEFAULT_OUT_PLAYERS)
    parser.add_argument("--out-teams", type=Path, default=DEFAULT_OUT_TEAMS)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    tm = load_tm(args.transfermarkt)
    mapping = load_unique_mapping(args.mapping, args.min_score)

    all_players_for_norm: List[Dict[str, object]] = []
    selected_teams: List[Tuple[Dict[str, str], Dict[str, object]]] = []
    for row in mapping:
        key = row["tm_team"].strip().lower()
        team = tm.get(key)
        if not team:
            continue
        selected_teams.append((row, team))
        all_players_for_norm.extend(team.get("squad", []))

    lo, hi = normalize_values(all_players_for_norm)

    args.out_players.parent.mkdir(parents=True, exist_ok=True)
    args.out_teams.parent.mkdir(parents=True, exist_ok=True)

    team_me: Dict[str, List[int]] = defaultdict(list)
    team_value: Dict[str, float] = {}
    rows_out = 0
    with args.out_players.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(
            [
                "pcf_index",
                "pcf_team_slot",
                "tm_team",
                "competition",
                "player_name",
                "position",
                "age",
                "market_value_eur",
                "VE",
                "RE",
                "AG",
                "CA",
                "ME",
                "PORTERO",
                "ENTRADA",
                "REGATE",
                "REMATE",
                "PASE",
                "TIRO",
                "source",
            ]
        )

        for row, team in selected_teams:
            tm_team = str(team.get("team_name", ""))
            comp = str(team.get("competition", ""))
            pcf_index = int(row["pcf_index"])
            pcf_team = row["pcf_team"]
            team_value[tm_team] = float(team.get("squad_total_value_eur") or team.get("team_market_value_eur") or 0.0)
            for player in team.get("squad", []):
                attrs = derive_attrs(
                    position=str(player.get("position") or ""),
                    market_value=float(player.get("market_value_eur") or 0.0),
                    age=player.get("age") if isinstance(player.get("age"), int) else None,
                    lo=lo,
                    hi=hi,
                )
                team_me[tm_team].append(attrs["ME"])
                w.writerow(
                    [
                        pcf_index,
                        pcf_team,
                        tm_team,
                        comp,
                        player.get("name", ""),
                        player.get("position", ""),
                        player.get("age", ""),
                        int(float(player.get("market_value_eur") or 0.0)),
                        attrs["VE"],
                        attrs["RE"],
                        attrs["AG"],
                        attrs["CA"],
                        attrs["ME"],
                        attrs["PORTERO"],
                        attrs["ENTRADA"],
                        attrs["REGATE"],
                        attrs["REMATE"],
                        attrs["PASE"],
                        attrs["TIRO"],
                        "transfermarkt-derived",
                    ]
                )
                rows_out += 1

    with args.out_teams.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["tm_team", "team_value_eur", "avg_me", "min_me", "max_me", "players"])
        for tm_team, mes in sorted(team_me.items(), key=lambda kv: mean(kv[1]), reverse=True):
            w.writerow(
                [
                    tm_team,
                    int(team_value.get(tm_team, 0.0)),
                    f"{mean(mes):.2f}",
                    min(mes),
                    max(mes),
                    len(mes),
                ]
            )

    print(f"Teams used: {len(selected_teams)}")
    print(f"Players written: {rows_out}")
    print(f"Players CSV: {args.out_players}")
    print(f"Teams CSV  : {args.out_teams}")


if __name__ == "__main__":
    main()

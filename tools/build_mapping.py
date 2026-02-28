#!/usr/bin/env python
"""Build automatic mapping between extracted PCF55 teams and Transfermarkt teams."""

from __future__ import annotations

import argparse
import csv
import json
import re
import unicodedata
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path
from typing import Dict, List, Tuple


DEFAULT_PCF_EXTRACTED = Path(__file__).resolve().parent / "out" / "pcf55_teams_extracted.json"
DEFAULT_TM_JSON = Path(__file__).resolve().parent / "out" / "transfermarkt_teams.json"
DEFAULT_OUT_CSV = Path(__file__).resolve().parent / "out" / "pcf55_transfermarkt_mapping.csv"
DEFAULT_OUT_JSON = Path(__file__).resolve().parent / "out" / "pcf55_transfermarkt_mapping.json"


STOPWORDS = {
    "club",
    "cf",
    "fc",
    "real",
    "deportivo",
    "futbol",
    "football",
    "sporting",
    "athletic",
    "s",
    "a",
    "d",
    "ud",
    "cd",
    "sd",
}


@dataclass
class MatchRow:
    pcf_index: int
    pcf_team: str
    pcf_stadium: str
    pcf_confidence: float
    tm_team: str
    tm_competition: str
    tm_stadium: str
    tm_team_market_value_eur: float
    tm_squad_total_value_eur: float
    score: float
    accepted: bool


def normalize(s: str) -> str:
    s = (s or "").strip().lower()
    s = "".join(
        c
        for c in unicodedata.normalize("NFKD", s)
        if not unicodedata.combining(c)
    )
    s = re.sub(r"[^a-z0-9 ]+", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def token_set(s: str) -> set[str]:
    tokens = set(normalize(s).split())
    return {t for t in tokens if t and t not in STOPWORDS}


def similarity(a: str, b: str) -> float:
    na = normalize(a)
    nb = normalize(b)
    if not na or not nb:
        return 0.0

    seq = SequenceMatcher(None, na, nb).ratio()

    ta = token_set(a)
    tb = token_set(b)
    if ta and tb:
        jacc = len(ta & tb) / len(ta | tb)
    else:
        jacc = 0.0

    starts = 1.0 if na.startswith(nb) or nb.startswith(na) else 0.0

    has_b_a = " b " in f" {na} " or na.endswith(" b")
    has_b_b = " b " in f" {nb} " or nb.endswith(" b")
    b_penalty = 0.15 if (has_b_a ^ has_b_b) else 0.0

    return (0.65 * seq + 0.30 * jacc + 0.05 * starts) - b_penalty


def best_match(pcf_name: str, tm_teams: List[Dict[str, object]]) -> Tuple[Dict[str, object], float]:
    best_team = tm_teams[0]
    best_score = -1.0
    for tm in tm_teams:
        score = similarity(pcf_name, str(tm.get("team_name", "")))
        if score > best_score:
            best_team = tm
            best_score = score
    return best_team, round(best_score, 4)


def load_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def build_rows(
    pcf_data: List[Dict[str, object]],
    tm_data: Dict[str, object],
    min_score: float,
    min_pcf_confidence: float,
) -> List[MatchRow]:
    tm_teams = list(tm_data.get("teams", []))
    if not tm_teams:
        return []

    rows: List[MatchRow] = []
    for pcf in pcf_data:
        pcf_team = str(pcf.get("team_name", "")).strip()
        if not pcf_team:
            continue
        pcf_conf = float(pcf.get("confidence", 0.0) or 0.0)
        if pcf_conf < min_pcf_confidence:
            continue

        tm, score = best_match(pcf_team, tm_teams)
        accepted = score >= min_score
        rows.append(
            MatchRow(
                pcf_index=int(pcf["index"]),
                pcf_team=pcf_team,
                pcf_stadium=str(pcf.get("stadium_name", "") or ""),
                pcf_confidence=pcf_conf,
                tm_team=str(tm.get("team_name", "")),
                tm_competition=str(tm.get("competition", "")),
                tm_stadium=str(tm.get("stadium_name", "")),
                tm_team_market_value_eur=float(tm.get("team_market_value_eur") or 0.0),
                tm_squad_total_value_eur=float(tm.get("squad_total_value_eur") or 0.0),
                score=score,
                accepted=accepted,
            )
        )
    return rows


def write_csv(rows: List[MatchRow], out_csv: Path) -> None:
    out_csv.parent.mkdir(parents=True, exist_ok=True)
    with out_csv.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(
            [
                "pcf_index",
                "pcf_team",
                "pcf_stadium",
                "pcf_confidence",
                "tm_team",
                "tm_competition",
                "tm_stadium",
                "tm_team_market_value_eur",
                "tm_squad_total_value_eur",
                "score",
                "accepted",
            ]
        )
        for r in rows:
            writer.writerow(
                [
                    r.pcf_index,
                    r.pcf_team,
                    r.pcf_stadium,
                    f"{r.pcf_confidence:.2f}",
                    r.tm_team,
                    r.tm_competition,
                    r.tm_stadium,
                    f"{r.tm_team_market_value_eur:.0f}",
                    f"{r.tm_squad_total_value_eur:.0f}",
                    f"{r.score:.4f}",
                    int(r.accepted),
                ]
            )


def write_json(rows: List[MatchRow], out_json: Path) -> None:
    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(
        json.dumps([r.__dict__ for r in rows], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pcf", type=Path, default=DEFAULT_PCF_EXTRACTED)
    parser.add_argument("--tm", type=Path, default=DEFAULT_TM_JSON)
    parser.add_argument("--out-csv", type=Path, default=DEFAULT_OUT_CSV)
    parser.add_argument("--out-json", type=Path, default=DEFAULT_OUT_JSON)
    parser.add_argument(
        "--min-score",
        type=float,
        default=0.72,
        help="Automatic acceptance threshold for matching",
    )
    parser.add_argument(
        "--min-pcf-confidence",
        type=float,
        default=0.60,
        help="Minimum extraction confidence to include a PCF team row",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    pcf_data = load_json(args.pcf)
    tm_data = load_json(args.tm)
    rows = build_rows(
        pcf_data=pcf_data,
        tm_data=tm_data,
        min_score=args.min_score,
        min_pcf_confidence=args.min_pcf_confidence,
    )
    write_csv(rows, args.out_csv)
    write_json(rows, args.out_json)
    accepted = sum(1 for r in rows if r.accepted)
    print(f"Rows written: {len(rows)}")
    print(f"Accepted   : {accepted}")
    print(f"CSV: {args.out_csv}")
    print(f"JSON: {args.out_json}")


if __name__ == "__main__":
    main()

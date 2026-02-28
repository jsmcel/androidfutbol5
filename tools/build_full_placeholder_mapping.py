#!/usr/bin/env python
"""Build a full mapping using obsolete PCF slots as placeholders for new teams."""

from __future__ import annotations

import argparse
import csv
import json
import unicodedata
from collections import defaultdict
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path
from typing import Dict, List, Tuple


DEFAULT_EXTRACTED = Path(__file__).resolve().parent / "out" / "pcf55_teams_extracted.csv"
DEFAULT_BASE_MAPPING = Path(__file__).resolve().parent / "out" / "pcf55_transfermarkt_mapping.csv"
DEFAULT_TM_JSON = Path(__file__).resolve().parent / "out" / "transfermarkt_teams.json"
DEFAULT_OUT = Path(__file__).resolve().parent / "out" / "pcf55_full_mapping.csv"
DEFAULT_PRIORITY_COMPETITIONS = [
    "ES1",
    "ES2",
    "E3G1",
    "E3G2",
    "GB1",
    "IT1",
    "FR1",
    "L1",
    "PO1",
    "NL1",
    "BE1",
    "TR1",
    "RU1",
    "SC1",
    "A1",
    "DK1",
    "PL1",
]
DEFAULT_TARGET_COUNTS = [
    "ES1=20",
    "ES2=22",
    "E3G1=20",
    "E3G2=20",
    "GB1=20",
    "IT1=20",
    "FR1=18",
    "L1=18",
    "PO1=18",
    "NL1=18",
    "BE1=16",
    "TR1=18",
    "RU1=10",
    "PL1=10",
    "SC1=4",
    "A1=4",
    "DK1=4",
]

TEAM_PATCH_NAMES = {
    "fc barcelona": "Barcelona",
    "atletico de madrid": "At. Madrid",
    "athletic bilbao": "Athletic",
    "real sociedad": "R. Sociedad",
    "real betis balompie": "R. Betis",
    "valencia cf": "Valencia",
    "girona fc": "Girona",
    "celta de vigo": "Celta",
    "sevilla fc": "Sevilla",
    "rcd espanyol barcelona": "Espanyol",
    "rayo vallecano": "Rayo Vallecano",
    "ca osasuna": "Osasuna",
    "elche cf": "Elche",
    "levante ud": "Levante",
    "rcd mallorca": "Mallorca",
    "getafe cf": "Getafe",
    "real oviedo": "R. Oviedo",
    "deportivo alaves": "Alaves",
    "deportivo de la coruna": "Dep. Coruna",
    "ud almeria": "Almeria",
    "racing santander": "Racing",
    "real valladolid cf": "Valladolid",
    "sporting gijon": "Sporting",
    "real zaragoza": "Zaragoza",
    "cadiz cf": "Cadiz",
    "cd leganes": "Leganes",
    "granada cf": "Granada",
    "burgos cf": "Burgos",
    "cd mirandes": "Mirandes",
    "malaga cf": "Malaga",
    "fc andorra": "Andorra",
    "cd castellon": "Castellon",
    "sd eibar": "Eibar",
    "cordoba cf": "Cordoba",
    "sd huesca": "Huesca",
    "cultural leonesa": "Leonesa",
    "ad ceuta fc": "Ceuta",
    "albacete balompie": "Albacete",
    "real sociedad b": "R. Sociedad B",
}

# Force known strategic clubs to strong legacy slots with full squads.
# Keys are normalized with `normalize()`.
FORCED_SLOT_BY_TM = {
    "atletico de madrid": 92,  # old At. Madrid slot
}


@dataclass
class Slot:
    index: int
    team_name: str
    stadium_name: str
    confidence: float
    team_len: int
    stadium_len: int


def normalize(s: str) -> str:
    s = (s or "").strip().lower()
    s = "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))
    return " ".join(s.split())


def normalize_ascii(s: str) -> str:
    raw = (s or "").strip()
    raw = "".join(c for c in unicodedata.normalize("NFKD", raw) if not unicodedata.combining(c))
    return " ".join(raw.split())


def load_extracted(path: Path) -> Dict[int, Slot]:
    out: Dict[int, Slot] = {}
    with path.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if not row.get("team_pos"):
                continue
            idx = int(row["index"])
            team_len = int(row.get("team_len") or 0)
            stadium_len = int(row.get("stadium_len") or 0)
            if team_len <= 0:
                continue
            out[idx] = Slot(
                index=idx,
                team_name=row.get("team_name", "") or "",
                stadium_name=row.get("stadium_name", "") or "",
                confidence=float(row.get("confidence") or 0.0),
                team_len=team_len,
                stadium_len=stadium_len,
            )
    return out


def load_base_mapping(path: Path, min_score: float) -> List[Dict[str, str]]:
    rows: List[Dict[str, str]] = []
    with path.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row.get("accepted", "0") not in ("1", "true", "True"):
                continue
            if float(row.get("score") or 0) < min_score:
                continue
            rows.append(row)

    # Deduplicate by tm team.
    best: Dict[str, Dict[str, str]] = {}
    for row in rows:
        key = normalize(row.get("tm_team", ""))
        if not key:
            continue
        if key not in best or float(row["score"]) > float(best[key]["score"]):
            best[key] = row
    return list(best.values())


def load_tm(path: Path) -> List[Dict[str, object]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return list(data.get("teams", []))


def byte_len_cp1252(text: str) -> int:
    return len((text or "").encode("cp1252", errors="replace"))


def name_similarity(a: str, b: str) -> float:
    return SequenceMatcher(None, normalize(a), normalize(b)).ratio()


def patch_team_name(raw: str) -> str:
    clean = normalize_ascii(raw)
    key = normalize(clean)
    out = TEAM_PATCH_NAMES.get(key, clean)
    out = (
        out.replace("Football Club", "FC")
        .replace("Futbol Club", "FC")
        .replace("Sporting Club", "SC")
    )
    return " ".join(out.split())


def patch_stadium_name(raw: str) -> str:
    s = normalize_ascii(raw)
    replacements = [
        ("Stadium", "Std."),
        ("Stadion", "Std."),
        ("Stadio", "Std."),
        ("Estadio", "Est."),
        ("Estadio", "Est."),
        ("Municipal", "Mpal."),
        ("Complexo Desportivo", "C.D."),
        ("Complexe Sportif", "C.S."),
        ("Sport Kompleksi", "S.K."),
        ("Spor Kompleksi", "S.K."),
        ("Arena", "Ar."),
        ("Parque", "Pk."),
        ("Park", "Pk."),
        ("de", ""),
        ("del", ""),
        ("da", ""),
        ("do", ""),
        ("Bahrain Victorious", ""),
        ("Spotify", ""),
        ("Ibercaja", ""),
    ]
    for a, b in replacements:
        s = s.replace(a, b)
    return " ".join(s.split())


def parse_target_counts(items: List[str]) -> Dict[str, int]:
    out: Dict[str, int] = {}
    for item in items:
        txt = (item or "").strip()
        if not txt or "=" not in txt:
            continue
        k, v = txt.split("=", 1)
        k = k.strip().upper()
        try:
            n = int(v.strip())
        except ValueError:
            continue
        if n > 0:
            out[k] = n
    return out


def apply_target_counts(tm_rows: List[Dict[str, object]], target_counts: Dict[str, int], only_targeted: bool) -> List[Dict[str, object]]:
    if not target_counts:
        return list(tm_rows)

    by_comp: Dict[str, List[Dict[str, object]]] = defaultdict(list)
    for row in tm_rows:
        comp = str(row.get("competition", "")).upper()
        by_comp[comp].append(row)

    selected: List[Dict[str, object]] = []
    for comp, rows in by_comp.items():
        rows_sorted = sorted(
            rows,
            key=lambda t: float(t.get("squad_total_value_eur") or t.get("team_market_value_eur") or 0.0),
            reverse=True,
        )
        if comp in target_counts:
            selected.extend(rows_sorted[: target_counts[comp]])
        elif not only_targeted:
            selected.extend(rows_sorted)
    return selected


def choose_slot(team_name: str, stadium_name: str, slots: List[Slot]) -> Tuple[int, float, float]:
    need_team = byte_len_cp1252(team_name)
    need_stadium = byte_len_cp1252(stadium_name)

    best_i = 0
    best_cost = 10**9
    best_sim = 0.0
    for i, slot in enumerate(slots):
        trunc_team = max(0, need_team - slot.team_len)
        trunc_stadium = max(0, need_stadium - max(1, slot.stadium_len))

        sim = name_similarity(team_name, slot.team_name)
        cost = (trunc_team * 20.0) + (trunc_stadium * 8.0) - (sim * 4.0) - (slot.confidence * 1.0)

        if cost < best_cost:
            best_cost = cost
            best_i = i
            best_sim = sim
    return best_i, best_cost, best_sim


def unmatched_sort_key(team: Dict[str, object], priority_rank: Dict[str, int]) -> Tuple[int, float, int]:
    comp = str(team.get("competition", "") or "").upper()
    rank = priority_rank.get(comp, len(priority_rank) + 100)
    value = float(team.get("squad_total_value_eur") or team.get("team_market_value_eur") or 0.0)
    txt_len = byte_len_cp1252(str(team.get("team_name", ""))) + byte_len_cp1252(str(team.get("stadium_name", "")))
    return (rank, -value, -txt_len)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--extracted", type=Path, default=DEFAULT_EXTRACTED)
    parser.add_argument("--base-mapping", type=Path, default=DEFAULT_BASE_MAPPING)
    parser.add_argument("--transfermarkt", type=Path, default=DEFAULT_TM_JSON)
    parser.add_argument("--min-score", type=float, default=0.72)
    parser.add_argument(
        "--priority-competitions",
        nargs="*",
        default=DEFAULT_PRIORITY_COMPETITIONS,
        help="Competition codes ranked from highest to lowest insertion priority.",
    )
    parser.add_argument(
        "--target-counts",
        nargs="*",
        default=[],
        help='Optional quotas by competition, e.g. "ES1=20 ES2=22 GB1=20".',
    )
    parser.add_argument(
        "--only-targeted",
        action="store_true",
        help="If target counts are set, ignore competitions not present in target list.",
    )
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    slots_by_idx = load_extracted(args.extracted)
    base = load_base_mapping(args.base_mapping, args.min_score)
    tm_all = load_tm(args.transfermarkt)
    target_counts = parse_target_counts(args.target_counts)
    tm = apply_target_counts(tm_all, target_counts, args.only_targeted)

    tm_by_name = {normalize(str(t.get("team_name", ""))): t for t in tm if str(t.get("team_name", "")).strip()}

    used_slots = {int(r["pcf_index"]) for r in base}
    mapped_tm = {normalize(r["tm_team"]) for r in base}
    priority_rank = {comp.upper(): i for i, comp in enumerate(args.priority_competitions)}

    available_slots = [s for idx, s in slots_by_idx.items() if idx not in used_slots]
    available_slots.sort(key=lambda s: (s.confidence, s.team_len + s.stadium_len), reverse=True)

    unmatched_tm = [t for t in tm if normalize(str(t.get("team_name", ""))) not in mapped_tm]
    unmatched_tm.sort(key=lambda t: unmatched_sort_key(t, priority_rank))

    rows_out: List[Dict[str, object]] = []

    # Keep base matches first.
    for r in base:
        tm_team_norm = normalize(r["tm_team"])
        t = tm_by_name.get(tm_team_norm, {})
        slot = slots_by_idx.get(int(r["pcf_index"]))
        team_len = slot.team_len if slot else 0
        stadium_len = slot.stadium_len if slot else 0
        raw_team = r["tm_team"]
        raw_stadium = r["tm_stadium"]
        patch_team = patch_team_name(raw_team)
        patch_stadium = patch_stadium_name(raw_stadium)
        rows_out.append(
            {
                "pcf_index": int(r["pcf_index"]),
                "pcf_team": r["pcf_team"],
                "pcf_stadium": r["pcf_stadium"],
                "pcf_confidence": float(r["pcf_confidence"]),
                "tm_team": raw_team,
                "tm_team_patch": patch_team,
                "tm_competition": r["tm_competition"],
                "tm_stadium": raw_stadium,
                "tm_stadium_patch": patch_stadium,
                "tm_team_market_value_eur": float(r["tm_team_market_value_eur"] or 0),
                "tm_squad_total_value_eur": float(r["tm_squad_total_value_eur"] or 0),
                "score": float(r["score"]),
                "accepted": 1,
                "mapping_mode": "matched",
                "slot_team_len": team_len,
                "slot_stadium_len": stadium_len,
                "team_truncated": int(byte_len_cp1252(patch_team) > max(1, team_len)),
                "stadium_truncated": int(byte_len_cp1252(patch_stadium) > max(1, stadium_len)),
            }
        )

    # Fill unmatched with placeholders.
    still_unmatched: List[Dict[str, object]] = []
    for t in unmatched_tm:
        tm_team_norm = normalize(str(t.get("team_name", "")))
        forced_idx = FORCED_SLOT_BY_TM.get(tm_team_norm)
        if forced_idx is None:
            still_unmatched.append(t)
            continue
        slot = slots_by_idx.get(forced_idx)
        if slot is None or forced_idx in used_slots:
            still_unmatched.append(t)
            continue
        available_slots = [s for s in available_slots if s.index != forced_idx]
        used_slots.add(forced_idx)

        team_name_raw = str(t.get("team_name", "") or "")
        team_name = patch_team_name(team_name_raw)
        stadium_name_raw = str(t.get("stadium_name", "") or "")
        stadium_name = patch_stadium_name(stadium_name_raw)
        # Short stadium text for old fixed field lengths.
        if tm_team_norm == "atletico de madrid":
            stadium_name = "Metropolitano"

        rows_out.append(
            {
                "pcf_index": slot.index,
                "pcf_team": slot.team_name,
                "pcf_stadium": slot.stadium_name,
                "pcf_confidence": slot.confidence,
                "tm_team": team_name_raw,
                "tm_team_patch": team_name,
                "tm_competition": str(t.get("competition", "")),
                "tm_stadium": stadium_name_raw,
                "tm_stadium_patch": stadium_name,
                "tm_team_market_value_eur": float(t.get("team_market_value_eur") or 0),
                "tm_squad_total_value_eur": float(t.get("squad_total_value_eur") or 0),
                "score": 0.95,
                "accepted": 1,
                "mapping_mode": "placeholder-forced",
                "slot_team_len": slot.team_len,
                "slot_stadium_len": slot.stadium_len,
                "team_truncated": int(byte_len_cp1252(team_name) > max(1, slot.team_len)),
                "stadium_truncated": int(byte_len_cp1252(stadium_name) > max(1, slot.stadium_len)),
            }
        )

    unmatched_tm = still_unmatched

    for t in unmatched_tm:
        if not available_slots:
            break
        team_name_raw = str(t.get("team_name", "") or "")
        team_name = patch_team_name(team_name_raw)
        stadium_name_raw = str(t.get("stadium_name", "") or "")
        stadium_name = patch_stadium_name(stadium_name_raw)
        pick_i, cost, sim = choose_slot(team_name, stadium_name, available_slots)
        slot = available_slots.pop(pick_i)
        rows_out.append(
            {
                "pcf_index": slot.index,
                "pcf_team": slot.team_name,
                "pcf_stadium": slot.stadium_name,
                "pcf_confidence": slot.confidence,
                "tm_team": team_name_raw,
                "tm_team_patch": team_name,
                "tm_competition": str(t.get("competition", "")),
                "tm_stadium": stadium_name_raw,
                "tm_stadium_patch": stadium_name,
                "tm_team_market_value_eur": float(t.get("team_market_value_eur") or 0),
                "tm_squad_total_value_eur": float(t.get("squad_total_value_eur") or 0),
                "score": round(max(0.05, 0.55 + (sim * 0.25) - (max(0.0, cost) / 200.0)), 4),
                "accepted": 1,
                "mapping_mode": "placeholder",
                "slot_team_len": slot.team_len,
                "slot_stadium_len": slot.stadium_len,
                "team_truncated": int(byte_len_cp1252(team_name) > max(1, slot.team_len)),
                "stadium_truncated": int(byte_len_cp1252(stadium_name) > max(1, slot.stadium_len)),
            }
        )

    # Deduplicate by tm team once more (safety).
    dedup: Dict[str, Dict[str, object]] = {}
    for row in rows_out:
        key = normalize(str(row["tm_team"]))
        if key not in dedup or float(row["score"]) > float(dedup[key]["score"]):
            dedup[key] = row
    final_rows = sorted(dedup.values(), key=lambda r: (str(r["tm_competition"]), -float(r["tm_squad_total_value_eur"])))

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(
            [
                "pcf_index",
                "pcf_team",
                "pcf_stadium",
                "pcf_confidence",
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
                "slot_team_len",
                "slot_stadium_len",
                "team_truncated",
                "stadium_truncated",
            ]
        )
        for r in final_rows:
            w.writerow(
                [
                    r["pcf_index"],
                    r["pcf_team"],
                    r["pcf_stadium"],
                    f"{float(r['pcf_confidence']):.2f}",
                    r["tm_team"],
                    r["tm_team_patch"],
                    r["tm_competition"],
                    r["tm_stadium"],
                    r["tm_stadium_patch"],
                    f"{float(r['tm_team_market_value_eur']):.0f}",
                    f"{float(r['tm_squad_total_value_eur']):.0f}",
                    f"{float(r['score']):.4f}",
                    int(r["accepted"]),
                    r["mapping_mode"],
                    int(r["slot_team_len"]),
                    int(r["slot_stadium_len"]),
                    int(r["team_truncated"]),
                    int(r["stadium_truncated"]),
                ]
            )

    placeholders = sum(1 for r in final_rows if r["mapping_mode"] == "placeholder")
    trunc_team = sum(int(r["team_truncated"]) for r in final_rows)
    trunc_stadium = sum(int(r["stadium_truncated"]) for r in final_rows)
    by_comp = defaultdict(int)
    for r in final_rows:
        by_comp[str(r["tm_competition"])] += 1
    print(f"Final mapped teams: {len(final_rows)}")
    print(f"Placeholders used : {placeholders}")
    print(f"Team truncations  : {trunc_team}")
    print(f"Stadium truncations: {trunc_stadium}")
    if target_counts:
        print(f"Target counts: {target_counts}")
    print(f"By competition: {dict(sorted(by_comp.items()))}")
    print(f"Output: {args.out}")


if __name__ == "__main__":
    main()


#!/usr/bin/env python3
"""Run 5 full ProManager seasons matchday by matchday (no season skip)."""

from __future__ import annotations

import csv
import json
import os
import re
import subprocess
import sys
import time
from collections import defaultdict
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLI_CWD = ROOT / "cli"
CLI_SCRIPT = CLI_CWD / "pcfutbol_cli.py"
OUTPUT_ROOT = CLI_CWD / "qa_outputs"
CAREER_SAVE = Path.home() / ".pcfutbol_career.json"
PLAYERS_CSV = ROOT / "android" / "core" / "data" / "src" / "main" / "assets" / "pcf55_players_2526.csv"

SEASONS_TARGET = 5
MANAGER_NAME = "JORNADA_REAL"
OFFER_ROTATION = [1, 2, 3, 4, 1]
COACH_MATCHES_PER_SEASON = 2
try:
    COACH_PADDING_LINES = max(800, int(os.getenv("PCF_COACH_PADDING_LINES", "8000")))
except ValueError:
    COACH_PADDING_LINES = 8000
COACH_PADDING_TOKEN = os.getenv("PCF_COACH_PADDING_TOKEN", "0")
COACH_SPEED_FOR_BOTS = os.getenv("PCF_COACH_SPEED_FOR_BOTS", "fast").strip().lower() or "fast"

TOT_MD_BY_COMP = {
    "ES1": 38,
    "ES2": 42,
    "E3G1": 38,
    "E3G2": 38,
    "GB1": 38,
    "IT1": 38,
    "L1": 34,
    "FR1": 34,
    "NL1": 34,
    "PO1": 34,
    "BE1": 30,
    "TR1": 34,
}


def _safe_decode(raw: bytes) -> str:
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        return raw.decode("utf-8", errors="replace")


def _coach_metrics_from_stdout(stdout_text: str) -> dict[str, int]:
    ansi_clean = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", stdout_text)
    text = ansi_clean.lower()
    goals = len(re.findall(r"\bgo+ol+\b", text))
    var_reviews = text.count("var revisando") + text.count("var revisa")
    yellow_cards = text.count("amarilla para ") + text.count("tarjeta amarilla")
    red_cards = text.count("roja para ") + text.count("expulsion")
    injury_events = (
        text.count("golpe en la cabeza")
        + text.count("golpe leve")
        + text.count("lesion de ")
    )
    narrator_lines = len(re.findall(r"narrador\s*:", text))
    tactical_stops = (
        text.count("parada tactica")
        + text.count("ventana de decisiones")
        + text.count("tramo clave")
        + text.count("tras el gol")
        + text.count("decide rapido")
    )
    orders_issued = len(re.findall(r"orden\s*:", text))
    metrics: dict[str, int] = {
        "goals": goals,
        "var_reviews": var_reviews,
        "var_disallowed": text.count("gol anulado por var"),
        "yellow_cards": yellow_cards,
        "red_cards": red_cards,
        "injury_events": injury_events,
        "narrator_lines": narrator_lines,
        "tactical_stops": tactical_stops,
        "orders_issued": orders_issued,
    }
    metrics["core_events"] = (
        metrics["goals"]
        + metrics["var_reviews"]
        + metrics["yellow_cards"]
        + metrics["red_cards"]
        + metrics["injury_events"]
    )
    return metrics


def _read_career() -> dict:
    if not CAREER_SAVE.exists():
        return {}
    return json.loads(CAREER_SAVE.read_text(encoding="utf-8"))


def _load_team_names() -> dict[int, str]:
    names: dict[int, str] = {}
    with open(PLAYERS_CSV, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                slot = int(row.get("teamSlotId", "0"))
            except ValueError:
                continue
            team_name = str(row.get("teamName", "")).strip()
            if slot > 0 and team_name and slot not in names:
                names[slot] = team_name
    return names


def _run_session(
    label: str,
    input_lines: list[str | int],
    out_dir: Path,
    timeout_seconds: int = 420,
    extra_meta: dict | None = None,
    env_overrides: dict[str, str] | None = None,
) -> dict:
    out_dir.mkdir(parents=True, exist_ok=True)
    text_input = "\n".join(str(x) for x in input_lines) + "\n"

    env = os.environ.copy()
    if isinstance(env_overrides, dict):
        env.update(env_overrides)

    started_at = time.time()
    result = subprocess.run(
        [sys.executable, str(CLI_SCRIPT)],
        input=text_input.encode("utf-8"),
        capture_output=True,
        cwd=str(CLI_CWD),
        env=env,
        timeout=timeout_seconds,
    )
    elapsed_seconds = round(time.time() - started_at, 3)

    stdout_text = _safe_decode(result.stdout or b"")
    stderr_text = _safe_decode(result.stderr or b"")
    stdout_path = out_dir / f"{label}.stdout.log"
    stderr_path = out_dir / f"{label}.stderr.log"
    meta_path = out_dir / f"{label}.meta.json"

    stdout_path.write_text(stdout_text, encoding="utf-8")
    stderr_path.write_text(stderr_text, encoding="utf-8")
    meta_payload = {
        "label": label,
        "returncode": result.returncode,
        "elapsed_seconds": elapsed_seconds,
        "input_lines": [str(x) for x in input_lines],
        "stdout_path": str(stdout_path),
        "stderr_path": str(stderr_path),
    }
    if isinstance(extra_meta, dict):
        meta_payload.update(extra_meta)
    is_coach_mode = bool(meta_payload.get("coach_mode"))
    coach_metrics = _coach_metrics_from_stdout(stdout_text) if is_coach_mode else None
    if coach_metrics is not None:
        meta_payload["coach_metrics"] = coach_metrics

    meta_path.write_text(json.dumps(meta_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    payload = {
        "label": label,
        "returncode": result.returncode,
        "elapsed_seconds": elapsed_seconds,
        "stdout_path": str(stdout_path),
        "stderr_path": str(stderr_path),
        "meta_path": str(meta_path),
    }
    if coach_metrics is not None:
        payload["coach_metrics"] = coach_metrics
    return payload


def _compute_table(results: list[dict]) -> list[dict]:
    table: dict[int, dict] = defaultdict(
        lambda: {"pj": 0, "g": 0, "e": 0, "p": 0, "gf": 0, "ga": 0, "pts": 0, "team_slot": 0}
    )
    for r in results:
        h = int(r["h"])
        a = int(r["a"])
        hg = int(r["hg"])
        ag = int(r["ag"])

        hr = table[h]
        ar = table[a]
        hr["team_slot"] = h
        ar["team_slot"] = a

        hr["pj"] += 1
        ar["pj"] += 1
        hr["gf"] += hg
        hr["ga"] += ag
        ar["gf"] += ag
        ar["ga"] += hg

        if hg > ag:
            hr["g"] += 1
            ar["p"] += 1
            hr["pts"] += 3
        elif ag > hg:
            ar["g"] += 1
            hr["p"] += 1
            ar["pts"] += 3
        else:
            hr["e"] += 1
            ar["e"] += 1
            hr["pts"] += 1
            ar["pts"] += 1

    rows = list(table.values())
    for row in rows:
        row["dg"] = row["gf"] - row["ga"]
    rows.sort(key=lambda x: (-x["pts"], -x["dg"], -x["gf"], x["team_slot"]))
    return rows


def _group_results_by_matchday(results: list[dict]) -> dict[str, list[dict]]:
    out: dict[str, list[dict]] = {}
    for r in results:
        md = str(int(r["md"]))
        out.setdefault(md, []).append(
            {
                "md": int(r["md"]),
                "h": int(r["h"]),
                "a": int(r["a"]),
                "hg": int(r["hg"]),
                "ag": int(r["ag"]),
            }
        )
    return out


def _season_summary(data: dict, team_names: dict[int, str]) -> dict:
    season = str(data.get("season", "?"))
    competition = str(data.get("competition", "?"))
    team_slot = int(data.get("team_slot", -1))
    objective = str(data.get("objective", ""))
    results = data.get("results", [])
    if not isinstance(results, list):
        results = []

    table = _compute_table(results)
    grouped = _group_results_by_matchday(results)
    top5 = table[:5]
    bottom3 = table[-3:] if len(table) >= 3 else table
    champion = top5[0] if top5 else None
    manager_row = next((row for row in table if row["team_slot"] == team_slot), None)
    manager_pos = table.index(manager_row) + 1 if manager_row in table else None

    history = data.get("manager", {}).get("history", [])
    history_entry = None
    if isinstance(history, list):
        for item in reversed(history):
            if str(item.get("season", "")) == season:
                history_entry = item
                break

    def add_names(rows: list[dict]) -> list[dict]:
        out = []
        for row in rows:
            row_copy = dict(row)
            row_copy["team_name"] = team_names.get(int(row_copy["team_slot"]), f"#{row_copy['team_slot']}")
            out.append(row_copy)
        return out

    champion_info = None
    if champion:
        champion_info = {
            "team_slot": int(champion["team_slot"]),
            "team_name": team_names.get(int(champion["team_slot"]), f"#{champion['team_slot']}"),
            "points": int(champion["pts"]),
        }

    manager_row_with_name = None
    if manager_row:
        manager_row_with_name = dict(manager_row)
        manager_row_with_name["team_name"] = team_names.get(int(team_slot), str(data.get("team_name", "")))

    return {
        "season": season,
        "competition": competition,
        "manager_team_slot": team_slot,
        "manager_team_name": str(data.get("team_name", "")),
        "objective": objective,
        "history_entry": history_entry,
        "champion": champion_info,
        "manager_position": manager_pos,
        "manager_row": manager_row_with_name,
        "total_matches": len(results),
        "matchdays": len(grouped),
        "top5": add_names(top5),
        "bottom3": add_names(bottom3),
        "results_by_matchday": grouped,
    }


def _assert_ok(session_result: dict):
    if int(session_result["returncode"]) != 0:
        raise RuntimeError(
            f"Session {session_result['label']} failed with exit code {session_result['returncode']}. "
            f"See {session_result['stdout_path']} and {session_result['stderr_path']}"
        )


def main() -> int:
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    run_dir = OUTPUT_ROOT / f"jornada_real_{stamp}"
    session_dir = run_dir / "sessions"
    run_dir.mkdir(parents=True, exist_ok=True)
    session_dir.mkdir(parents=True, exist_ok=True)

    team_names = _load_team_names()
    index = {
        "run_type": "jornada_real_5seasons",
        "manager_name": MANAGER_NAME,
        "target_seasons": SEASONS_TARGET,
        "script": str(CLI_SCRIPT),
        "cwd": str(CLI_CWD),
        "run_dir": str(run_dir),
        "sessions": [],
        "seasons": [],
    }

    if CAREER_SAVE.exists():
        backup_path = run_dir / "career_backup_before_run.json"
        backup_path.write_text(CAREER_SAVE.read_text(encoding="utf-8"), encoding="utf-8")
        CAREER_SAVE.unlink()
        print(f"[run] existing save backed up to: {backup_path}")

    print("[run] creating fresh manager and first season setup...")
    setup_offer = OFFER_ROTATION[0]
    first_setup = _run_session(
        "s01_setup_new_manager",
        [6, MANAGER_NAME, 2, setup_offer, 0, 0],
        session_dir,
        extra_meta={"coach_mode": False, "phase": "setup", "season_index": 1},
        env_overrides={"PCF_COACH_SPEED": COACH_SPEED_FOR_BOTS},
    )
    _assert_ok(first_setup)
    index["sessions"].append(first_setup)

    completed_seasons = 0
    while completed_seasons < SEASONS_TARGET:
        data = _read_career()
        if not data:
            raise RuntimeError("Career save missing during run.")
        if str(data.get("phase")) != "SEASON":
            raise RuntimeError(f"Expected phase SEASON, got: {data.get('phase')}")

        season_name = str(data.get("season", "?"))
        comp = str(data.get("competition", "ES1"))
        current_md = int(data.get("current_matchday", 1))
        total_md = int(TOT_MD_BY_COMP.get(comp, 38))
        is_last_md = current_md == total_md
        coach_mode = current_md <= COACH_MATCHES_PER_SEASON

        label = f"s{completed_seasons + 1:02d}_md{current_md:02d}"
        mode_label = "fichitas" if coach_mode else "auto"
        print(
            f"[run] season {completed_seasons + 1}/{SEASONS_TARGET} | "
            f"{season_name} | J{current_md}/{total_md} | {mode_label}"
        )

        # ProManager -> Continue -> Simulate this matchday -> Mode (1 auto, 2 coach).
        session_inputs: list[str | int] = [6, 1, 1, 2 if coach_mode else 1]
        if coach_mode:
            # Consume all coach prompts with a safe default that works for text and numeric menus.
            session_inputs.extend([COACH_PADDING_TOKEN] * COACH_PADDING_LINES)
        if is_last_md:
            # End-of-season screen -> Exit to main menu.
            session_inputs.extend([2, 0])
        else:
            # Back to season menu -> Save and exit -> Exit main menu.
            session_inputs.extend([0, 0])

        matchday_session = _run_session(
            label,
            session_inputs,
            session_dir,
            extra_meta={
                "coach_mode": coach_mode,
                "phase": "matchday",
                "season_index": completed_seasons + 1,
                "season": season_name,
                "matchday": current_md,
                "competition": comp,
            },
            env_overrides={"PCF_COACH_SPEED": COACH_SPEED_FOR_BOTS},
        )
        matchday_session["coach_mode"] = coach_mode
        matchday_session["season"] = season_name
        matchday_session["matchday"] = current_md
        _assert_ok(matchday_session)
        index["sessions"].append(matchday_session)

        if not is_last_md:
            continue

        post_data = _read_career()
        if not post_data:
            raise RuntimeError("Career save missing after season end.")
        if str(post_data.get("phase")) != "POSTSEASON":
            raise RuntimeError(f"Expected phase POSTSEASON, got: {post_data.get('phase')}")

        completed_seasons += 1
        summary = _season_summary(post_data, team_names)
        summary["season_index"] = completed_seasons

        summary_season = str(summary.get("season", season_name))
        summary_path = run_dir / f"season_{completed_seasons:02d}_{summary_season}.summary.json"
        career_copy = run_dir / f"season_{completed_seasons:02d}_{summary_season}.career.json"
        summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
        career_copy.write_text(json.dumps(post_data, ensure_ascii=False, indent=2), encoding="utf-8")

        season_index_entry = {
            "season_index": completed_seasons,
            "season": summary_season,
            "competition": summary.get("competition"),
            "manager_team": summary.get("manager_team_name"),
            "objective": summary.get("objective"),
            "manager_position": summary.get("manager_position"),
            "objective_met": bool((summary.get("history_entry") or {}).get("met", False)),
            "summary_file": str(summary_path),
            "career_file": str(career_copy),
        }
        index["seasons"].append(season_index_entry)

        print(
            "[run] completed season "
            f"{completed_seasons}/{SEASONS_TARGET}: {summary_season} | "
            f"{summary.get('manager_team_name')} | pos {summary.get('manager_position')}"
        )

        if completed_seasons >= SEASONS_TARGET:
            break

        next_offer = OFFER_ROTATION[completed_seasons % len(OFFER_ROTATION)]
        next_setup_label = f"s{completed_seasons + 1:02d}_setup_next_season"
        print(f"[run] starting next season with offer option {next_offer}...")
        next_setup = _run_session(
            next_setup_label,
            [6, 1, next_offer, 0, 0],
            session_dir,
            extra_meta={"coach_mode": False, "phase": "setup", "season_index": completed_seasons + 1},
            env_overrides={"PCF_COACH_SPEED": COACH_SPEED_FOR_BOTS},
        )
        _assert_ok(next_setup)
        index["sessions"].append(next_setup)

    coach_sessions = [
        s for s in index["sessions"]
        if bool(s.get("coach_mode")) and isinstance(s.get("elapsed_seconds"), (int, float))
    ]
    if coach_sessions:
        durations = [float(s["elapsed_seconds"]) for s in coach_sessions]
        index["coach_match_timing"] = {
            "matches_count": len(durations),
            "min_seconds": round(min(durations), 3),
            "max_seconds": round(max(durations), 3),
            "avg_seconds": round(sum(durations) / len(durations), 3),
        }
    else:
        index["coach_match_timing"] = {
            "matches_count": 0,
            "min_seconds": None,
            "max_seconds": None,
            "avg_seconds": None,
        }

    coach_metrics_all = [
        s.get("coach_metrics")
        for s in coach_sessions
        if isinstance(s.get("coach_metrics"), dict)
    ]
    if coach_metrics_all:
        tracked = [
            "core_events",
            "goals",
            "var_reviews",
            "var_disallowed",
            "yellow_cards",
            "red_cards",
            "injury_events",
            "tactical_stops",
            "orders_issued",
            "narrator_lines",
        ]
        summary: dict[str, dict[str, float | int]] = {}
        for key in tracked:
            vals = [int(m.get(key, 0)) for m in coach_metrics_all]
            summary[key] = {
                "total": int(sum(vals)),
                "avg_per_match": round(sum(vals) / len(vals), 2),
                "min_per_match": int(min(vals)),
                "max_per_match": int(max(vals)),
            }
        index["coach_engagement"] = {
            "matches_count": len(coach_metrics_all),
            "speed_mode_for_run": COACH_SPEED_FOR_BOTS,
            "metrics": summary,
        }
    else:
        index["coach_engagement"] = {
            "matches_count": 0,
            "speed_mode_for_run": COACH_SPEED_FOR_BOTS,
            "metrics": {},
        }

    index_path = run_dir / "index.json"
    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8")

    latest_path = OUTPUT_ROOT / "jornada_real_latest.json"
    latest_path.write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8")

    print("[run] done.")
    print(f"[run] index: {index_path}")
    print(f"[run] latest: {latest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

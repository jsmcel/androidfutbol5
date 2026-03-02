#!/usr/bin/env python3
"""Run 5 full ProManager seasons matchday by matchday with human-like decisions."""

from __future__ import annotations

import csv
import json
import os
import random
import re
import subprocess
import sys
import time
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any


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
try:
    SESSION_TIMEOUT_SECONDS = max(420, int(os.getenv("PCF_QA_SESSION_TIMEOUT_SECONDS", "900")))
except ValueError:
    SESSION_TIMEOUT_SECONDS = 900

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

TEAM_COUNT_BY_COMP = {
    "ES1": 20,
    "ES2": 22,
    "E3G1": 20,
    "E3G2": 20,
    "GB1": 20,
    "IT1": 20,
    "L1": 18,
    "FR1": 18,
    "NL1": 18,
    "PO1": 18,
    "BE1": 16,
    "TR1": 18,
}


def _safe_int(value: Any, default: int) -> int:
    try:
        return int(value)
    except Exception:
        return default


def _safe_decode(raw: bytes) -> str:
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        return raw.decode("utf-8", errors="replace")


def _strip_ansi(text: str) -> str:
    return re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", text or "")


def _coach_metrics_from_stdout(stdout_text: str) -> dict[str, int]:
    ansi_clean = _strip_ansi(stdout_text)
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
    timeout_seconds: int = SESSION_TIMEOUT_SECONDS,
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


def _manager_table_context(data: dict) -> tuple[int | None, int, list[dict]]:
    results = data.get("results", [])
    if not isinstance(results, list):
        results = []
    table = _compute_table(results) if results else []
    team_slot = _safe_int(data.get("team_slot", -1), -1)
    manager_row = next((row for row in table if _safe_int(row.get("team_slot", -1), -1) == team_slot), None)
    manager_pos = table.index(manager_row) + 1 if manager_row in table else None

    total_teams = len(table)
    if total_teams <= 0:
        comp = str(data.get("competition", "ES1"))
        total_teams = TEAM_COUNT_BY_COMP.get(comp, 20)
    return manager_pos, total_teams, table


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


def _window_open(matchday: int, total_md: int) -> bool:
    w_start = 19 if total_md == 38 else 22
    w_end = 23 if total_md == 38 else 26
    return matchday <= 5 or w_start <= matchday <= w_end


def _coach_matchdays(total_md: int) -> set[int]:
    if COACH_MATCHES_PER_SEASON <= 0:
        return set()
    if total_md <= 2:
        return set(range(1, total_md + 1))

    early = 2 if total_md >= 10 else 1
    mid = max(early + 1, total_md // 2)
    late = max(mid + 1, total_md - 4)
    candidates = [early, mid, late, total_md]

    out: list[int] = []
    for md in candidates:
        md = max(1, min(total_md, int(md)))
        if md not in out:
            out.append(md)
        if len(out) >= COACH_MATCHES_PER_SEASON:
            break
    return set(out)


def _coach_padding_inputs() -> list[str]:
    pattern = ["", "A", "0", "P", "0", "D", "0", "C", "0", "W", "0", "0", COACH_PADDING_TOKEN]
    if COACH_PADDING_LINES <= 0:
        return []
    out: list[str] = []
    while len(out) < COACH_PADDING_LINES:
        out.extend(pattern)
    return out[:COACH_PADDING_LINES]


def _choose_declaration_option(
    data: dict,
    manager_pos: int | None,
    total_teams: int,
    current_md: int,
    rng: random.Random,
) -> int | None:
    market = data.get("market", {}) if isinstance(data.get("market"), dict) else {}
    profile = data.get("president", {}) if isinstance(data.get("president"), dict) else {}

    last_statement_md = _safe_int(market.get("last_statement_md", 0), 0)
    if last_statement_md == current_md:
        return None

    verdict = str(market.get("referee_verdict", "NEUTRAL")).upper()
    pressure = _safe_int(profile.get("pressure", 0), 0)
    mood = _safe_int(market.get("fan_mood", 55), 55)

    if verdict == "HARMED":
        return 6 if pressure >= 7 else 3
    if manager_pos is not None and manager_pos >= max(5, int(total_teams * 0.70)):
        return 5 if pressure >= 8 else 2
    if manager_pos is not None and manager_pos <= max(2, int(total_teams * 0.20)) and current_md % 5 == 0:
        return 1
    if pressure >= 9 and current_md % 2 == 0:
        return 4
    if mood <= 38 and current_md % 4 == 0:
        return 2
    if current_md % 9 == 0 and rng.random() < 0.45:
        return 4
    return None


def _choose_president_action(
    data: dict,
    current_md: int,
    total_md: int,
    rng: random.Random,
) -> int | None:
    market = data.get("market", {}) if isinstance(data.get("market"), dict) else {}
    profile = data.get("president", {}) if isinstance(data.get("president"), dict) else {}

    budget = _safe_int(data.get("budget", 0), 0)
    pressure = _safe_int(profile.get("pressure", 0), 0)
    investor_rounds = _safe_int(profile.get("investor_rounds", 0), 0)
    ownership = str(profile.get("ownership", "SOCIOS")).upper()
    ipo_done = bool(profile.get("ipo_done", False))
    pelotazo_done = bool(profile.get("pelotazo_done", False))

    shirt_price = _safe_int(market.get("shirt_price_eur", 70), 70)
    press = _safe_int(market.get("press_rating", 50), 50)
    channel = _safe_int(market.get("channel_level", 45), 45)
    mood = _safe_int(market.get("fan_mood", 55), 55)

    if current_md <= 2:
        return None
    if pressure >= 9 and investor_rounds < 2 and budget < 20_000_000:
        return 2
    if pressure >= 10 and ownership != "STATE" and budget < 25_000_000 and current_md > total_md // 3:
        return 3
    if not ipo_done and current_md >= total_md // 2 and pressure >= 7 and rng.random() < 0.35:
        return 4
    if not pelotazo_done and current_md >= total_md // 2 and budget < 12_000_000 and rng.random() < 0.40:
        return 5
    if budget > 35_000_000 and channel < 68 and current_md % 4 == 0:
        return 9
    if budget > 24_000_000 and press < 65 and current_md % 3 == 0:
        return 8
    if shirt_price > 90 and mood < 50:
        return 7
    if shirt_price < 55 and mood > 75 and current_md % 6 == 0:
        return 6
    return None


def _choose_training_adjustment(
    manager_pos: int | None,
    total_teams: int,
    current_md: int,
    total_md: int,
) -> tuple[int, int] | None:
    checkpoints = {1, max(2, total_md // 3), max(3, (2 * total_md) // 3)}
    if current_md not in checkpoints:
        return None

    if manager_pos is None:
        return (2, 1)
    if manager_pos >= max(5, int(total_teams * 0.65)):
        return (3, 5)  # Alta + Ataque
    if manager_pos <= max(2, int(total_teams * 0.25)):
        return (2, 3)  # Media + Defensivo
    return (2, 1)      # Media + Equilibrado


def _build_tactic_adjustment_inputs(
    data: dict,
    manager_pos: int | None,
    total_teams: int,
    current_md: int,
    total_md: int,
) -> tuple[list[int], list[str]]:
    checkpoints = {1, max(4, total_md // 4), max(6, total_md // 2), max(8, (3 * total_md) // 4)}
    if current_md not in checkpoints:
        return [], []

    tactic = data.get("tactic", {}) if isinstance(data.get("tactic"), dict) else {}
    perda_tiempo = _safe_int(tactic.get("perdidaTiempo", 0), 0)
    inputs: list[int] = [7]
    actions: list[str] = []

    if manager_pos is not None and manager_pos >= max(5, int(total_teams * 0.65)):
        # Si vamos mal, empujar ofensivamente.
        inputs.extend([1, 3, 3, 3])
        actions.append("tactic_push")
        if perda_tiempo == 1:
            inputs.append(8)  # desactivar perdida de tiempo
            actions.append("tactic_disable_time_waste")
    elif manager_pos is not None and manager_pos <= max(2, int(total_teams * 0.25)) and current_md >= int(total_md * 0.70):
        # Si vamos arriba al final, cerrar partidos.
        inputs.extend([1, 1, 3, 2])
        actions.append("tactic_close_games")
        if perda_tiempo == 0:
            inputs.append(8)  # activar perdida de tiempo
            actions.append("tactic_enable_time_waste")
    else:
        inputs.extend([1, 2, 3, 2])
        actions.append("tactic_balance")
        if perda_tiempo == 1 and current_md < int(total_md * 0.55):
            inputs.append(8)
            actions.append("tactic_disable_time_waste")

    inputs.append(0)
    return inputs, actions


def _build_matchday_inputs(
    data: dict,
    season_index: int,
    current_md: int,
    total_md: int,
    coach_mode: bool,
) -> tuple[list[str | int], list[str], dict[str, int | None]]:
    manager_pos, total_teams, _ = _manager_table_context(data)
    seed = _safe_int(data.get("season_seed", 0), 0) ^ (season_index * 100_003) ^ (current_md * 1_009)
    rng = random.Random(seed)

    inputs: list[str | int] = [6, 1]  # Main menu -> ProManager continue
    actions: list[str] = []

    # 1) Ajustes de entrenamiento/staff en checkpoints.
    training_adj = _choose_training_adjustment(manager_pos, total_teams, current_md, total_md)
    if training_adj is not None:
        intensity, focus = training_adj
        inputs.extend([8, 1, intensity, 2, focus, 0])
        actions.append(f"training_i{intensity}_f{focus}")

    # 2) Ajustes tacticos para simular toma de decisiones de entrenador.
    tactic_inputs, tactic_actions = _build_tactic_adjustment_inputs(
        data=data,
        manager_pos=manager_pos,
        total_teams=total_teams,
        current_md=current_md,
        total_md=total_md,
    )
    if tactic_inputs:
        inputs.extend(tactic_inputs)
        actions.extend(tactic_actions)

    # 3) Revision de mercado en ventanas abiertas.
    if _window_open(current_md, total_md) and current_md % 3 == 1:
        # Flujo estable: abrir mercado -> ver plantilla -> volver.
        inputs.extend([6, 3, 0])
        actions.append("market_review")

    # 4) Intervenciones puntuales del presidente.
    president_action = _choose_president_action(data, current_md, total_md, rng)
    if president_action is not None:
        inputs.extend([11, president_action, 0])
        actions.append(f"president_{president_action}")

    # 5) Declaraciones pre-partido cuando proceda.
    declaration_option = _choose_declaration_option(data, manager_pos, total_teams, current_md, rng)
    if declaration_option is not None:
        inputs.extend([13, declaration_option])
        actions.append(f"declaration_{declaration_option}")

    # 6) Jugar jornada.
    inputs.extend([1, 2 if coach_mode else 1])
    if coach_mode:
        inputs.extend(_coach_padding_inputs())
        actions.append("coach_match")
    else:
        actions.append("auto_match")

    return inputs, actions, {"manager_pos_before_md": manager_pos, "total_teams": total_teams}


def _extract_news_delta(pre_data: dict, post_data: dict, max_items: int = 8) -> list[str]:
    pre_news = pre_data.get("news", [])
    post_news = post_data.get("news", [])
    if not isinstance(pre_news, list):
        pre_news = []
    if not isinstance(post_news, list):
        post_news = []

    if len(post_news) >= len(pre_news):
        delta = post_news[len(pre_news):]
    else:
        delta = post_news[-max_items:]
    return [str(x) for x in delta][-max_items:]


def _extract_manager_match(
    post_data: dict,
    matchday: int,
    team_names: dict[int, str],
) -> dict | None:
    results = post_data.get("results", [])
    if not isinstance(results, list):
        return None
    team_slot = _safe_int(post_data.get("team_slot", -1), -1)
    md_results = [r for r in results if _safe_int(r.get("md", 0), 0) == matchday]
    manager_match = next(
        (
            r for r in md_results
            if _safe_int(r.get("h", -1), -1) == team_slot or _safe_int(r.get("a", -1), -1) == team_slot
        ),
        None,
    )
    if not isinstance(manager_match, dict):
        return None

    home_slot = _safe_int(manager_match.get("h", -1), -1)
    away_slot = _safe_int(manager_match.get("a", -1), -1)
    home_goals = _safe_int(manager_match.get("hg", 0), 0)
    away_goals = _safe_int(manager_match.get("ag", 0), 0)
    manager_is_home = home_slot == team_slot

    goals_for = home_goals if manager_is_home else away_goals
    goals_against = away_goals if manager_is_home else home_goals
    opponent_slot = away_slot if manager_is_home else home_slot
    opponent_name = team_names.get(opponent_slot, f"#{opponent_slot}")

    if goals_for > goals_against:
        outcome = "W"
    elif goals_for < goals_against:
        outcome = "L"
    else:
        outcome = "D"

    return {
        "home_slot": home_slot,
        "away_slot": away_slot,
        "home_goals": home_goals,
        "away_goals": away_goals,
        "manager_is_home": manager_is_home,
        "goals_for": goals_for,
        "goals_against": goals_against,
        "opponent_slot": opponent_slot,
        "opponent_name": opponent_name,
        "outcome": outcome,
        "scoreline": f"{goals_for}-{goals_against}",
    }


def _build_timeline_entry(
    pre_data: dict,
    post_data: dict,
    season_index: int,
    season_name: str,
    competition: str,
    matchday: int,
    total_md: int,
    team_names: dict[int, str],
    actions: list[str],
    coach_mode: bool,
    decision_meta: dict[str, int | None],
    session_result: dict,
) -> dict:
    manager_pos_after, total_teams_after, _ = _manager_table_context(post_data)
    manager_match = _extract_manager_match(post_data, matchday, team_names)
    news_delta = _extract_news_delta(pre_data, post_data)

    market = post_data.get("market", {}) if isinstance(post_data.get("market"), dict) else {}
    president = post_data.get("president", {}) if isinstance(post_data.get("president"), dict) else {}

    entry = {
        "season_index": season_index,
        "season": season_name,
        "competition": competition,
        "matchday": matchday,
        "total_matchdays": total_md,
        "coach_mode": coach_mode,
        "actions": actions,
        "decision_context": decision_meta,
        "manager_team_slot": _safe_int(post_data.get("team_slot", -1), -1),
        "manager_team_name": str(post_data.get("team_name", "")),
        "manager_position_after_md": manager_pos_after,
        "table_size_after_md": total_teams_after,
        "budget_after_md": _safe_int(post_data.get("budget", 0), 0),
        "president_pressure_after_md": _safe_int(president.get("pressure", 0), 0),
        "market_fan_mood_after_md": _safe_int(market.get("fan_mood", 55), 55),
        "market_press_after_md": _safe_int(market.get("press_rating", 50), 50),
        "market_channel_after_md": _safe_int(market.get("channel_level", 45), 45),
        "referee_verdict_after_md": str(market.get("referee_verdict", "NEUTRAL")).upper(),
        "news_delta": news_delta,
        "session_elapsed_seconds": float(session_result.get("elapsed_seconds", 0.0)),
        "session_label": str(session_result.get("label", "")),
    }
    if manager_match is not None:
        entry["manager_match"] = manager_match
    coach_metrics = session_result.get("coach_metrics")
    if isinstance(coach_metrics, dict):
        entry["coach_metrics"] = coach_metrics
    return entry


def _summarize_actions(timeline: list[dict]) -> dict[str, int]:
    counts: dict[str, int] = defaultdict(int)
    for item in timeline:
        actions = item.get("actions", [])
        if not isinstance(actions, list):
            continue
        for action in actions:
            counts[str(action)] += 1
    return dict(sorted(counts.items(), key=lambda kv: (-kv[1], kv[0])))


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
    index: dict[str, Any] = {
        "run_type": "jornada_real_5seasons_human_decisions",
        "manager_name": MANAGER_NAME,
        "target_seasons": SEASONS_TARGET,
        "script": str(CLI_SCRIPT),
        "cwd": str(CLI_CWD),
        "run_dir": str(run_dir),
        "coach_speed_for_run": COACH_SPEED_FOR_BOTS,
        "sessions": [],
        "seasons": [],
        "timeline": [],
        "coach_matchdays_plan": {},
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
        [6, MANAGER_NAME, 3, setup_offer, 0, 0],
        session_dir,
        extra_meta={"coach_mode": False, "phase": "setup", "season_index": 1},
        env_overrides={"PCF_COACH_SPEED": COACH_SPEED_FOR_BOTS},
    )
    _assert_ok(first_setup)
    index["sessions"].append(first_setup)

    completed_seasons = 0
    coach_plan_cache: dict[str, set[int]] = {}

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

        season_key = f"{completed_seasons + 1}:{season_name}:{comp}:{total_md}"
        if season_key not in coach_plan_cache:
            coach_plan_cache[season_key] = _coach_matchdays(total_md)
            index["coach_matchdays_plan"][f"S{completed_seasons + 1:02d} {season_name}"] = sorted(
                coach_plan_cache[season_key]
            )
        coach_mode = current_md in coach_plan_cache[season_key]

        label = f"s{completed_seasons + 1:02d}_md{current_md:02d}"
        mode_label = "fichitas" if coach_mode else "auto"
        print(
            f"[run] season {completed_seasons + 1}/{SEASONS_TARGET} | "
            f"{season_name} | J{current_md}/{total_md} | {mode_label}"
        )

        session_inputs, actions, decision_meta = _build_matchday_inputs(
            data=data,
            season_index=completed_seasons + 1,
            current_md=current_md,
            total_md=total_md,
            coach_mode=coach_mode,
        )

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
                "actions": actions,
                "decision_meta": decision_meta,
            },
            env_overrides={"PCF_COACH_SPEED": COACH_SPEED_FOR_BOTS},
        )
        matchday_session["coach_mode"] = coach_mode
        matchday_session["season"] = season_name
        matchday_session["matchday"] = current_md
        matchday_session["actions"] = actions
        _assert_ok(matchday_session)
        index["sessions"].append(matchday_session)

        post_data_after_md = _read_career()
        if not post_data_after_md:
            raise RuntimeError("Career save missing after matchday session.")

        timeline_entry = _build_timeline_entry(
            pre_data=data,
            post_data=post_data_after_md,
            season_index=completed_seasons + 1,
            season_name=season_name,
            competition=comp,
            matchday=current_md,
            total_md=total_md,
            team_names=team_names,
            actions=actions,
            coach_mode=coach_mode,
            decision_meta=decision_meta,
            session_result=matchday_session,
        )
        index["timeline"].append(timeline_entry)

        if not is_last_md:
            continue

        post_data = post_data_after_md
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

    timeline = index.get("timeline", [])
    if isinstance(timeline, list):
        index["human_actions_summary"] = _summarize_actions(timeline)
    else:
        index["human_actions_summary"] = {}

    knowledge_base = {
        "manager_name": MANAGER_NAME,
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "target_seasons": SEASONS_TARGET,
        "coach_speed_for_run": COACH_SPEED_FOR_BOTS,
        "seasons": index.get("seasons", []),
        "timeline": index.get("timeline", []),
        "coach_engagement": index.get("coach_engagement", {}),
        "human_actions_summary": index.get("human_actions_summary", {}),
    }
    knowledge_path = run_dir / "qa_knowledge_base.json"
    knowledge_path.write_text(json.dumps(knowledge_base, ensure_ascii=False, indent=2), encoding="utf-8")
    index["knowledge_base_file"] = str(knowledge_path)

    index_path = run_dir / "index.json"
    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8")

    latest_path = OUTPUT_ROOT / "jornada_real_latest.json"
    latest_path.write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8")

    print("[run] done.")
    print(f"[run] index: {index_path}")
    print(f"[run] knowledge: {knowledge_path}")
    print(f"[run] latest: {latest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

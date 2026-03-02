#!/usr/bin/env python3
"""Guardrail de regresion para la QA jornada a jornada de ProManager."""

from __future__ import annotations

import argparse
import json
import statistics
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INDEX = ROOT / "cli" / "qa_outputs" / "jornada_real_latest.json"


def _coerce_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except Exception:
        return default


def _coerce_float(value: Any, default: float = 0.0) -> float:
    try:
        return float(value)
    except Exception:
        return default


def _validate(args: argparse.Namespace, payload: dict[str, Any]) -> tuple[list[str], dict[str, Any]]:
    errors: list[str] = []
    summary: dict[str, Any] = {}

    run_type = str(payload.get("run_type", ""))
    if not run_type.startswith("jornada_real_5seasons"):
        errors.append(f"run_type invalido: {run_type!r}")

    target_seasons = _coerce_int(payload.get("target_seasons", 0))
    if target_seasons < args.min_seasons:
        errors.append(
            f"target_seasons={target_seasons} por debajo de minimo requerido ({args.min_seasons})"
        )
    summary["target_seasons"] = target_seasons

    sessions = payload.get("sessions", [])
    if not isinstance(sessions, list) or not sessions:
        errors.append("sessions vacio o no es lista")
        sessions = []
    else:
        failed_sessions = [s for s in sessions if _coerce_int(s.get("returncode", 1), 1) != 0]
        if failed_sessions:
            labels = ", ".join(str(s.get("label", "?")) for s in failed_sessions[:8])
            errors.append(f"hay sesiones fallidas (returncode!=0): {labels}")
    summary["sessions_count"] = len(sessions)

    seasons = payload.get("seasons", [])
    if not isinstance(seasons, list):
        errors.append("seasons no es lista")
        seasons = []
    if len(seasons) < args.min_seasons:
        errors.append(
            f"seasons completadas={len(seasons)} por debajo de minimo requerido ({args.min_seasons})"
        )
    summary["seasons_count"] = len(seasons)

    season_md_map: dict[int, int] = {}
    for season in seasons:
        if not isinstance(season, dict):
            continue
        season_index = _coerce_int(season.get("season_index", 0))
        career_file = Path(str(season.get("career_file", "")))
        summary_file = Path(str(season.get("summary_file", "")))
        if season_index <= 0:
            errors.append(f"season_index invalido en seasons: {season!r}")
            continue
        if not career_file.exists():
            errors.append(f"falta career_file de temporada {season_index}: {career_file}")
        if not summary_file.exists():
            errors.append(f"falta summary_file de temporada {season_index}: {summary_file}")
        season_md_map[season_index] = _coerce_int(season.get("tot_md", 0))

    timeline = payload.get("timeline", [])
    if not isinstance(timeline, list):
        errors.append("timeline no es lista")
        timeline = []
    if not timeline:
        errors.append("timeline vacio")
    summary["timeline_entries"] = len(timeline)

    timeline_md_counter: dict[int, int] = {}
    manager_goals_totals: list[int] = []
    coach_mode_count = 0
    for row in timeline:
        if not isinstance(row, dict):
            continue
        season_index = _coerce_int(row.get("season_index", 0))
        timeline_md_counter[season_index] = timeline_md_counter.get(season_index, 0) + 1
        if bool(row.get("coach_mode")):
            coach_mode_count += 1
        manager_match = row.get("manager_match")
        if isinstance(manager_match, dict):
            gf = _coerce_int(manager_match.get("goals_for", 0))
            ga = _coerce_int(manager_match.get("goals_against", 0))
            manager_goals_totals.append(gf + ga)

    for season_index, expected_md in season_md_map.items():
        observed_md = timeline_md_counter.get(season_index, 0)
        if expected_md > 0 and observed_md != expected_md:
            errors.append(
                f"temporada {season_index}: timeline tiene {observed_md} jornadas y esperaba {expected_md}"
            )

    min_coach_matches = args.min_seasons * args.min_coach_matches_per_season
    if coach_mode_count < min_coach_matches:
        errors.append(
            f"coach matches insuficientes: {coach_mode_count} < {min_coach_matches}"
        )
    summary["coach_mode_matches"] = coach_mode_count

    if not manager_goals_totals:
        errors.append("no se detectaron manager_match en timeline")
    else:
        avg_goals = sum(manager_goals_totals) / len(manager_goals_totals)
        median_goals = statistics.median(manager_goals_totals)
        max_goals = max(manager_goals_totals)
        summary["manager_matches"] = len(manager_goals_totals)
        summary["avg_goals_manager_match"] = round(avg_goals, 3)
        summary["median_goals_manager_match"] = float(median_goals)
        summary["max_goals_manager_match"] = max_goals
        if avg_goals < args.min_avg_goals or avg_goals > args.max_avg_goals:
            errors.append(
                "media de goles por partido de manager fuera de rango: "
                f"{avg_goals:.2f} (esperado {args.min_avg_goals:.2f}..{args.max_avg_goals:.2f})"
            )

    coach_timing = payload.get("coach_match_timing", {})
    if not isinstance(coach_timing, dict):
        errors.append("coach_match_timing no es objeto")
        coach_timing = {}
    coach_timing_count = _coerce_int(coach_timing.get("matches_count", 0))
    coach_timing_avg = _coerce_float(coach_timing.get("avg_seconds", 0.0))
    summary["coach_timing_matches"] = coach_timing_count
    summary["coach_timing_avg_seconds"] = round(coach_timing_avg, 3)
    if coach_timing_count < min_coach_matches:
        errors.append(
            f"coach_match_timing.matches_count={coach_timing_count} < {min_coach_matches}"
        )

    speed_mode = str(payload.get("coach_speed_for_run", "")).lower()
    summary["coach_speed_for_run"] = speed_mode
    if speed_mode in {"fast", "bot", "bots"}:
        if coach_timing_avg < args.fast_min_seconds or coach_timing_avg > args.fast_max_seconds:
            errors.append(
                "duracion media fichitas en modo fast fuera de rango: "
                f"{coach_timing_avg:.2f}s (esperado {args.fast_min_seconds:.2f}..{args.fast_max_seconds:.2f})"
            )
    elif speed_mode in {"human", "humano"}:
        if coach_timing_avg < args.human_min_seconds or coach_timing_avg > args.human_max_seconds:
            errors.append(
                "duracion media fichitas en modo human fuera de rango: "
                f"{coach_timing_avg:.2f}s (esperado {args.human_min_seconds:.0f}..{args.human_max_seconds:.0f}s)"
            )

    coach_engagement = payload.get("coach_engagement", {})
    if not isinstance(coach_engagement, dict):
        errors.append("coach_engagement no es objeto")
        coach_engagement = {}
    metrics = coach_engagement.get("metrics", {})
    if not isinstance(metrics, dict):
        errors.append("coach_engagement.metrics no es objeto")
        metrics = {}
    summary["coach_engagement_matches"] = _coerce_int(
        coach_engagement.get("matches_count", 0), 0
    )

    def avg_metric(metric_name: str) -> float:
        metric_obj = metrics.get(metric_name, {})
        if not isinstance(metric_obj, dict):
            return 0.0
        return _coerce_float(metric_obj.get("avg_per_match", 0.0), 0.0)

    core_avg = avg_metric("core_events")
    tactical_avg = avg_metric("tactical_stops")
    orders_avg = avg_metric("orders_issued")
    summary["core_events_avg"] = round(core_avg, 3)
    summary["tactical_stops_avg"] = round(tactical_avg, 3)
    summary["orders_issued_avg"] = round(orders_avg, 3)

    if core_avg < args.min_core_events_avg:
        errors.append(
            f"core_events.avg_per_match demasiado bajo: {core_avg:.2f} < {args.min_core_events_avg:.2f}"
        )
    if tactical_avg < args.min_tactical_stops_avg:
        errors.append(
            f"tactical_stops.avg_per_match demasiado bajo: {tactical_avg:.2f} < {args.min_tactical_stops_avg:.2f}"
        )
    if orders_avg < args.min_orders_avg:
        errors.append(
            f"orders_issued.avg_per_match demasiado bajo: {orders_avg:.2f} < {args.min_orders_avg:.2f}"
        )

    return errors, summary


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Valida consistencia del run QA jornada a jornada (5 temporadas).",
    )
    parser.add_argument(
        "--index",
        type=Path,
        default=DEFAULT_INDEX,
        help="Ruta al index JSON de salida (por defecto: jornada_real_latest.json)",
    )
    parser.add_argument("--min-seasons", type=int, default=5)
    parser.add_argument("--min-coach-matches-per-season", type=int, default=2)
    parser.add_argument("--min-avg-goals", type=float, default=1.8)
    parser.add_argument("--max-avg-goals", type=float, default=4.2)
    parser.add_argument("--min-core-events-avg", type=float, default=2.0)
    parser.add_argument("--min-tactical-stops-avg", type=float, default=8.0)
    parser.add_argument("--min-orders-avg", type=float, default=3.0)
    parser.add_argument("--fast-min-seconds", type=float, default=5.0)
    parser.add_argument("--fast-max-seconds", type=float, default=45.0)
    parser.add_argument("--human-min-seconds", type=float, default=300.0)
    parser.add_argument("--human-max-seconds", type=float, default=480.0)
    return parser


def main() -> int:
    parser = _build_parser()
    args = parser.parse_args()

    index_path = args.index.resolve()
    if not index_path.exists():
        print(f"[guardrail] ERROR: no existe index: {index_path}")
        return 1

    try:
        payload = json.loads(index_path.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"[guardrail] ERROR: no se pudo leer JSON {index_path}: {exc}")
        return 1
    if not isinstance(payload, dict):
        print(f"[guardrail] ERROR: formato invalido en {index_path} (se esperaba objeto JSON)")
        return 1

    errors, summary = _validate(args, payload)
    if errors:
        print(f"[guardrail] FAIL ({len(errors)} errores)")
        for item in errors:
            print(f" - {item}")
        print("[guardrail] resumen parcial:")
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        return 1

    print("[guardrail] PASS")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

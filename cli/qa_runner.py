#!/usr/bin/env python3
"""Automated QA runner for ProManager CLI season simulation."""

from __future__ import annotations

import json
import subprocess
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CAREER_SAVE = Path.home() / ".pcfutbol_career.json"
OUTPUT_DIR = ROOT / "cli" / "qa_outputs"

INPUT_LINES = [
    "6",         # main menu -> ProManager
    "CODEX_QA",  # new manager name
    "1",         # first offer
    "5",         # simulate rest of season
    "0",         # back at main menu -> exit
]
INPUTS = "\n".join(INPUT_LINES) + "\n"

RUN_TARGETS = [
    {
        "label": "cli_root",
        "script": ROOT / "cli" / "pcfutbol_cli.py",
        "cwd": ROOT / "cli",
    },
]


def _safe_decode(raw: bytes) -> str:
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        return raw.decode("utf-8", errors="replace")


def _write_run_logs(
    label: str,
    returncode: int,
    stdout_text: str,
    stderr_text: str,
) -> dict[str, str]:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    prefix = OUTPUT_DIR / f"{stamp}_{label}"

    stdout_path = f"{prefix}.stdout.log"
    stderr_path = f"{prefix}.stderr.log"
    meta_path = f"{prefix}.meta.json"

    Path(stdout_path).write_text(stdout_text, encoding="utf-8")
    Path(stderr_path).write_text(stderr_text, encoding="utf-8")
    Path(meta_path).write_text(
        json.dumps(
            {
                "label": label,
                "returncode": returncode,
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    return {
        "stdout": stdout_path,
        "stderr": stderr_path,
        "meta": meta_path,
    }


def _run_target(target: dict[str, Path | str]) -> tuple[subprocess.CompletedProcess[bytes], dict[str, str], str, str]:
    script = Path(target["script"])
    cwd = Path(target["cwd"])
    cmd = [sys.executable, str(script)]

    result = subprocess.run(
        cmd,
        input=INPUTS.encode("utf-8"),
        capture_output=True,
        timeout=240,
        cwd=str(cwd),
    )

    stdout_text = _safe_decode(result.stdout or b"")
    stderr_text = _safe_decode(result.stderr or b"")
    paths = _write_run_logs(str(target["label"]), result.returncode, stdout_text, stderr_text)
    return result, paths, stdout_text, stderr_text


def _looks_like_asset_path_error(text: str) -> bool:
    return "No se encuentra el CSV" in text or "FileNotFoundError" in text


def _season_completed(text: str) -> bool:
    checks = [
        ("MODO PRO MANAGER",),
        ("SELECCIÓN DE OFERTA", "SELECCIÃ“N DE OFERTA"),
        ("Simular resto de temporada",),
        ("FIN DE TEMPORADA",),
        ("Prestigio:",),
    ]
    return all(any(option in text for option in variants) for variants in checks)


def main() -> int:
    print(f"[qa] save path: {CAREER_SAVE}")

    selected = None
    failures: list[dict[str, str | int | bool]] = []
    for target in RUN_TARGETS:
        label = str(target["label"])
        if CAREER_SAVE.exists():
            CAREER_SAVE.unlink()
            print(f"[qa] {label}: deleted save file for isolated run")
        else:
            print(f"[qa] {label}: save file not found (clean start)")
        print(f"[qa] running target: {label}")
        result, paths, stdout_text, stderr_text = _run_target(target)

        merged = stdout_text + "\n" + stderr_text
        season_completed = _season_completed(stdout_text)
        print(
            f"[qa] {label}: returncode={result.returncode} "
            f"stdout={paths['stdout']} stderr={paths['stderr']}"
        )

        if _looks_like_asset_path_error(merged):
            print(f"[qa] {label}: asset path issue detected, trying next target.")
            failures.append(
                {
                    "label": label,
                    "reason": "asset_path_error",
                    "returncode": result.returncode,
                    "season_completed": season_completed,
                    "stdout": paths["stdout"],
                    "stderr": paths["stderr"],
                }
            )
            continue

        if result.returncode != 0 or not season_completed:
            print(
                f"[qa] {label}: run rejected "
                f"(returncode={result.returncode}, season_completed={season_completed})"
            )
            failures.append(
                {
                    "label": label,
                    "reason": "qa_checks_failed",
                    "returncode": result.returncode,
                    "season_completed": season_completed,
                    "stdout": paths["stdout"],
                    "stderr": paths["stderr"],
                }
            )
            continue

        selected = {
            "label": label,
            "result": result,
            "stdout_text": stdout_text,
            "paths": paths,
            "season_completed": season_completed,
        }
        break

    if selected is None:
        print("[qa] no target passed qa checks.")
        if failures:
            print("[qa] failure summary:")
            for fail in failures:
                print(
                    "[qa] "
                    f"{fail['label']}: reason={fail['reason']} "
                    f"returncode={fail['returncode']} "
                    f"season_completed={fail['season_completed']} "
                    f"stdout={fail['stdout']} stderr={fail['stderr']}"
                )
        return 1

    print(f"[qa] selected target: {selected['label']}")
    print(f"[qa] season_completed={selected['season_completed']}")
    print(f"[qa] exit_code={selected['result'].returncode}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

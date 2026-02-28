#!/usr/bin/env python
"""Patch competition labels: Segunda B -> Primera RFEF with 2 active groups.

By default this patches only ``DBASEDOS.DAT``. ``MANDOS.DAT`` can also be
patched, but it is opt-in because aggressive text rewrites in that file can
break runtime compatibility on some DOSBox builds (notably Android).
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import unicodedata
from pathlib import Path
from typing import Dict, List, Tuple


DEFAULT_MANDOS = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "MANDOS.DAT"
DEFAULT_DBASEDOS = (
    Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBASEDOS.DAT"
)
DEFAULT_REPORT = Path(__file__).resolve().parent / "out" / "competition_patch_report.json"

# Short code keys can map to internal ids/tables in LE binaries. Replacing
# them can break runtime behavior (offers generation, competition lookups) and
# in some builds even crash startup.
UNSAFE_COMPETITION_CODE_KEYS = {
    "2OB",
    "2AB",
    "2O DIVISION B",
    "2A DIVISION B",
    "2OB I",
    "2AB I",
    "2OB II",
    "2AB II",
    "2OB III",
    "2AB III",
    "2OB IV",
    "2AB IV",
    "2OB-I",
    "2AB-I",
    "2OB-II",
    "2AB-II",
    "2OB-III",
    "2AB-III",
    "2OB-IV",
    "2AB-IV",
    "2OB-GRUPO I",
    "2AB-GRUPO I",
    "2OB-GRUPO II",
    "2AB-GRUPO II",
    "2OB-GRUPO III",
    "2AB-GRUPO III",
    "2OB-GRUPO IV",
    "2AB-GRUPO IV",
    "LIGA2B",
}

# Canonicalized token -> replacement text.
# Replacements are fitted in-place to each token fixed size.
REPLACEMENTS: Dict[str, str] = {
    "SEGUNDA B - GRUPO I": "PRIMERA RFEF - G1",
    "SEGUNDA B - GRUPO II": "PRIMERA RFEF - G2",
    "SEGUNDA B - GRUPO III": "RFEF GRUPO 3 OFF",
    "SEGUNDA B - GRUPO IV": "RFEF GRUPO 4 OFF",
    "SEGUNDA DIVISION B - GRUPO I": "PRIMERA RFEF - GRP 1",
    "SEGUNDA DIVISION B - GRUPO II": "PRIMERA RFEF - GRP 2",
    "SEGUNDA DIVISION B - GRUPO III": "RFEF GRUPO 3 OFF",
    "SEGUNDA DIVISION B - GRUPO IV": "RFEF GRUPO 4 OFF",
    "SEGUNDA DIVISION B": "PRIMERA RFEF",
    "2O DIVISION B": "1A RFEF",
    "2A DIVISION B": "1A RFEF",
    "2O DIVISION B, EUROPA Y SUDAMERICA": "1A RFEF, EUROPA Y SUDAMERICA",
    "2A DIVISION B, EUROPA Y SUDAMERICA": "1A RFEF, EUROPA Y SUDAMERICA",
    "2O DIVISION B - LIGUILLA DE PROMOCION": "PRIMERA RFEF - PLAYOFF ASCENSO",
    "2A DIVISION B - LIGUILLA DE PROMOCION": "PRIMERA RFEF - PLAYOFF ASCENSO",
    "LIGA - 2O DIVISION B": "LIGA - 1A RFEF",
    "LIGA - 2A DIVISION B": "LIGA - 1A RFEF",
    "LIGA 2O DIVISION B:": "LIGA 1A RFEF:",
    "LIGA 2A DIVISION B:": "LIGA 1A RFEF:",
    "CAMPEON DE 2O DIVISION B": "CAMPEON DE 1A RFEF",
    "CAMPEON DE 2A DIVISION B": "CAMPEON DE 1A RFEF",
    "PROMOCION 2OB": "PROMO RFEF",
    "PROMOCION 2AB": "PROMO RFEF",
    "2OB": "1R",
    "2AB": "1R",
    "2OB I": "RFEF1",
    "2AB I": "RFEF1",
    "2OB II": "RFEF2",
    "2AB II": "RFEF2",
    "2OB III": "OFFG3",
    "2AB III": "OFFG3",
    "2OB IV": "OFFG4",
    "2AB IV": "OFFG4",
    "2OB-I": "RFEF1",
    "2AB-I": "RFEF1",
    "2OB-II": "RFEF2",
    "2AB-II": "RFEF2",
    "2OB-III": "OFFG3",
    "2AB-III": "OFFG3",
    "2OB-IV": "OFFG4",
    "2AB-IV": "OFFG4",
    "2OB-GRUPO I": "RFEF-GRP1",
    "2AB-GRUPO I": "RFEF-GRP1",
    "2OB-GRUPO II": "RFEF-GRP2",
    "2AB-GRUPO II": "RFEF-GRP2",
    "2OB-GRUPO III": "OFF-GRP-3",
    "2AB-GRUPO III": "OFF-GRP-3",
    "2OB-GRUPO IV": "OFF-GRP-4",
    "2AB-GRUPO IV": "OFF-GRP-4",
    "LIGA2B": "LIGRFE",
}


def canonicalize(text: str) -> str:
    s = (text or "").strip().upper()
    s = s.replace("º", "O").replace("ª", "A")
    s = "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))
    s = "".join(c if (c.isalnum() or c in " -:") else " " for c in s)
    s = " ".join(s.split())
    # Tolerate broken ordinal rendering in legacy resources.
    s = re.sub(r"\b2\s*B\b", "2OB", s)
    s = re.sub(r"\b2\s*DIVISION\b", "2O DIVISION", s)
    return s


def fit_fixed_cp1252(text: str, max_len: int) -> Tuple[bytes, int]:
    raw = (text or "").encode("cp1252", errors="replace")
    truncated = 0
    if len(raw) > max_len:
        raw = raw[:max_len]
        truncated = 1
    if len(raw) < max_len:
        raw = raw + (b" " * (max_len - len(raw)))
    return raw, truncated


def patch_file(path: Path, replacements: Dict[str, str]) -> Dict[str, object]:
    original = path.read_bytes()
    tokens = original.split(b"\x00")
    out_tokens: List[bytes] = []
    patched = 0
    truncated = 0
    by_key: Dict[str, int] = {}

    for tok in tokens:
        if not tok:
            out_tokens.append(tok)
            continue
        try:
            txt = tok.decode("cp1252", errors="replace")
        except Exception:
            out_tokens.append(tok)
            continue
        key = canonicalize(txt)
        repl = replacements.get(key)
        if repl is None:
            out_tokens.append(tok)
            continue

        new_tok, trunc = fit_fixed_cp1252(repl, len(tok))
        out_tokens.append(new_tok)
        patched += 1
        truncated += trunc
        by_key[key] = by_key.get(key, 0) + 1

    patched_bytes = b"\x00".join(out_tokens)
    changed = patched_bytes != original
    return {
        "file": str(path),
        "changed": changed,
        "tokens_patched": patched,
        "tokens_truncated": truncated,
        "by_key": dict(sorted(by_key.items())),
        "patched_bytes": patched_bytes,
        "original_bytes": original,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--files", nargs="*", type=Path, default=None)
    parser.add_argument(
        "--include-mandos",
        action="store_true",
        help="Also patch MANDOS.DAT (opt-in due compatibility risk on mobile DOSBox).",
    )
    parser.add_argument(
        "--only-keys",
        nargs="*",
        default=None,
        help="Patch only these canonical keys (exact key text, case-insensitive).",
    )
    parser.add_argument(
        "--exclude-keys",
        nargs="*",
        default=None,
        help="Exclude these canonical keys from patching.",
    )
    parser.add_argument(
        "--allow-unsafe-mandos-codes",
        action="store_true",
        help="Allow unsafe internal competition-key replacements in binary files (can break runtime).",
    )
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    return parser.parse_args()


def build_replacements(only_keys: List[str] | None, exclude_keys: List[str] | None) -> Dict[str, str]:
    selected = dict(REPLACEMENTS)
    if only_keys:
        wanted = {canonicalize(k) for k in only_keys if (k or "").strip()}
        selected = {k: v for k, v in selected.items() if k in wanted}
    if exclude_keys:
        blocked = {canonicalize(k) for k in exclude_keys if (k or "").strip()}
        selected = {k: v for k, v in selected.items() if k not in blocked}
    return selected


def main() -> None:
    args = parse_args()
    base_replacements = build_replacements(args.only_keys, args.exclude_keys)
    files = list(args.files or [])
    if not files:
        files = [DEFAULT_DBASEDOS]
        if args.include_mandos:
            files.insert(0, DEFAULT_MANDOS)

    results: List[Dict[str, object]] = []
    backups: List[str] = []
    writes = 0

    for path in files:
        if not path.exists():
            results.append({"file": str(path), "status": "missing"})
            continue
        effective_replacements = dict(base_replacements)
        unsafe_blocked: List[str] = []
        if not args.allow_unsafe_mandos_codes:
            unsafe_blocked = sorted(k for k in effective_replacements if k in UNSAFE_COMPETITION_CODE_KEYS)
            for k in unsafe_blocked:
                effective_replacements.pop(k, None)

        res = patch_file(path, effective_replacements)
        changed = bool(res["changed"])
        status = "unchanged"
        if changed and not args.dry_run:
            stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
            backup = path.with_suffix(path.suffix + f".{stamp}.bak")
            backup.write_bytes(res["original_bytes"])
            path.write_bytes(res["patched_bytes"])
            backups.append(str(backup))
            writes += 1
            status = "patched"
        elif changed:
            status = "would_patch"

        results.append(
            {
                "file": str(path),
                "status": status,
                "changed": changed,
                "tokens_patched": int(res["tokens_patched"]),
                "tokens_truncated": int(res["tokens_truncated"]),
                "by_key": res["by_key"],
                "effective_replacement_count": len(effective_replacements),
                "unsafe_mandos_keys_blocked": unsafe_blocked,
            }
        )

    report = {
        "dry_run": bool(args.dry_run),
        "writes": writes,
        "replacement_count": len(base_replacements),
        "replacement_keys": sorted(base_replacements.keys()),
        "allow_unsafe_mandos_codes": bool(args.allow_unsafe_mandos_codes),
        "backups": backups,
        "results": results,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Writes: {writes}")
    print(f"Report: {args.report}")
    for row in results:
        print(
            f"- {row['status']}: {row['file']} "
            f"(patched={row.get('tokens_patched', 0)}, trunc={row.get('tokens_truncated', 0)})"
        )


if __name__ == "__main__":
    main()

#!/usr/bin/env python
"""Extract rewrite-facing contracts from reverse inventory + binary tokens."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Dict, List


DEFAULT_INVENTORY = Path(__file__).resolve().parent / "out" / "reverse_full_game.json"
DEFAULT_MANDOS = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "MANDOS.DAT"
DEFAULT_DBASEDOS = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBASEDOS.DAT"
DEFAULT_OUT = Path(__file__).resolve().parent / "out" / "rewrite_contract.json"

PROMANAGER_PATTERNS = [
    r"TACTICS\PROMANAG",
    r"TACTICS\MANAGER",
    r"SELECCION DE OFERTAS",
    r"OFERTAS PARA ",
    r"MANAGERS",
    r"CLAVE DE ACCESO",
    r"Cargar Liga",
]

COMP_KEY_PATTERNS = [r"LIGA1", r"LIGA2", r"LIGA2B", r"RECOPA", r"SCESP", r"SCEUR"]

SIM_TOKENS = [
    "PCF_SIMULATOR_COMFILE",
    "@velocidad",
    "@resistencia",
    "@agresividad",
    "@calidad",
    "@estadoforma",
    "@moral",
    "@media",
    "@portero",
    "@entrada",
    "@regate",
    "@remate",
    "@pase",
    "@tiro",
    "@rol",
    "@demarcacion",
    "@tipojuego",
    "@tipomarcaje",
    "@tipodespejes",
    "@tipopresion",
    "@faltas",
    "@porctoque",
    "@porccontra",
    "@marcajedefensas",
    "@marcajemedios",
    "@puntodefensa",
    "@puntoataque",
    "@area",
]


def find_file_entry(inventory: Dict[str, object], path_name: str) -> Dict[str, object]:
    for f in inventory.get("files", []):
        if str(f.get("path", "")).lower() == path_name.lower():
            return f
    return {}


def extract_strings(path: Path) -> List[str]:
    txt = path.read_bytes().decode("cp1252", errors="replace")
    out = [s.strip() for s in txt.split("\x00")]
    return [s for s in out if s]


def count_tokens(strings: List[str], tokens: List[str]) -> Dict[str, int]:
    joined = "\x00".join(strings)
    out: Dict[str, int] = {}
    for t in tokens:
        c = len(re.findall(re.escape(t), joined))
        if c > 0:
            out[t] = c
    return out


def filter_paths(strings: List[str]) -> Dict[str, List[str]]:
    def clean(s: str) -> str:
        s = s.strip()
        # Keep ASCII path-like tokens only; binary-adjacent artifacts become unreadable.
        if not s:
            return ""
        if any(ord(c) < 32 or ord(c) > 126 for c in s):
            return ""
        return s

    actliga: List[str] = []
    tactics: List[str] = []
    for raw in strings:
        s = clean(raw)
        if not s:
            continue
        up = s.upper()
        if up.startswith("%C:ACTLIGA\\"):
            actliga.append(s)
        if up.startswith("%C:TACTICS\\") or up in {"TACTICS\\MANAGER", "TACTICS\\PROMANAG"}:
            tactics.append(s)
    actliga = sorted(set(actliga))
    tactics = sorted(set(tactics))
    return {"actliga_templates": actliga, "tactics_templates": tactics}


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--inventory", type=Path, default=DEFAULT_INVENTORY)
    p.add_argument("--mandos", type=Path, default=DEFAULT_MANDOS)
    p.add_argument("--dbasedos", type=Path, default=DEFAULT_DBASEDOS)
    p.add_argument("--out", type=Path, default=DEFAULT_OUT)
    return p.parse_args()


def main() -> None:
    args = parse_args()
    inventory = json.loads(args.inventory.read_text(encoding="utf-8"))

    mandos_entry = find_file_entry(inventory, "MANDOS.DAT")
    db_entry = find_file_entry(inventory, "DBASEDOS.DAT")

    mandos_strings = extract_strings(args.mandos)
    db_strings = extract_strings(args.dbasedos)

    report = {
        "sources": {
            "inventory": str(args.inventory),
            "mandos": str(args.mandos),
            "dbasedos": str(args.dbasedos),
        },
        "modules": {
            "mandos": {
                "size": mandos_entry.get("size"),
                "sha256": mandos_entry.get("sha256"),
                "binary_type": mandos_entry.get("binary_type"),
                "le": mandos_entry.get("le"),
            },
            "dbasedos": {
                "size": db_entry.get("size"),
                "sha256": db_entry.get("sha256"),
                "binary_type": db_entry.get("binary_type"),
                "le": db_entry.get("le"),
            },
        },
        "promanager_offsets": {
            p: (mandos_entry.get("pattern_hits", {}).get(p, []) if mandos_entry else [])
            for p in PROMANAGER_PATTERNS
        },
        "competition_key_offsets": {
            p: (mandos_entry.get("pattern_hits", {}).get(p, []) if mandos_entry else [])
            for p in COMP_KEY_PATTERNS
        },
        "persistence_paths": filter_paths(mandos_strings),
        "simulator_contract_tokens": count_tokens(mandos_strings, SIM_TOKENS),
        "dbasedos_actliga_paths": sorted(set(s for s in db_strings if "ACTLIGA" in s.upper())),
    }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Inventory: {args.inventory}")
    print(f"Contract : {args.out}")
    print(
        "ProManager anchors: "
        + ", ".join(f"{k}={len(v)}" for k, v in report["promanager_offsets"].items())
    )
    print(
        "Persistence templates: "
        f"ACTLIGA={len(report['persistence_paths']['actliga_templates'])}, "
        f"TACTICS={len(report['persistence_paths']['tactics_templates'])}"
    )
    print(f"Simulator tokens: {len(report['simulator_contract_tokens'])}")


if __name__ == "__main__":
    main()

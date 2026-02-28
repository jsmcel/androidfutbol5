#!/usr/bin/env python
"""Find LE (DOS/4GW) xref candidates to string anchors by VA literal scan."""

from __future__ import annotations

import argparse
import json
import struct
from pathlib import Path
from typing import Dict, List, Optional

from reverse_le import LeInfo, parse_le


DEFAULT_FILE = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "MANDOS.DAT"
DEFAULT_OUT = Path(__file__).resolve().parent / "out" / "re_le_xrefs_mandos.json"


def file_off_to_va(info: LeInfo, off: int) -> Optional[int]:
    if off < info.data_pages_offset or info.page_size <= 0:
        return None
    rel = off - info.data_pages_offset
    page = (rel // info.page_size) + 1
    in_page = rel % info.page_size
    for obj in info.objects:
        if obj.page_first <= page <= obj.page_last:
            page_in_obj = page - obj.page_first
            return int(obj.relocation_base + (page_in_obj * info.page_size) + in_page)
    return None


def find_ascii(blob: bytes, needle: bytes) -> List[int]:
    out: List[int] = []
    i = blob.find(needle)
    while i != -1:
        out.append(i)
        i = blob.find(needle, i + 1)
    return out


def find_dword(blob: bytes, value: int) -> List[int]:
    key = struct.pack("<I", value & 0xFFFFFFFF)
    out: List[int] = []
    i = blob.find(key)
    while i != -1:
        out.append(i)
        i = blob.find(key, i + 1)
    return out


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--file", type=Path, default=DEFAULT_FILE, help="LE binary to analyze.")
    p.add_argument(
        "--patterns",
        nargs="*",
        default=[
            "SELECCION DE OFERTAS",
            "OFERTAS PARA",
            "MANAGERS",
            "CLAVE DE ACCESO",
            "TACTICS\\PROMANAG",
            "TACTICS\\MANAGER",
        ],
        help="ASCII patterns used as anchor strings.",
    )
    p.add_argument("--max-xrefs", type=int, default=200, help="Cap xref offsets per anchor.")
    p.add_argument("--out", type=Path, default=DEFAULT_OUT, help="JSON output path.")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    blob = args.file.read_bytes()
    info = parse_le(args.file)

    anchors: Dict[str, List[Dict[str, int]]] = {}
    xrefs: Dict[str, List[Dict[str, int]]] = {}

    for pat in args.patterns:
        pat_b = pat.encode("cp1252", errors="replace")
        offs = find_ascii(blob, pat_b)
        rows: List[Dict[str, int]] = []
        for off in offs:
            va = file_off_to_va(info, off)
            if va is None:
                continue
            rows.append({"file_offset": off, "va": va})
        anchors[pat] = rows

        xr_rows: List[Dict[str, int]] = []
        seen = set()
        for r in rows:
            va = int(r["va"])
            ref_offs = find_dword(blob, va)
            for ref in ref_offs:
                if ref == int(r["file_offset"]):
                    continue
                if ref in seen:
                    continue
                seen.add(ref)
                xr_rows.append(
                    {
                        "file_offset": ref,
                        "va_if_mapped": file_off_to_va(info, ref) or 0,
                        "target_va": va,
                    }
                )
                if len(xr_rows) >= args.max_xrefs:
                    break
            if len(xr_rows) >= args.max_xrefs:
                break
        xrefs[pat] = xr_rows

    report = {
        "file": str(args.file),
        "le_offset": info.le_offset,
        "page_size": info.page_size,
        "data_pages_offset": info.data_pages_offset,
        "anchors": anchors,
        "xrefs": xrefs,
    }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Report: {args.out}")
    for pat in args.patterns:
        print(
            f"{pat}: anchors={len(anchors.get(pat, []))} "
            f"xref_candidates={len(xrefs.get(pat, []))}"
        )


if __name__ == "__main__":
    main()


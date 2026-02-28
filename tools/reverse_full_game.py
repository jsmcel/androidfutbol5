#!/usr/bin/env python
"""Comprehensive reverse-engineering inventory for PCF55 game files.

This script scans the full game tree and builds:
- file inventory with hashes and binary type hints
- LE metadata for MZ/LE modules
- PKF pointer-table stats
- DBC id distributions (1..260)
- key symbol/pattern hit map (manager/pro-manager/competitions)
- markdown summary for fast human review
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import struct
from pathlib import Path
from typing import Dict, List, Optional

from reverse_le import parse_le


DEFAULT_ROOT = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5"
DEFAULT_OUT_JSON = Path(__file__).resolve().parent / "out" / "reverse_full_game.json"
DEFAULT_OUT_MD = Path(__file__).resolve().parent / "out" / "reverse_full_game.md"

PATTERNS = [
    r"TACTICS\PROMANAG",
    r"TACTICS\MANAGER",
    r"ACTLIGA",
    r"SELECCION DE OFERTAS",
    r"OFERTAS PARA ",
    r"MANAGERS",
    r"CLAVE DE ACCESO",
    r"Cargar Liga",
    r"LIGA1",
    r"LIGA2",
    r"LIGA2B",
    r"2OB",
    r"2AB",
    r"RECOPA",
    r"SCESP",
    r"SCEUR",
    r"PCF_SIMULATOR_COMFILE",
    r"@velocidad",
    r"@resistencia",
    r"@agresividad",
    r"@calidad",
    r"@media",
    r"@rol",
]

INCLUDE_EXTS = {
    ".dat",
    ".dbc",
    ".pkf",
    ".exe",
    ".dll",
    ".htm",
    ".html",
    ".ini",
    ".arg",
    ".wcf",
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def classify_binary(blob: bytes) -> str:
    if blob.startswith(b"MZ"):
        if len(blob) >= 0x40:
            e_lfanew = struct.unpack_from("<I", blob, 0x3C)[0]
            if 0 < e_lfanew + 2 <= len(blob):
                sig = blob[e_lfanew : e_lfanew + 2]
                if sig == b"LE":
                    return "mz-le"
                if sig == b"PE":
                    return "mz-pe"
        return "mz"
    if blob.startswith(b"PK"):
        return "zip-like"
    return "raw"


def find_offsets(blob: bytes, pattern: str) -> List[int]:
    p = pattern.encode("cp1252", errors="replace")
    out: List[int] = []
    pos = blob.find(p)
    while pos != -1:
        out.append(pos)
        pos = blob.find(p, pos + 1)
    return out


def extract_context(blob: bytes, off: int, span: int = 64) -> str:
    s = max(0, off - span)
    e = min(len(blob), off + span)
    raw = blob[s:e]
    txt = raw.decode("cp1252", errors="replace")
    txt = txt.replace("\x00", " ")
    txt = re.sub(r"\s+", " ", txt).strip()
    return txt


def parse_pkf_pointer_count(blob: bytes) -> Dict[str, int]:
    # Mirrors legacy pointer table parser behavior: starts around offset 232.
    i = 232
    offset = 232
    count = 0
    jumps = 0
    while i < len(blob):
        b = blob[i]
        if b == 0x04:
            if i + 5 > len(blob):
                break
            jump_to = struct.unpack_from("<I", blob, i + 1)[0]
            i += 5
            skip = jump_to - offset - 5
            if skip < 0:
                break
            i += skip
            offset = jump_to
            jumps += 1
            continue
        if b == 0x02:
            if i + 38 > len(blob):
                break
            i += 38
            count += 1
            offset += 38
            continue
        if b == 0x05 and blob[i + 1 : i + 5] == b"\x00\x00\x00\x00":
            break
        i += 1
    return {"pointer_count": count, "jump_blocks": jumps}


def parse_dbc_stats(blob: bytes) -> Dict[str, object]:
    n = len(blob) // 2
    vals = struct.unpack("<" + ("H" * n), blob[: n * 2]) if n > 0 else ()
    valid = [int(v) for v in vals if 1 <= v <= 260]
    unique = sorted(set(valid))
    return {
        "word_count": n,
        "valid_id_count": len(valid),
        "valid_unique_count": len(unique),
        "valid_min": min(unique) if unique else None,
        "valid_max": max(unique) if unique else None,
        "valid_ids_head": unique[:120],
    }


def parse_html_headings(text: str) -> List[str]:
    out: List[str] = []
    for m in re.finditer(r"<H([1-6])[^>]*>(.*?)</H\1>", text, flags=re.IGNORECASE | re.DOTALL):
        raw = m.group(2)
        stripped = re.sub(r"<[^>]+>", " ", raw)
        stripped = html.unescape(re.sub(r"\s+", " ", stripped)).strip()
        if stripped:
            out.append(stripped)
    return out


def scan_file(path: Path, root: Path, patterns: List[str]) -> Dict[str, object]:
    blob = path.read_bytes()
    rel = str(path.relative_to(root)).replace("\\", "/")
    ext = path.suffix.lower()
    entry: Dict[str, object] = {
        "path": rel,
        "size": len(blob),
        "sha256": sha256(path),
        "ext": ext,
        "binary_type": classify_binary(blob),
        "pattern_hits": {},
    }

    pat_hits: Dict[str, List[int]] = {}
    for pat in patterns:
        offs = find_offsets(blob, pat)
        if offs:
            pat_hits[pat] = offs
    entry["pattern_hits"] = pat_hits

    if entry["binary_type"] == "mz-le":
        try:
            info = parse_le(path)
            entry["le"] = {
                "le_offset": info.le_offset,
                "module_pages": info.module_pages,
                "page_size": info.page_size,
                "module_flags": info.module_flags,
                "object_count": info.object_count,
                "objects": [
                    {
                        "index": o.index,
                        "virtual_size": o.virtual_size,
                        "relocation_base": o.relocation_base,
                        "flags": o.flags,
                        "page_table_index": o.page_table_index,
                        "page_count": o.page_count,
                    }
                    for o in info.objects
                ],
                "data_pages_offset": info.data_pages_offset,
                "fixup_page_table_offset": info.fixup_page_table_offset,
                "fixup_record_table_offset": info.fixup_record_table_offset,
            }
        except Exception as exc:  # noqa: BLE001
            entry["le_error"] = str(exc)

    if ext == ".pkf":
        entry["pkf"] = parse_pkf_pointer_count(blob)

    if ext == ".dbc":
        entry["dbc"] = parse_dbc_stats(blob)

    if ext in {".htm", ".html"}:
        txt = blob.decode("cp1252", errors="replace")
        entry["html"] = {
            "headings": parse_html_headings(txt)[:200],
            "contains_promanager": ("promanager" in txt.lower()),
            "contains_manager": ("manager" in txt.lower()),
            "contains_ofertas": ("ofertas" in txt.lower()),
            "contains_objetivo": ("objetivo" in txt.lower()),
        }

    # Keep compact context snippets for relevant critical patterns.
    contexts: List[Dict[str, object]] = []
    for pat in [
        r"SELECCION DE OFERTAS",
        r"OFERTAS PARA ",
        r"TACTICS\PROMANAG",
        r"TACTICS\MANAGER",
        r"LIGA2B",
        r"ACTLIGA",
    ]:
        for off in pat_hits.get(pat, [])[:4]:
            contexts.append({"pattern": pat, "offset": off, "context": extract_context(blob, off)})
    if contexts:
        entry["contexts"] = contexts

    return entry


def build_summary(files: List[Dict[str, object]]) -> Dict[str, object]:
    type_counts: Dict[str, int] = {}
    ext_counts: Dict[str, int] = {}
    hits: Dict[str, List[Dict[str, object]]] = {p: [] for p in PATTERNS}

    for f in files:
        btype = str(f.get("binary_type"))
        type_counts[btype] = type_counts.get(btype, 0) + 1
        ext = str(f.get("ext"))
        ext_counts[ext] = ext_counts.get(ext, 0) + 1
        pmap = f.get("pattern_hits", {})
        if isinstance(pmap, dict):
            for pat, offs in pmap.items():
                if pat not in hits:
                    hits[pat] = []
                hits[pat].append(
                    {
                        "path": f["path"],
                        "count": len(offs) if isinstance(offs, list) else 0,
                        "first_offsets": offs[:8] if isinstance(offs, list) else [],
                    }
                )

    # Sort hit lists by count desc.
    for pat in list(hits):
        hits[pat] = sorted(hits[pat], key=lambda r: r["count"], reverse=True)
        if not hits[pat]:
            hits.pop(pat)

    return {
        "file_count": len(files),
        "binary_type_counts": dict(sorted(type_counts.items())),
        "extension_counts": dict(sorted(ext_counts.items())),
        "pattern_hit_index": hits,
    }


def render_markdown(report: Dict[str, object]) -> str:
    lines: List[str] = []
    lines.append("# PCF55 Reverse Engineering Inventory")
    lines.append("")
    lines.append("## Summary")
    lines.append("")
    lines.append(f"- Files scanned: **{report['summary']['file_count']}**")
    lines.append("- Binary types:")
    for k, v in report["summary"]["binary_type_counts"].items():
        lines.append(f"  - `{k}`: {v}")
    lines.append("- Extensions:")
    for k, v in report["summary"]["extension_counts"].items():
        lines.append(f"  - `{k}`: {v}")
    lines.append("")

    lines.append("## Key Pattern Hits")
    lines.append("")
    ph = report["summary"]["pattern_hit_index"]
    for pat in sorted(ph.keys()):
        rows = ph[pat][:12]
        lines.append(f"### `{pat}`")
        for r in rows:
            lines.append(f"- `{r['path']}`: count={r['count']} first={r['first_offsets']}")
        lines.append("")

    lines.append("## LE Modules")
    lines.append("")
    for f in report["files"]:
        if f.get("binary_type") != "mz-le":
            continue
        le = f.get("le")
        lines.append(f"### `{f['path']}`")
        if not isinstance(le, dict):
            lines.append(f"- parse error: {f.get('le_error', 'unknown')}")
            continue
        lines.append(
            f"- le_offset={le['le_offset']} page_size={le['page_size']} "
            f"pages={le['module_pages']} objects={le['object_count']}"
        )
        for o in le["objects"]:
            lines.append(
                f"  - obj{o['index']}: base=0x{o['relocation_base']:08X} "
                f"vsize={o['virtual_size']} flags=0x{o['flags']:08X} "
                f"page_idx={o['page_table_index']} page_count={o['page_count']}"
            )
        if f.get("contexts"):
            lines.append("- contexts:")
            for c in f["contexts"][:10]:
                lines.append(f"  - {c['pattern']} @{c['offset']}: {c['context']}")
        lines.append("")

    lines.append("## DBC Stats")
    lines.append("")
    for f in report["files"]:
        dbc = f.get("dbc")
        if not isinstance(dbc, dict):
            continue
        lines.append(
            f"- `{f['path']}`: words={dbc['word_count']} valid={dbc['valid_id_count']} "
            f"unique={dbc['valid_unique_count']} range={dbc['valid_min']}..{dbc['valid_max']}"
        )
    lines.append("")

    lines.append("## PKF Pointer Tables")
    lines.append("")
    for f in report["files"]:
        pkf = f.get("pkf")
        if not isinstance(pkf, dict):
            continue
        lines.append(
            f"- `{f['path']}`: pointers={pkf['pointer_count']} jump_blocks={pkf['jump_blocks']}"
        )
    lines.append("")

    lines.append("## INFOFUT Headings")
    lines.append("")
    for f in report["files"]:
        h = f.get("html")
        if not isinstance(h, dict):
            continue
        if "infofut/" not in str(f["path"]).lower():
            continue
        heads = h.get("headings", [])
        if not heads:
            continue
        lines.append(f"### `{f['path']}`")
        for title in heads[:20]:
            lines.append(f"- {title}")
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    p.add_argument("--out-json", type=Path, default=DEFAULT_OUT_JSON)
    p.add_argument("--out-md", type=Path, default=DEFAULT_OUT_MD)
    p.add_argument(
        "--include-exts",
        nargs="*",
        default=sorted(INCLUDE_EXTS),
        help="Extensions to scan (e.g. .dat .dbc .pkf .exe .htm)",
    )
    p.add_argument("--max-files", type=int, default=0, help="Optional cap for faster dry runs (0=no cap)")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    include_exts = {e.lower() if e.startswith(".") else f".{e.lower()}" for e in args.include_exts}

    all_files = sorted(
        [
            p
            for p in args.root.rglob("*")
            if p.is_file() and p.suffix.lower() in include_exts
        ]
    )
    if args.max_files > 0:
        all_files = all_files[: args.max_files]

    rows: List[Dict[str, object]] = []
    for p in all_files:
        rows.append(scan_file(p, args.root, PATTERNS))

    report = {
        "root": str(args.root),
        "patterns": PATTERNS,
        "summary": build_summary(rows),
        "files": rows,
    }

    args.out_json.parent.mkdir(parents=True, exist_ok=True)
    args.out_md.parent.mkdir(parents=True, exist_ok=True)
    args.out_json.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    args.out_md.write_text(render_markdown(report), encoding="utf-8")

    print(f"Root: {args.root}")
    print(f"Files scanned: {len(rows)}")
    print(f"JSON: {args.out_json}")
    print(f"MD  : {args.out_md}")


if __name__ == "__main__":
    main()


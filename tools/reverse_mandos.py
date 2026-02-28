#!/usr/bin/env python
"""Reverse-engineering helpers for PCF55 MANDOS.DAT."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List


DEFAULT_MANDOS = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "MANDOS.DAT"
DEFAULT_OUT = Path(__file__).resolve().parent / "out" / "re_mandos_report.json"


FLOW_MARKERS = [
    "TACTICS\\PROMANAG",
    "TACTICS\\MANAGER",
    "SELECCION DE OFERTAS",
    "MANAGERS",
    "CLAVE DE ACCESO",
    "SELECCIONA MANAGER",
    "OFERTAS PARA ",
]

COMP_KEYS = ["LIGA1", "LIGA2", "LIGA2B", "RECOPA", "SCESP", "SCEUR"]


@dataclass
class Token:
    idx: int
    off: int
    length: int
    text: str


def decode_tokens(blob: bytes) -> list[Token]:
    out: list[Token] = []
    start = 0
    idx = 0
    for i, b in enumerate(blob):
        if b != 0:
            continue
        if i > start:
            raw = blob[start:i]
            txt = raw.decode("cp1252", errors="replace")
            out.append(Token(idx=idx, off=start, length=i - start, text=txt))
            idx += 1
        start = i + 1
    return out


def _is_mostly_printable(raw: bytes) -> bool:
    if not raw:
        return True
    printable = sum(1 for b in raw if (32 <= b <= 126) or (160 <= b <= 255))
    return printable >= int(len(raw) * 0.8)


def flow_hits(tokens: list[Token], markers: Iterable[str]) -> list[dict[str, object]]:
    up_markers = [m.upper() for m in markers]
    hits: list[dict[str, object]] = []
    for i, tok in enumerate(tokens):
        up = tok.text.upper()
        for marker in up_markers:
            if marker not in up:
                continue
            ctx = []
            for j in range(max(0, i - 4), min(len(tokens), i + 8)):
                t = tokens[j]
                ctx.append(
                    {
                        "idx": t.idx,
                        "off": t.off,
                        "len": t.length,
                        "text": t.text,
                        "is_hit": j == i,
                    }
                )
            hits.append({"marker": marker, "token": tok.text, "idx": tok.idx, "off": tok.off, "context": ctx})
            break
    return hits


def competition_key_blocks(blob: bytes, keys: Iterable[str]) -> list[dict[str, object]]:
    out: list[dict[str, object]] = []
    for key in keys:
        bkey = key.encode("ascii")
        pos = 0
        while True:
            off = blob.find(bkey, pos)
            if off < 0:
                break
            pos = off + 1

            pair_off = blob.find(bkey, off + len(bkey))
            if pair_off < 0 or pair_off - off > 16:
                pair_off = None

            pre_u32 = []
            for p in (off - 12, off - 8, off - 4):
                if p >= 0:
                    pre_u32.append(int.from_bytes(blob[p : p + 4], "little", signed=False))

            post_start = (pair_off + len(bkey)) if pair_off is not None else (off + len(bkey))
            while post_start < len(blob) and blob[post_start] == 0:
                post_start += 1
            if post_start % 4:
                post_start += 4 - (post_start % 4)
            post_u32 = []
            for i in range(0, 24):
                p = post_start + (i * 4)
                if p + 4 > len(blob):
                    break
                post_u32.append(int.from_bytes(blob[p : p + 4], "little", signed=False))

            out.append(
                {
                    "key": key,
                    "offset": off,
                    "pair_offset": pair_off,
                    "pre_u32": pre_u32,
                    "post_u32_head": post_u32,
                }
            )
    return out


def diff_ranges(a: bytes, b: bytes) -> list[dict[str, object]]:
    if len(a) != len(b):
        raise ValueError(f"length mismatch: {len(a)} vs {len(b)}")
    idx = [i for i, (x, y) in enumerate(zip(a, b)) if x != y]
    if not idx:
        return []

    ranges = []
    s = idx[0]
    p = idx[0]
    for i in idx[1:]:
        if i == p + 1:
            p = i
            continue
        ranges.append((s, p))
        s = p = i
    ranges.append((s, p))

    out: list[dict[str, object]] = []
    for s, e in ranges:
        cur = a[s : e + 1]
        oth = b[s : e + 1]
        out.append(
            {
                "start": s,
                "end": e,
                "length": (e - s + 1),
                "current_hex": cur.hex(),
                "other_hex": oth.hex(),
                "current_ascii": "".join(chr(x) if 32 <= x < 127 else "." for x in cur),
                "other_ascii": "".join(chr(x) if 32 <= x < 127 else "." for x in oth),
                "looks_textual": _is_mostly_printable(cur) and _is_mostly_printable(oth),
            }
        )
    return out


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--mandos", type=Path, default=DEFAULT_MANDOS)
    p.add_argument("--compare", type=Path, default=None, help="Optional second MANDOS file for byte diff.")
    p.add_argument("--out", type=Path, default=DEFAULT_OUT)
    return p.parse_args()


def main() -> None:
    args = parse_args()
    blob = args.mandos.read_bytes()
    tokens = decode_tokens(blob)

    report: dict[str, object] = {
        "file": str(args.mandos),
        "size": len(blob),
        "token_count": len(tokens),
        "pro_manager_flow_hits": flow_hits(tokens, FLOW_MARKERS),
        "competition_key_blocks": competition_key_blocks(blob, COMP_KEYS),
    }

    if args.compare is not None:
        other = args.compare.read_bytes()
        diffs = diff_ranges(blob, other)
        report["compare_file"] = str(args.compare)
        report["diff_ranges"] = diffs
        report["diff_summary"] = {
            "range_count": len(diffs),
            "byte_count": sum(int(r["length"]) for r in diffs),
            "text_like_ranges": sum(1 for r in diffs if bool(r["looks_textual"])),
            "binary_like_ranges": sum(1 for r in diffs if not bool(r["looks_textual"])),
        }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"MANDOS file: {args.mandos}")
    print(f"Size: {len(blob)} bytes, tokens: {len(tokens)}")
    print(f"Flow hits: {len(report['pro_manager_flow_hits'])}")
    print(f"Competition blocks: {len(report['competition_key_blocks'])}")
    if "diff_summary" in report:
        ds = report["diff_summary"]
        print(
            "Diff summary: "
            f"ranges={ds['range_count']}, bytes={ds['byte_count']}, "
            f"text_like={ds['text_like_ranges']}, binary_like={ds['binary_like_ranges']}"
        )
    print(f"Report: {args.out}")


if __name__ == "__main__":
    main()

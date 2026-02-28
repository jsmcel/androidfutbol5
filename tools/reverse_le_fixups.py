#!/usr/bin/env python
"""Inspect LE fixup-page table density and page/object mapping.

Useful to locate pages that likely contain active code/relocations and to
correlate target file offsets (e.g. manager/pro-manager strings) with nearby
relocation-heavy zones.
"""

from __future__ import annotations

import argparse
import json
import struct
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import List, Optional

from reverse_le import parse_le


@dataclass
class FixupPage:
    page: int
    object_index: Optional[int]
    file_start: int
    file_end: int
    fixup_record_start: int
    fixup_record_end: int
    fixup_record_bytes: int


def object_for_page(info, page: int) -> Optional[int]:
    for o in info.objects:
        first = o.page_table_index
        last = o.page_table_index + o.page_count - 1
        if first <= page <= last:
            return o.index
    return None


def parse_fixup_pages(path: Path) -> tuple[List[FixupPage], Optional[str]]:
    info = parse_le(path)
    blob = path.read_bytes()
    le = info.le_offset

    if info.fixup_page_table_offset <= 0 or info.fixup_record_table_offset <= 0:
        return [], "fixup table offsets are zero in LE header"

    page_table_abs = le + info.fixup_page_table_offset
    record_table_abs = le + info.fixup_record_table_offset

    # LE stores one u32 entry per page + 1 sentinel.
    need = info.module_pages + 1
    entries: List[int] = []
    for i in range(need):
        off = page_table_abs + (i * 4)
        if off + 4 > len(blob):
            break
        entries.append(struct.unpack_from("<I", blob, off)[0])

    if len(entries) < 2:
        return [], "fixup page table is truncated"

    rows: List[FixupPage] = []
    page_count = min(info.module_pages, len(entries) - 1)
    for page in range(1, page_count + 1):
        s = entries[page - 1]
        e = entries[page]
        if e < s:
            # malformed range; keep row but clamp.
            e = s
        file_start = info.data_pages_offset + ((page - 1) * info.page_size)
        file_end = file_start + info.page_size - 1
        rows.append(
            FixupPage(
                page=page,
                object_index=object_for_page(info, page),
                file_start=file_start,
                file_end=file_end,
                fixup_record_start=record_table_abs + s,
                fixup_record_end=record_table_abs + e,
                fixup_record_bytes=e - s,
            )
        )
    return rows, None


def page_for_offset(rows: List[FixupPage], off: int) -> Optional[FixupPage]:
    for r in rows:
        if r.file_start <= off <= r.file_end:
            return r
    return None


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--file", type=Path, required=True)
    p.add_argument(
        "--target-offset",
        nargs="*",
        default=[],
        help="Optional file offsets (decimal or 0xHEX) to resolve to fixup pages.",
    )
    p.add_argument("--top", type=int, default=20, help="Show top pages by fixup bytes.")
    p.add_argument("--out", type=Path, default=None)
    return p.parse_args()


def parse_target_offsets(vals: List[str]) -> List[int]:
    out: List[int] = []
    for v in vals:
        x = v.strip().lower()
        if not x:
            continue
        if x.startswith("0x"):
            out.append(int(x, 16))
        else:
            out.append(int(x))
    return out


def main() -> None:
    args = parse_args()
    rows, parse_note = parse_fixup_pages(args.file)
    targets = parse_target_offsets(args.target_offset)

    top = sorted(rows, key=lambda r: r.fixup_record_bytes, reverse=True)[: max(1, args.top)]
    target_rows = []
    for t in targets:
        r = page_for_offset(rows, t)
        target_rows.append(
            {
                "target_offset": t,
                "page": r.page if r else None,
                "object_index": r.object_index if r else None,
                "page_file_start": r.file_start if r else None,
                "page_file_end": r.file_end if r else None,
                "fixup_record_bytes": r.fixup_record_bytes if r else None,
            }
        )

    report = {
        "file": str(args.file),
        "page_count": len(rows),
        "top_fixup_pages": [asdict(r) for r in top],
        "target_pages": target_rows,
        "nonzero_fixup_pages": sum(1 for r in rows if r.fixup_record_bytes > 0),
        "zero_fixup_pages": sum(1 for r in rows if r.fixup_record_bytes == 0),
        "note": parse_note,
    }

    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"File: {args.file}")
    print(f"Pages parsed: {len(rows)}")
    if parse_note:
        print(f"Note: {parse_note}")
    print(
        f"Fixup pages: nonzero={report['nonzero_fixup_pages']} zero={report['zero_fixup_pages']}"
    )
    print("Top pages by fixup bytes:")
    for r in top:
        print(
            f"- page={r.page} obj={r.object_index} fixup_bytes={r.fixup_record_bytes} "
            f"file={r.file_start}..{r.file_end}"
        )
    if target_rows:
        print("Targets:")
        for tr in target_rows:
            print(
                f"- off={tr['target_offset']} -> page={tr['page']} obj={tr['object_index']} "
                f"fixup_bytes={tr['fixup_record_bytes']}"
            )
    if args.out:
        print(f"Report: {args.out}")


if __name__ == "__main__":
    main()

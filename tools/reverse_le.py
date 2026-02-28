#!/usr/bin/env python
"""Reverse-engineering helper for DOS/4GW LE binaries used by PCF55."""

from __future__ import annotations

import argparse
import json
import struct
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple


@dataclass
class LeObject:
    index: int
    virtual_size: int
    relocation_base: int
    flags: int
    page_table_index: int
    page_count: int
    reserved: int

    @property
    def page_first(self) -> int:
        return self.page_table_index

    @property
    def page_last(self) -> int:
        return self.page_table_index + self.page_count - 1


@dataclass
class LeInfo:
    path: str
    file_size: int
    mz_offset_lfanew: int
    le_offset: int
    signature: str
    cpu_type: int
    os_type: int
    module_flags: int
    module_pages: int
    page_size: int
    object_table_offset: int
    object_count: int
    object_page_map_offset: int
    fixup_page_table_offset: int
    fixup_record_table_offset: int
    import_module_name_table_offset: int
    import_proc_name_table_offset: int
    data_pages_offset: int
    nonresident_names_offset: int
    nonresident_names_length: int
    objects: List[LeObject]


@dataclass
class DiffRange:
    start: int
    end: int
    length: int
    section: str
    page_start: Optional[int]
    page_end: Optional[int]
    object_index: Optional[int]


def _u16(buf: bytes, off: int) -> int:
    return struct.unpack_from("<H", buf, off)[0]


def _u32(buf: bytes, off: int) -> int:
    return struct.unpack_from("<I", buf, off)[0]


def parse_le(path: Path) -> LeInfo:
    b = path.read_bytes()
    if b[:2] != b"MZ":
        raise ValueError(f"{path}: not an MZ file")
    lfanew = _u32(b, 0x3C)
    if lfanew <= 0 or lfanew + 0xC0 > len(b):
        raise ValueError(f"{path}: invalid e_lfanew={lfanew}")
    sig = b[lfanew : lfanew + 2]
    if sig != b"LE":
        raise ValueError(f"{path}: e_lfanew points to non-LE signature {sig!r}")

    # Offsets are LE-header-relative unless noted otherwise.
    cpu_type = _u16(b, lfanew + 0x08)
    os_type = _u16(b, lfanew + 0x0A)
    module_flags = _u32(b, lfanew + 0x10)
    module_pages = _u32(b, lfanew + 0x14)
    page_size = _u32(b, lfanew + 0x28)
    object_table_offset = _u32(b, lfanew + 0x40)
    object_count = _u32(b, lfanew + 0x44)
    object_page_map_offset = _u32(b, lfanew + 0x48)
    fixup_page_table_offset = _u32(b, lfanew + 0x60)
    fixup_record_table_offset = _u32(b, lfanew + 0x64)
    import_module_name_table_offset = _u32(b, lfanew + 0x68)
    import_proc_name_table_offset = _u32(b, lfanew + 0x6C)
    data_pages_offset = _u32(b, lfanew + 0x80)
    nonresident_names_offset = _u32(b, lfanew + 0x84)
    nonresident_names_length = _u32(b, lfanew + 0x88)

    objects: List[LeObject] = []
    for i in range(object_count):
        o = lfanew + object_table_offset + (i * 24)
        if o + 24 > len(b):
            break
        vals = struct.unpack_from("<6I", b, o)
        objects.append(
            LeObject(
                index=i + 1,
                virtual_size=vals[0],
                relocation_base=vals[1],
                flags=vals[2],
                page_table_index=vals[3],
                page_count=vals[4],
                reserved=vals[5],
            )
        )

    return LeInfo(
        path=str(path),
        file_size=len(b),
        mz_offset_lfanew=0x3C,
        le_offset=lfanew,
        signature="LE",
        cpu_type=cpu_type,
        os_type=os_type,
        module_flags=module_flags,
        module_pages=module_pages,
        page_size=page_size,
        object_table_offset=object_table_offset,
        object_count=object_count,
        object_page_map_offset=object_page_map_offset,
        fixup_page_table_offset=fixup_page_table_offset,
        fixup_record_table_offset=fixup_record_table_offset,
        import_module_name_table_offset=import_module_name_table_offset,
        import_proc_name_table_offset=import_proc_name_table_offset,
        data_pages_offset=data_pages_offset,
        nonresident_names_offset=nonresident_names_offset,
        nonresident_names_length=nonresident_names_length,
        objects=objects,
    )


def section_for_offset(info: LeInfo, off: int) -> str:
    le_off = info.le_offset
    if off < le_off:
        return "mz_stub"
    if off < le_off + 0xC0:
        return "le_header"
    data_off = info.data_pages_offset
    if data_off and off < data_off:
        return "le_loader_tables"
    if data_off and off >= data_off:
        return "le_paged_data"
    return "unknown"


def page_for_offset(info: LeInfo, off: int) -> Optional[int]:
    if info.data_pages_offset <= 0 or info.page_size <= 0:
        return None
    if off < info.data_pages_offset:
        return None
    rel = off - info.data_pages_offset
    return (rel // info.page_size) + 1


def object_for_page(info: LeInfo, page: Optional[int]) -> Optional[int]:
    if page is None:
        return None
    for obj in info.objects:
        if obj.page_first <= page <= obj.page_last:
            return obj.index
    return None


def diff_ranges(a: bytes, b: bytes) -> List[Tuple[int, int]]:
    out: List[Tuple[int, int]] = []
    n = min(len(a), len(b))
    i = 0
    while i < n:
        if a[i] == b[i]:
            i += 1
            continue
        s = i
        while i < n and a[i] != b[i]:
            i += 1
        out.append((s, i - 1))
    if len(a) != len(b):
        out.append((n, max(len(a), len(b)) - 1))
    return out


def classify_diffs(info: LeInfo, ranges: List[Tuple[int, int]]) -> List[DiffRange]:
    rows: List[DiffRange] = []
    for s, e in ranges:
        ps = page_for_offset(info, s)
        pe = page_for_offset(info, e)
        obj = object_for_page(info, ps)
        rows.append(
            DiffRange(
                start=s,
                end=e,
                length=(e - s + 1),
                section=section_for_offset(info, s),
                page_start=ps,
                page_end=pe,
                object_index=obj,
            )
        )
    return rows


def find_ascii_tokens(data: bytes, min_len: int) -> List[Dict[str, object]]:
    rows: List[Dict[str, object]] = []
    i = 0
    while i < len(data):
        if not (32 <= data[i] <= 126 or 160 <= data[i] <= 255):
            i += 1
            continue
        s = i
        while i < len(data) and (32 <= data[i] <= 126 or 160 <= data[i] <= 255):
            i += 1
        if i - s >= min_len:
            txt = data[s:i].decode("cp1252", errors="replace")
            rows.append({"offset": s, "len": i - s, "text": txt})
    return rows


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--file", type=Path, required=True, help="Primary LE file")
    p.add_argument("--compare", type=Path, default=None, help="Optional second file for diff")
    p.add_argument("--find", nargs="*", default=[], help="ASCII patterns to locate")
    p.add_argument("--min-string-len", type=int, default=12)
    p.add_argument("--out", type=Path, default=None, help="Optional JSON report path")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    info = parse_le(args.file)
    data = args.file.read_bytes()

    found: Dict[str, List[int]] = {}
    for pat in args.find:
        p = pat.encode("cp1252", errors="replace")
        offs: List[int] = []
        i = data.find(p)
        while i != -1:
            offs.append(i)
            i = data.find(p, i + 1)
        found[pat] = offs

    report: Dict[str, object] = {
        "file": asdict(info),
        "find": found,
        "sample_strings": find_ascii_tokens(data, args.min_string_len)[:200],
    }

    if args.compare:
        b = args.compare.read_bytes()
        ranges = diff_ranges(data, b)
        classified = classify_diffs(info, ranges)
        report["compare_file"] = str(args.compare)
        report["diff_count"] = len(classified)
        report["diff_total_bytes"] = sum(r.length for r in classified)
        report["diff_ranges"] = [asdict(r) for r in classified]

    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"File: {args.file}")
    print(f"LE offset: {info.le_offset} | pages={info.module_pages} | page_size={info.page_size}")
    print(f"Objects: {len(info.objects)}")
    for o in info.objects:
        print(
            f"- obj{o.index}: vsize={o.virtual_size} base={o.relocation_base} "
            f"flags=0x{o.flags:08X} pages={o.page_first}-{o.page_last} ({o.page_count})"
        )
    if args.compare:
        print(f"Compare: {args.compare}")
        print(
            f"Diffs: {report['diff_count']} ranges | {report['diff_total_bytes']} bytes"
        )
        top = report["diff_ranges"][:10]
        for r in top:
            print(
                f"  - {r['start']}..{r['end']} len={r['length']} "
                f"section={r['section']} page={r['page_start']} obj={r['object_index']}"
            )
    if args.find:
        for k, v in found.items():
            print(f"Find '{k}': {len(v)} hit(s) -> {v[:10]}")
    if args.out:
        print(f"Report: {args.out}")


if __name__ == "__main__":
    main()

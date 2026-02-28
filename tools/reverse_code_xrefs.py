#!/usr/bin/env python
"""Disassemble LE objects and find xrefs to key file/string offsets."""

from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Dict, List, Optional

from capstone import CS_ARCH_X86, CS_MODE_32, Cs  # type: ignore
from capstone.x86 import X86_OP_IMM, X86_OP_MEM  # type: ignore

from reverse_le import LeInfo, LeObject, parse_le


@dataclass
class ObjMap:
    index: int
    va_start: int
    va_end: int
    file_start: int
    file_end: int
    page_count: int


@dataclass
class Xref:
    target_name: str
    target_va: int
    target_file_offset: int
    insn_va: int
    insn_file_offset: int
    mnemonic: str
    op_str: str
    kind: str


def build_linear_object_maps(info: LeInfo) -> List[ObjMap]:
    """Map objects assuming paged data stored contiguously by object order."""
    maps: List[ObjMap] = []
    cur_file = info.data_pages_offset
    for obj in info.objects:
        span = obj.page_count * info.page_size
        va_start = obj.relocation_base
        va_end = obj.relocation_base + max(0, obj.virtual_size - 1)
        maps.append(
            ObjMap(
                index=obj.index,
                va_start=va_start,
                va_end=va_end,
                file_start=cur_file,
                file_end=cur_file + span - 1,
                page_count=obj.page_count,
            )
        )
        cur_file += span
    return maps


def file_to_va(obj_maps: List[ObjMap], off: int) -> Optional[int]:
    for om in obj_maps:
        if om.file_start <= off <= om.file_end:
            delta = off - om.file_start
            return om.va_start + delta
    return None


def va_to_file(obj_maps: List[ObjMap], va: int) -> Optional[int]:
    for om in obj_maps:
        if om.va_start <= va <= om.va_end:
            delta = va - om.va_start
            return om.file_start + delta
    return None


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--file", type=Path, required=True, help="LE file")
    p.add_argument(
        "--targets-json",
        type=Path,
        default=None,
        help="Optional JSON mapping {name: file_offset}",
    )
    p.add_argument(
        "--target",
        nargs="*",
        default=[],
        help="Inline targets as name=offset (offset decimal or 0xHEX)",
    )
    p.add_argument(
        "--object",
        type=int,
        default=1,
        help="Object index to disassemble (default obj1)",
    )
    p.add_argument("--max-insns", type=int, default=2_000_000)
    p.add_argument("--out", type=Path, default=None)
    return p.parse_args()


def parse_targets(args: argparse.Namespace) -> Dict[str, int]:
    out: Dict[str, int] = {}
    if args.targets_json:
        raw = json.loads(args.targets_json.read_text(encoding="utf-8"))
        for k, v in raw.items():
            out[str(k)] = int(v)
    for t in args.target:
        if "=" not in t:
            continue
        k, v = t.split("=", 1)
        v = v.strip()
        if v.lower().startswith("0x"):
            out[k.strip()] = int(v, 16)
        else:
            out[k.strip()] = int(v)
    return out


def main() -> None:
    args = parse_args()
    info = parse_le(args.file)
    blob = args.file.read_bytes()
    obj_maps = build_linear_object_maps(info)
    om = next((x for x in obj_maps if x.index == args.object), None)
    if om is None:
        raise SystemExit(f"Object {args.object} not found")

    targets_file = parse_targets(args)
    targets: Dict[str, Dict[str, int]] = {}
    for name, off in targets_file.items():
        va = file_to_va(obj_maps, off)
        if va is None:
            continue
        targets[name] = {"file_offset": off, "va": va}

    md = Cs(CS_ARCH_X86, CS_MODE_32)
    md.detail = True
    code = blob[om.file_start : om.file_end + 1]
    base_va = om.va_start

    xrefs: List[Xref] = []
    insn_count = 0
    target_vas = {name: d["va"] for name, d in targets.items()}

    for insn in md.disasm(code, base_va):
        insn_count += 1
        if insn_count > args.max_insns:
            break
        insn_file_off = va_to_file(obj_maps, insn.address)
        if insn_file_off is None:
            continue
        for op in insn.operands:
            if op.type == X86_OP_IMM:
                imm = op.imm & 0xFFFFFFFF
                for name, tv in target_vas.items():
                    if imm == tv:
                        xrefs.append(
                            Xref(
                                target_name=name,
                                target_va=tv,
                                target_file_offset=targets[name]["file_offset"],
                                insn_va=insn.address,
                                insn_file_offset=insn_file_off,
                                mnemonic=insn.mnemonic,
                                op_str=insn.op_str,
                                kind="imm",
                            )
                        )
            elif op.type == X86_OP_MEM:
                disp = op.mem.disp & 0xFFFFFFFF
                for name, tv in target_vas.items():
                    if disp == tv:
                        xrefs.append(
                            Xref(
                                target_name=name,
                                target_va=tv,
                                target_file_offset=targets[name]["file_offset"],
                                insn_va=insn.address,
                                insn_file_offset=insn_file_off,
                                mnemonic=insn.mnemonic,
                                op_str=insn.op_str,
                                kind="mem-disp",
                            )
                        )

    report = {
        "file": str(args.file),
        "object": args.object,
        "obj_map": [asdict(x) for x in obj_maps],
        "targets": targets,
        "insn_scanned": insn_count,
        "xrefs": [asdict(x) for x in xrefs],
    }

    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"File: {args.file}")
    print(f"Object: {args.object} | scanned insns: {insn_count}")
    print(f"Targets: {len(targets)}")
    for k, v in targets.items():
        print(f"  - {k}: file={v['file_offset']} va=0x{v['va']:08X}")
    print(f"Xrefs found: {len(xrefs)}")
    for xr in xrefs[:30]:
        print(
            f"  - {xr.target_name}: {xr.mnemonic} {xr.op_str} "
            f"@va=0x{xr.insn_va:08X} file={xr.insn_file_offset}"
        )
    if args.out:
        print(f"Report: {args.out}")


if __name__ == "__main__":
    main()

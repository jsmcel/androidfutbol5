#!/usr/bin/env python
"""Patch crest PKFs with real club logos sourced from Transfermarkt."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import re
import struct
import unicodedata
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import requests
from PIL import Image


BASE = Path(__file__).resolve().parents[2] / "PCF55" / "FUTBOL5" / "FUTBOL5" / "DBDAT"
OUT_DIR = Path(__file__).resolve().parent / "out"

DEFAULT_MAPPING = OUT_DIR / "pcf55_full_mapping_global.csv"
DEFAULT_TM_JSON = OUT_DIR / "transfermarkt_teams_global.json"
DEFAULT_PALETTE = Path(__file__).resolve().parents[1] / "pcx-utils" / "fut" / "meta" / "palette.bmp"
DEFAULT_CACHE = OUT_DIR / "tm_crests_cache"
DEFAULT_PREVIEW = OUT_DIR / "tm_crests_preview"
DEFAULT_REPORT = OUT_DIR / "shield_real_patch_report.json"

SHIELD_FILES = [
    BASE / "MINIESC.PKF",
    BASE / "NANOESC.PKF",
    BASE / "RIDIESC.PKF",
]

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36"
    ),
    "Accept-Language": "en-US,en;q=0.9",
}


@dataclass
class PtrEntry:
    index: int
    start: int
    size: int
    entry_off: int


def parse_pointer_entries(blob: bytes) -> Tuple[List[PtrEntry], int]:
    out: List[PtrEntry] = []
    i = 232
    idx = 1
    offset = 232
    table_end = 232
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
            table_end = max(table_end, i)
            continue
        if b == 0x02:
            if i + 38 > len(blob):
                break
            start = struct.unpack_from("<I", blob, i + 26)[0]
            size = struct.unpack_from("<I", blob, i + 30)[0]
            out.append(PtrEntry(index=idx, start=start, size=size, entry_off=i))
            i += 38
            idx += 1
            offset += 38
            table_end = max(table_end, i)
            continue
        if b == 0x05 and blob[i + 1 : i + 5] == b"\x00\x00\x00\x00":
            table_end = max(table_end, i + 5)
            break
        i += 1
    if out:
        table_end = max(table_end, max(p.entry_off + 38 for p in out))
    return out, table_end


def norm_name(text: str) -> str:
    s = (text or "").strip().lower()
    s = unicodedata.normalize("NFKD", s)
    s = "".join(ch for ch in s if not unicodedata.combining(ch))
    s = s.replace(".", " ").replace("-", " ").replace("'", " ")
    s = re.sub(r"\b(cf|fc|cd|ud|sd|rcd|ac|ca|club|deportivo|futbol|football|s a d|sad)\b", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def score_name(a: str, b: str) -> float:
    na = norm_name(a)
    nb = norm_name(b)
    if not na or not nb:
        return 0.0
    if na == nb:
        return 1.0
    if na in nb or nb in na:
        return 0.96
    return SequenceMatcher(None, na, nb).ratio()


def load_mapping(path: Path) -> List[Dict[str, str]]:
    return list(csv.DictReader(path.open("r", encoding="utf-8", newline="")))


def load_tm(path: Path) -> List[Dict[str, object]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    teams = payload.get("teams", [])
    if not isinstance(teams, list):
        raise ValueError(f"Invalid transfermarkt payload in {path}")
    return teams


def club_id_from_url(url: str) -> Optional[str]:
    m = re.search(r"/verein/(\d+)", url or "")
    return m.group(1) if m else None


def resolve_slots(
    mapping_rows: List[Dict[str, str]],
    tm_teams: List[Dict[str, object]],
    *,
    min_score: float,
) -> Tuple[Dict[int, Dict[str, object]], List[Dict[str, object]]]:
    by_comp: Dict[str, List[Dict[str, object]]] = {}
    by_comp_exact: Dict[Tuple[str, str], List[Dict[str, object]]] = {}

    for team in tm_teams:
        comp = str(team.get("competition") or "").strip()
        name = str(team.get("team_name") or "").strip()
        if not comp or not name:
            continue
        by_comp.setdefault(comp, []).append(team)
        by_comp_exact.setdefault((comp, norm_name(name)), []).append(team)

    resolved: Dict[int, Dict[str, object]] = {}
    evidence: List[Dict[str, object]] = []

    for row in mapping_rows:
        slot = int(row["pcf_index"])
        comp = (row.get("tm_competition") or "").strip()
        wanted = (row.get("tm_team") or row.get("tm_team_patch") or "").strip()

        best: Optional[Dict[str, object]] = None
        best_score = -1.0

        exact = by_comp_exact.get((comp, norm_name(wanted)), [])
        if exact:
            best = exact[0]
            best_score = 1.0
        else:
            for cand in by_comp.get(comp, []):
                cand_name = str(cand.get("team_name") or "")
                sc = score_name(wanted, cand_name)
                if sc > best_score:
                    best = cand
                    best_score = sc

        chosen = None
        club_id = None
        team_url = ""
        team_name = ""
        if best is not None and best_score >= min_score:
            team_url = str(best.get("team_url") or "")
            team_name = str(best.get("team_name") or "")
            club_id = club_id_from_url(team_url)
            if club_id:
                chosen = {
                    "slot": slot,
                    "tm_competition": comp,
                    "wanted_team": wanted,
                    "resolved_team": team_name,
                    "team_url": team_url,
                    "club_id": club_id,
                    "score": round(best_score, 4),
                }
                resolved[slot] = chosen

        evidence.append(
            {
                "slot": slot,
                "tm_competition": comp,
                "wanted_team": wanted,
                "resolved_team": team_name,
                "team_url": team_url,
                "club_id": club_id,
                "score": round(best_score, 4) if best is not None else 0.0,
                "accepted": bool(chosen),
            }
        )

    return resolved, evidence


def ensure_logo(
    session: requests.Session,
    club_id: str,
    cache_dir: Path,
) -> Path:
    cache_dir.mkdir(parents=True, exist_ok=True)
    out = cache_dir / f"{club_id}.png"
    if out.exists() and out.stat().st_size > 256:
        return out

    candidates = [
        f"https://tmssl.akamaized.net//images/wappen/head/{club_id}.png",
        f"https://tmssl.akamaized.net//images/wappen/tiny/{club_id}.png",
    ]
    for url in candidates:
        res = session.get(url, timeout=30)
        if res.status_code != 200:
            continue
        ctype = (res.headers.get("Content-Type") or "").lower()
        if not ctype.startswith("image/"):
            continue
        if len(res.content) < 256:
            continue
        out.write_bytes(res.content)
        return out

    raise RuntimeError(f"Unable to download logo for club id {club_id}")


def dm_dims_from_chunk(chunk: bytes) -> Tuple[int, int]:
    if len(chunk) < 56 or chunk[:2] != b"DM":
        raise ValueError("Invalid DM chunk")
    width = struct.unpack_from("<I", chunk, 18)[0]
    height = struct.unpack_from("<I", chunk, 22)[0]
    return int(width), int(height)


def to_indexed_with_palette(
    rgba: Image.Image,
    palette_img: Image.Image,
) -> Image.Image:
    rgb = rgba.convert("RGB")
    idx = rgb.quantize(palette=palette_img, dither=Image.Dither.NONE)

    alpha = rgba.getchannel("A").tobytes()
    data = bytearray(idx.tobytes())
    for i, a in enumerate(alpha):
        if a < 16:
            data[i] = 0

    out = Image.frombytes("P", idx.size, bytes(data))
    out.putpalette(palette_img.getpalette()[:768])
    return out


def compose_logo(logo_png: Path, width: int, height: int) -> Image.Image:
    src = Image.open(logo_png).convert("RGBA")
    alpha = src.getchannel("A")
    bbox = alpha.getbbox()
    if bbox:
        src = src.crop(bbox)

    margin = 1
    avail_w = max(1, width - margin * 2)
    avail_h = max(1, height - margin * 2)

    scale = min(avail_w / src.width, avail_h / src.height)
    target_w = max(1, int(round(src.width * scale)))
    target_h = max(1, int(round(src.height * scale)))
    resized = src.resize((target_w, target_h), Image.Resampling.LANCZOS)

    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    x = (width - target_w) // 2
    y = (height - target_h) // 2
    canvas.paste(resized, (x, y), resized)
    return canvas


def build_dm_chunk(indexed: Image.Image) -> bytes:
    width, height = indexed.size
    row = ((width + 3) // 4) * 4
    pix = indexed.tobytes()

    payload = bytearray()
    for y in range(height - 1, -1, -1):
        begin = y * width
        line = pix[begin : begin + width]
        payload.extend(line)
        if row > width:
            payload.extend(b"\x00" * (row - width))

    total_size = 56 + len(payload)
    header = bytearray(56)
    header[0:2] = b"DM"
    struct.pack_into("<I", header, 2, total_size + 1024)
    struct.pack_into("<I", header, 10, 1078)
    struct.pack_into("<I", header, 14, 40)
    struct.pack_into("<I", header, 18, width)
    struct.pack_into("<I", header, 22, height)
    struct.pack_into("<H", header, 26, 1)
    struct.pack_into("<H", header, 28, 8)
    struct.pack_into("<I", header, 38, 2834)
    struct.pack_into("<I", header, 42, 2834)

    return bytes(header) + bytes(payload)


def rebuild_pkf(
    original: bytes,
    ptrs: List[PtrEntry],
    chunks_by_slot: Dict[int, bytes],
    table_end: int,
) -> bytes:
    if not ptrs:
        raise ValueError("No pointer entries found")

    # Rebuild from pointer table prefix to avoid unbounded growth after repeated
    # patch runs. Some PKFs store pointer records beyond the first chunk start.
    prefix_len = max(table_end, max(p.entry_off + 38 for p in ptrs))
    blob = bytearray(original[:prefix_len])
    cursor = len(blob)

    for ptr in ptrs:
        chunk = chunks_by_slot[ptr.index]
        struct.pack_into("<I", blob, ptr.entry_off + 26, cursor)
        struct.pack_into("<I", blob, ptr.entry_off + 30, len(chunk))
        blob.extend(chunk)
        cursor += len(chunk)

    return bytes(blob)


def patch_one_pkf(
    path: Path,
    slot_to_logo: Dict[int, Path],
    palette_img: Image.Image,
    preview_dir: Path,
    *,
    dry_run: bool,
) -> Dict[str, object]:
    original = path.read_bytes()
    ptrs, table_end = parse_pointer_entries(original)
    rendered: Dict[Tuple[str, int, int], bytes] = {}
    chunks_by_slot: Dict[int, bytes] = {}

    preview_dir.mkdir(parents=True, exist_ok=True)

    changed = 0
    kept = 0
    errors: List[Dict[str, object]] = []

    for ptr in ptrs:
        old_chunk = original[ptr.start : ptr.start + ptr.size]
        slot = ptr.index
        logo_png = slot_to_logo.get(slot)

        try:
            width, height = dm_dims_from_chunk(old_chunk)
        except Exception as exc:  # noqa: BLE001
            chunks_by_slot[slot] = old_chunk
            errors.append({"slot": slot, "error": f"invalid_old_chunk: {exc}"})
            kept += 1
            continue

        if logo_png is None:
            chunks_by_slot[slot] = old_chunk
            kept += 1
            continue

        key = (str(logo_png), width, height)
        if key not in rendered:
            rgba = compose_logo(logo_png, width, height)
            indexed = to_indexed_with_palette(rgba, palette_img)
            rendered[key] = build_dm_chunk(indexed)

            preview_name = f"{path.stem.lower()}_slot_{slot:03d}_{logo_png.stem}_{width}x{height}.png"
            indexed.save(preview_dir / preview_name)

        new_chunk = rendered[key]
        chunks_by_slot[slot] = new_chunk
        if new_chunk != old_chunk:
            changed += 1
        else:
            kept += 1

    new_blob = rebuild_pkf(original, ptrs, chunks_by_slot, table_end=table_end)

    backup = None
    if not dry_run:
        ts = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
        backup_path = path.with_suffix(path.suffix + f".{ts}.bak")
        backup_path.write_bytes(original)
        path.write_bytes(new_blob)
        backup = str(backup_path)

    return {
        "file": str(path),
        "entries_total": len(ptrs),
        "slots_with_logo": sum(1 for p in ptrs if p.index in slot_to_logo),
        "changed_slots": changed,
        "kept_slots": kept,
        "errors": errors[:100],
        "backup": backup,
        "size_before": len(original),
        "size_after": len(new_blob),
    }


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    p.add_argument("--tm-json", type=Path, default=DEFAULT_TM_JSON)
    p.add_argument("--palette", type=Path, default=DEFAULT_PALETTE)
    p.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE)
    p.add_argument("--preview-dir", type=Path, default=DEFAULT_PREVIEW)
    p.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    p.add_argument("--min-score", type=float, default=0.72)
    p.add_argument("--dry-run", action="store_true")
    return p.parse_args()


def main() -> None:
    args = parse_args()

    mapping_rows = load_mapping(args.mapping)
    tm_teams = load_tm(args.tm_json)
    resolved, evidence = resolve_slots(mapping_rows, tm_teams, min_score=args.min_score)

    session = requests.Session()
    session.headers.update(HEADERS)

    slot_to_logo: Dict[int, Path] = {}
    download_errors: List[Dict[str, object]] = []
    downloaded = 0

    for slot, item in sorted(resolved.items()):
        club_id = str(item["club_id"])
        try:
            logo_path = ensure_logo(session, club_id, args.cache_dir)
            slot_to_logo[slot] = logo_path
            downloaded += 1
        except Exception as exc:  # noqa: BLE001
            download_errors.append(
                {
                    "slot": slot,
                    "club_id": club_id,
                    "team": item.get("resolved_team"),
                    "error": str(exc),
                }
            )

    palette_img = Image.open(args.palette).convert("P")

    file_reports = [
        patch_one_pkf(
            p,
            slot_to_logo,
            palette_img,
            args.preview_dir,
            dry_run=args.dry_run,
        )
        for p in SHIELD_FILES
    ]

    unresolved_slots = sorted({int(r["pcf_index"]) for r in mapping_rows} - set(slot_to_logo.keys()))

    report = {
        "mapping": str(args.mapping),
        "tm_json": str(args.tm_json),
        "palette": str(args.palette),
        "dry_run": args.dry_run,
        "min_score": args.min_score,
        "slots_total": len(mapping_rows),
        "slots_resolved": len(resolved),
        "slots_with_logo": len(slot_to_logo),
        "slots_unresolved": len(unresolved_slots),
        "unresolved_slots": unresolved_slots,
        "downloaded_logos": downloaded,
        "download_errors": download_errors,
        "files": file_reports,
        "resolve_evidence_head": evidence[:300],
    }

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Resolved slots: {len(resolved)}/{len(mapping_rows)}")
    print(f"Slots with logo: {len(slot_to_logo)}")
    print(f"Unresolved slots: {len(unresolved_slots)}")
    for fr in file_reports:
        print(
            f"{fr['file']}: changed={fr['changed_slots']} kept={fr['kept_slots']} "
            f"size {fr['size_before']} -> {fr['size_after']}"
        )
    print(f"Report: {args.report}")


if __name__ == "__main__":
    main()

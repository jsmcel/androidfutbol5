#!/usr/bin/env python
"""Apply in-place-safe team/stadium text patch on PCF55 EQUIPOS.PKF."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import struct
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional


DEFAULT_PKF = (
    Path(__file__).resolve().parents[2]
    / "PCF55"
    / "FUTBOL5"
    / "FUTBOL5"
    / "DBDAT"
    / "EQUIPOS.PKF"
)
DEFAULT_EXTRACTED = Path(__file__).resolve().parent / "out" / "pcf55_teams_extracted.json"
DEFAULT_MAPPING = Path(__file__).resolve().parent / "out" / "pcf55_transfermarkt_mapping.csv"
CDM = b"Copyright (c)1996 Dinamic Multimedia"


@dataclass
class Pointer:
    index: int
    start: int
    size: int


@dataclass
class CoreTextFields:
    team_pos: int
    team_len: int
    stadium_pos: int
    stadium_len: int


def bijective(b: int) -> int:
    if b < 32:
        return b + 97 if (b & 1) == 0 else b + 95
    if b < 64:
        return b + 33 if (b & 1) == 0 else b + 31
    if b < 96:
        return b - 31 if (b & 1) == 0 else b - 33
    if b < 128:
        return b - 95 if (b & 1) == 0 else b - 97
    if b < 160:
        return b + 97 if (b & 1) == 0 else b + 95
    if b < 192:
        return b + 33 if (b & 1) == 0 else b + 31
    if b < 224:
        return b - 31 if (b & 1) == 0 else b - 33
    return b - 95 if (b & 1) == 0 else b - 97


def _fits_cp1252(text: str, fixed_len: int) -> bool:
    return len(text.encode("cp1252", errors="replace")) <= fixed_len


def _normalize_ascii(text: str) -> str:
    raw = (text or "").strip()
    raw = "".join(c for c in unicodedata.normalize("NFKD", raw) if not unicodedata.combining(c))
    return " ".join(raw.split())


def _fit_text(text: str, fixed_len: int) -> str:
    s = _normalize_ascii(text)
    if _fits_cp1252(s, fixed_len):
        return s

    # Progressive abbreviations to keep semantic meaning.
    variants = [s]
    replacements = [
        ("Estadio", "Est."),
        ("Stadion", "Std."),
        ("Stadio", "Std."),
        ("Stadium", "Std."),
        ("Arena", "Ar."),
        ("Complex", "Cpx."),
        ("Kompleksi", "Kplx."),
        ("Kompleks", "Kplx."),
        ("Municipal", "Mpal."),
        ("Deportivo", "Dep."),
        ("Atletico", "Atl."),
        ("Athletic", "Ath."),
        ("Balompie", "Balom."),
        ("Victorious", "Vict."),
        ("Barcelona", "Barca"),
        ("Valencia", "Val."),
        ("Sociedad", "Soc."),
        ("Zaragoza", "Zgz."),
        ("Andorra", "And."),
        ("Coruna", "Cor."),
        ("Saint", "St."),
        ("Sankt", "St."),
        ("Sporting", "Sp."),
        ("Football", "Ftb."),
        ("Futbol", "Ftb."),
    ]

    tmp = s
    for a, b in replacements:
        tmp = tmp.replace(a, b)
    for tok in ("S.A.D.", "S.A.D", " FC ", " CF ", " SC ", " CD ", " AC ", " UD ", " SD "):
        tmp = tmp.replace(tok, " ")
    for tok in (" de ", " del ", " la ", " el ", " da ", " do ", " das ", " dos ", " the "):
        tmp = tmp.replace(tok, " ")
    variants.append(" ".join(tmp.split()))

    # Remove common filler words.
    stop_words = {"de", "del", "la", "el", "the", "club", "futbol", "football"}
    words = [w for w in tmp.split() if w.lower() not in stop_words]
    if words:
        variants.append(" ".join(words))

    # Abbreviate long words.
    short_words = []
    for w in words or tmp.split():
        if len(w) > 6 and not w.endswith("."):
            short_words.append(w[:5] + ".")
        else:
            short_words.append(w)
    if short_words:
        variants.append(" ".join(short_words))

    # Initials fallback.
    initials = "".join((w[0] + ".") for w in (words or tmp.split()) if w)
    if initials:
        variants.append(initials)

    for v in variants:
        v = " ".join(v.split())
        if _fits_cp1252(v, fixed_len):
            return v

    # Final hard truncate (cp1252-byte aware).
    raw = s.encode("cp1252", errors="replace")[:fixed_len]
    return raw.decode("cp1252", errors="replace").rstrip()


def encode_fixed_text(plain_text: str, fixed_len: int) -> tuple[bytes, str]:
    payload = _fit_text((plain_text or "").strip(), fixed_len)
    raw = payload.encode("cp1252", errors="replace")
    if len(raw) > fixed_len:
        raw = raw[:fixed_len]
        payload = raw.decode("cp1252", errors="replace").rstrip()
    if len(raw) < fixed_len:
        raw = raw + (b" " * (fixed_len - len(raw)))
    return bytes(bijective(b) for b in raw), payload


def load_extracted(path: Path) -> Dict[int, Dict[str, object]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return {int(item["index"]): item for item in data}


def parse_pointers(blob: bytes) -> Dict[int, Pointer]:
    pointers: Dict[int, Pointer] = {}
    i = 232
    idx = 1
    offset = 232

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
            continue

        if b == 0x02:
            if i + 38 > len(blob):
                break
            start = struct.unpack_from("<I", blob, i + 26)[0]
            size = struct.unpack_from("<I", blob, i + 30)[0]
            pointers[idx] = Pointer(index=idx, start=start, size=size)
            i += 38
            idx += 1
            offset += 38
            continue

        if b == 0x05 and blob[i + 1 : i + 5] == b"\x00\x00\x00\x00":
            break

        i += 1

    return pointers


def read_u8(chunk: bytes, i: int) -> tuple[int, int]:
    if i >= len(chunk):
        raise ValueError("u8 out of bounds")
    return chunk[i], i + 1


def read_u16(chunk: bytes, i: int) -> tuple[int, int]:
    if i + 1 >= len(chunk):
        raise ValueError("u16 out of bounds")
    return struct.unpack_from("<H", chunk, i)[0], i + 2


def parse_core_text_fields(chunk: bytes) -> Optional[CoreTextFields]:
    if not chunk.startswith(CDM):
        return None
    try:
        i = len(CDM)
        _, i = read_u16(chunk, i)
        _, i = read_u16(chunk, i)
        _, i = read_u8(chunk, i)
        _, i = read_u8(chunk, i)

        team_pos = i
        team_len, i = read_u16(chunk, i)
        i += team_len

        stadium_pos = i
        stadium_len, _ = read_u16(chunk, i)

        return CoreTextFields(
            team_pos=team_pos,
            team_len=int(team_len),
            stadium_pos=stadium_pos,
            stadium_len=int(stadium_len),
        )
    except Exception:
        return None


def load_mapping(path: Path, min_score: float, only_accepted: bool) -> List[Dict[str, str]]:
    rows: List[Dict[str, str]] = []
    with path.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            score = float(row.get("score", "0") or 0)
            accepted = row.get("accepted", "0") in ("1", "true", "True")
            if score < min_score:
                continue
            if only_accepted and not accepted:
                continue
            rows.append(row)

    # Keep one slot per TM team (best score) to avoid cloning a club into several PCF slots.
    best_by_tm: Dict[str, Dict[str, str]] = {}
    for row in rows:
        key = (row.get("tm_team") or "").strip().lower()
        if not key:
            continue
        if key not in best_by_tm:
            best_by_tm[key] = row
            continue
        if float(row["score"]) > float(best_by_tm[key]["score"]):
            best_by_tm[key] = row
    return list(best_by_tm.values())


def apply_patch(
    original: bytes,
    blob: bytearray,
    mapping_rows: List[Dict[str, str]],
    skip_indices: set[int],
    extracted_by_index: Dict[int, Dict[str, object]],
    allow_heuristic_full_name: bool,
) -> Dict[str, int]:
    pointers = parse_pointers(original)
    updated_teams = 0
    updated_full_names = 0
    updated_stadiums = 0
    truncated_fields = 0
    skipped = 0
    missing_pointer = 0
    missing_core_fields = 0
    skipped_full_name_heuristic = 0

    for row in mapping_rows:
        idx = int(row["pcf_index"])
        if idx in skip_indices:
            skipped += 1
            continue

        ptr = pointers.get(idx)
        if ptr is None:
            missing_pointer += 1
            continue

        chunk = original[ptr.start : ptr.start + ptr.size]
        core = parse_core_text_fields(chunk)
        if core is None:
            missing_core_fields += 1
            continue

        tm_team = (row.get("tm_team_patch") or row.get("tm_team") or "").strip()
        tm_stadium = (row.get("tm_stadium_patch") or row.get("tm_stadium") or "").strip()

        if core.team_len:
            fixed_len = int(core.team_len)
            payload, fitted = encode_fixed_text(tm_team, fixed_len)
            if fitted != tm_team.strip():
                truncated_fields += 1
            text_start = ptr.start + int(core.team_pos) + 2
            blob[text_start : text_start + fixed_len] = payload
            updated_teams += 1
        else:
            skipped += 1

        if core.stadium_len and tm_stadium:
            fixed_len = int(core.stadium_len)
            payload, fitted = encode_fixed_text(tm_stadium, fixed_len)
            if fitted != tm_stadium.strip():
                truncated_fields += 1
            text_start = ptr.start + int(core.stadium_pos) + 2
            blob[text_start : text_start + fixed_len] = payload
            updated_stadiums += 1

        # Optional and unsafe by default: heuristics can target non-string fields.
        if allow_heuristic_full_name:
            ext = extracted_by_index.get(idx)
            full_pos = ext.get("full_pos") if ext else None
            full_len = ext.get("full_len") if ext else None
            if full_pos is not None and full_len:
                fixed_len = int(full_len)
                abs_start = ptr.start + int(full_pos) + 2
                abs_end = abs_start + fixed_len
                if ptr.start <= abs_start and abs_end <= ptr.start + ptr.size:
                    payload, fitted = encode_fixed_text(tm_team, fixed_len)
                    if fitted != tm_team.strip():
                        truncated_fields += 1
                    blob[abs_start:abs_end] = payload
                    updated_full_names += 1
                else:
                    skipped_full_name_heuristic += 1
        else:
            skipped_full_name_heuristic += 1

    return {
        "updated_teams": updated_teams,
        "updated_full_names": updated_full_names,
        "updated_stadiums": updated_stadiums,
        "truncated_fields": truncated_fields,
        "skipped": skipped,
        "missing_pointer": missing_pointer,
        "missing_core_fields": missing_core_fields,
        "skipped_full_name_heuristic": skipped_full_name_heuristic,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pkf", type=Path, default=DEFAULT_PKF)
    parser.add_argument("--extracted", type=Path, default=DEFAULT_EXTRACTED)
    parser.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    parser.add_argument(
        "--min-score",
        type=float,
        default=0.72,
        help="Minimum mapping score required",
    )
    parser.add_argument(
        "--only-accepted",
        action="store_true",
        help="Apply only rows already marked as accepted in mapping CSV",
    )
    parser.add_argument(
        "--in-place",
        action="store_true",
        help="Overwrite input PKF after creating .bak backup",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Output patched PKF path (ignored when --in-place)",
    )
    parser.add_argument(
        "--skip-indices",
        nargs="*",
        type=int,
        default=[],
        help="Optional list of pcf_index slots to skip for safe patching.",
    )
    parser.add_argument(
        "--allow-heuristic-full-name",
        action="store_true",
        help=(
            "Also patch extracted full-name field using heuristic offsets. "
            "Disabled by default because legacy extraction can target non-text bytes."
        ),
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    extracted = load_extracted(args.extracted) if args.allow_heuristic_full_name else {}
    mapping_rows = load_mapping(args.mapping, args.min_score, args.only_accepted)

    original = args.pkf.read_bytes()
    blob = bytearray(original)
    stats = apply_patch(
        original=original,
        blob=blob,
        mapping_rows=mapping_rows,
        skip_indices=set(args.skip_indices),
        extracted_by_index=extracted,
        allow_heuristic_full_name=args.allow_heuristic_full_name,
    )

    timestamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    backup = args.pkf.with_suffix(args.pkf.suffix + f".{timestamp}.bak")
    backup.write_bytes(original)

    if args.in_place:
        out_path = args.pkf
    else:
        if args.output is not None:
            out_path = args.output
        else:
            out_path = args.pkf.with_name(args.pkf.stem + ".patched" + args.pkf.suffix)
    out_path.write_bytes(bytes(blob))

    print(f"Backup: {backup}")
    print(f"Output: {out_path}")
    print(
        "Stats: "
        + ", ".join(
            [
                f"rows={len(mapping_rows)}",
                f"teams={stats['updated_teams']}",
                f"full_names={stats['updated_full_names']}",
                f"stadiums={stats['updated_stadiums']}",
                f"truncated={stats['truncated_fields']}",
                f"skipped={stats['skipped']}",
                f"missing_ptr={stats['missing_pointer']}",
                f"missing_core={stats['missing_core_fields']}",
                f"full_name_skips={stats['skipped_full_name_heuristic']}",
            ]
        )
    )


if __name__ == "__main__":
    main()

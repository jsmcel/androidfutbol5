#!/usr/bin/env python
"""Patch PCF55 player records (names + attributes + age-derived birth year)."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import re
import struct
from collections import defaultdict, deque
from dataclasses import dataclass
from pathlib import Path
from typing import Deque, Dict, Iterable, List, Optional, Sequence, Tuple


DEFAULT_PKF = (
    Path(__file__).resolve().parents[2]
    / "PCF55"
    / "FUTBOL5"
    / "FUTBOL5"
    / "DBDAT"
    / "EQUIPOS.PKF"
)
DEFAULT_MAPPING = Path(__file__).resolve().parent / "out" / "pcf55_full_mapping.csv"
DEFAULT_PLAYERS_CSV = Path(__file__).resolve().parent / "out" / "pcf55_derived_attributes_full.csv"
DEFAULT_REPORT = Path(__file__).resolve().parent / "out" / "player_patch_report.json"

FILLER_TOKENS = {
    "de",
    "del",
    "de la",
    "la",
    "el",
    "da",
    "do",
    "dos",
    "das",
    "van",
    "von",
    "di",
    "du",
    "al",
    "bin",
}


@dataclass
class Pointer:
    index: int
    start: int
    size: int


@dataclass
class SourcePlayer:
    name: str
    position_text: str
    age: Optional[int]
    VE: int
    RE: int
    AG: int
    CA: int
    PORTERO: int
    ENTRADA: int
    REGATE: int
    REMATE: int
    PASE: int
    TIRO: int


@dataclass
class PlayerRecord:
    start: int
    end: int
    pos_code: int
    day: int
    month: int
    year: int
    name_len: int
    name_text_off: int
    full_len: int
    full_text_off: int
    day_off: int
    month_off: int
    year_off: int
    attrs_off: int
    current_name: str
    current_fullname: str


class ParseError(Exception):
    pass


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


def decode_text(raw: bytes) -> str:
    return bytes(bijective(b) for b in raw).decode("cp1252", errors="replace")


def _fits_cp1252(text: str, fixed_len: int) -> bool:
    return len(text.encode("cp1252", errors="replace")) <= fixed_len


def _fit_text(text: str, fixed_len: int) -> str:
    s = " ".join((text or "").strip().split())
    if _fits_cp1252(s, fixed_len):
        return s

    variants = [s]

    # Remove connector words.
    words = [w for w in s.split() if w.lower() not in FILLER_TOKENS]
    if words:
        variants.append(" ".join(words))

    # Initial + surname fallback.
    if len(words) >= 2:
        variants.append(f"{words[0][0]}. {words[-1]}")

    # Initials.
    if words:
        variants.append("".join((w[0] + ".") for w in words if w))

    # Abbreviate long words.
    short_words = []
    for w in words or s.split():
        if len(w) > 6 and not w.endswith("."):
            short_words.append(w[:5] + ".")
        else:
            short_words.append(w)
    if short_words:
        variants.append(" ".join(short_words))

    for v in variants:
        v = " ".join(v.split())
        if _fits_cp1252(v, fixed_len):
            return v

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


def normalize(s: str) -> str:
    return " ".join((s or "").strip().lower().split())


def parse_pointers(blob: bytes) -> List[Pointer]:
    pointers: List[Pointer] = []
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
                raise ValueError(f"Invalid pointer jump at {i}: skip={skip}")
            i += skip
            offset = jump_to
            continue

        if b == 0x02:
            if i + 38 > len(blob):
                break
            start = struct.unpack_from("<I", blob, i + 26)[0]
            size = struct.unpack_from("<I", blob, i + 30)[0]
            pointers.append(Pointer(index=idx, start=start, size=size))
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
        raise ParseError("u8 out of bounds")
    return chunk[i], i + 1


def read_u16(chunk: bytes, i: int) -> tuple[int, int]:
    if i + 1 >= len(chunk):
        raise ParseError("u16 out of bounds")
    return chunk[i] | (chunk[i + 1] << 8), i + 2


def read_string(chunk: bytes, i: int) -> tuple[str, int, int, int]:
    n, i2 = read_u16(chunk, i)
    if i2 + n > len(chunk):
        raise ParseError("string out of bounds")
    s = decode_text(chunk[i2 : i2 + n])
    return s, i2 + n, n, i2


def plausible_name(s: str, min_len: int = 2, max_len: int = 80) -> bool:
    x = (s or "").replace("\x00", " ").strip()
    if not x:
        return False
    if len(x) < min_len or len(x) > max_len:
        return False
    if any(ord(ch) < 32 for ch in x):
        return False
    letters = sum(ch.isalpha() for ch in x)
    return letters >= max(1, int(len(x) * 0.3))


def parse_player_record(chunk: bytes, start: int) -> PlayerRecord:
    if chunk[start] != 0x01:
        raise ParseError("missing player prefix")
    i = start + 1

    # Core fields.
    _, i = read_u16(chunk, i)  # pid
    _, i = read_u8(chunk, i)  # number
    name, i, name_len, name_text_off = read_string(chunk, i)
    fullname, i, full_len, full_text_off = read_string(chunk, i)
    if not plausible_name(name, min_len=2, max_len=40):
        raise ParseError("bad short name")
    if not plausible_name(fullname, min_len=2, max_len=90):
        raise ParseError("bad full name")

    _, i = read_u8(chunk, i)  # index
    status, i = read_u8(chunk, i)
    roles = chunk[i : i + 6]
    i += 6
    _, i = read_u8(chunk, i)  # citizenship
    _, i = read_u8(chunk, i)  # skin
    _, i = read_u8(chunk, i)  # hair
    pos_code, i = read_u8(chunk, i)

    day_off = i
    day, i = read_u8(chunk, i)
    month_off = i
    month, i = read_u8(chunk, i)
    year_off = i
    year, i = read_u16(chunk, i)

    height, i = read_u8(chunk, i)
    weight, i = read_u8(chunk, i)

    if status > 7 or pos_code > 7:
        raise ParseError("invalid status/position")
    valid_birth = (
        (1 <= day <= 31 and 1 <= month <= 12 and 1900 <= year <= 2100)
        or (day == 0 and month == 0 and year in (0, 1900))
        or (0 <= day <= 31 and 0 <= month <= 12 and 1900 <= year <= 2100)
    )
    if not valid_birth:
        raise ParseError("invalid birth date")

    # Try extended variant first: country + 10 strings + 10 attrs.
    ext_i = i
    ext_ok = True
    try:
        _, ext_i = read_u8(chunk, ext_i)  # country db
        for _ in range(10):
            _, ext_i, _, _ = read_string(chunk, ext_i)
        attrs_off = ext_i
        attrs = list(chunk[attrs_off : attrs_off + 10])
        ext_i += 10
        if len(attrs) < 10 or sum(1 for a in attrs if a <= 160) < 7:
            ext_ok = False
    except Exception:
        ext_ok = False

    if ext_ok:
        end = ext_i
    else:
        attrs_off = i
        attrs = list(chunk[attrs_off : attrs_off + 10])
        if len(attrs) < 10 or sum(1 for a in attrs if a <= 160) < 7:
            raise ParseError("invalid simple attrs block")
        end = i + 10

    return PlayerRecord(
        start=start,
        end=end,
        pos_code=pos_code,
        day=day,
        month=month,
        year=year,
        name_len=name_len,
        name_text_off=name_text_off,
        full_len=full_len,
        full_text_off=full_text_off,
        day_off=day_off,
        month_off=month_off,
        year_off=year_off,
        attrs_off=attrs_off,
        current_name=name.strip(),
        current_fullname=fullname.strip(),
    )


def detect_player_chain(chunk: bytes, min_players: int) -> List[PlayerRecord]:
    best: Optional[tuple[int, int, int, List[PlayerRecord]]] = None
    # (count, -tail, start, records)
    for start in range(0, max(0, len(chunk) - 24)):
        if chunk[start] != 0x01:
            continue
        recs: List[PlayerRecord] = []
        j = start
        try:
            while j < len(chunk) and chunk[j] == 0x01:
                rec = parse_player_record(chunk, j)
                recs.append(rec)
                j = rec.end
        except ParseError:
            continue

        count = len(recs)
        if count < min_players:
            continue
        tail = len(chunk) - j
        score = (count, -tail, -start)
        if best is None or score > (best[0], best[1], best[2]):
            best = (count, -tail, -start, recs)
    return best[3] if best is not None else []


def role_group_code(position_text: str) -> int:
    p = (position_text or "").lower()
    if "keeper" in p or "goal" in p:
        return 0
    if any(k in p for k in ("back", "defender", "sweeper")):
        return 1
    if any(k in p for k in ("midfield", "winger")):
        return 2
    if any(k in p for k in ("forward", "striker", "centre-forward", "center-forward")):
        return 3
    return 2


def short_name_from_full(full: str) -> str:
    tokens = [t for t in re.split(r"\s+", (full or "").strip()) if t]
    if not tokens:
        return full
    filtered = [t for t in tokens if t.lower() not in FILLER_TOKENS]
    if not filtered:
        filtered = tokens
    return filtered[-1]


def load_mapping(path: Path, min_score: float, only_accepted: bool) -> Dict[int, Dict[str, str]]:
    out: Dict[int, Dict[str, str]] = {}
    with path.open("r", encoding="utf-8", newline="") as f:
        r = csv.DictReader(f)
        for row in r:
            score = float(row.get("score", "0") or 0)
            accepted = row.get("accepted", "0") in ("1", "true", "True")
            if score < min_score:
                continue
            if only_accepted and not accepted:
                continue
            out[int(row["pcf_index"])] = row
    return out


def as_int(x: str) -> Optional[int]:
    try:
        if x is None or x == "":
            return None
        return int(float(x))
    except Exception:
        return None


def load_source_players(path: Path) -> Dict[str, List[SourcePlayer]]:
    grouped: Dict[str, List[SourcePlayer]] = defaultdict(list)
    with path.open("r", encoding="utf-8", newline="") as f:
        r = csv.DictReader(f)
        for row in r:
            team = normalize(row.get("tm_team", ""))
            if not team:
                continue
            sp = SourcePlayer(
                name=row.get("player_name", "").strip(),
                position_text=row.get("position", "").strip(),
                age=as_int(row.get("age", "")),
                VE=int(row["VE"]),
                RE=int(row["RE"]),
                AG=int(row["AG"]),
                CA=int(row["CA"]),
                PORTERO=int(row["PORTERO"]),
                ENTRADA=int(row["ENTRADA"]),
                REGATE=int(row["REGATE"]),
                REMATE=int(row["REMATE"]),
                PASE=int(row["PASE"]),
                TIRO=int(row["TIRO"]),
            )
            grouped[team].append(sp)
    return grouped


def assign_sources(records: Sequence[PlayerRecord], players: Sequence[SourcePlayer]) -> List[Optional[SourcePlayer]]:
    if not records:
        return []
    if not players:
        return [None] * len(records)

    by_pos: Dict[int, Deque[int]] = defaultdict(deque)
    for i, p in enumerate(players):
        by_pos[role_group_code(p.position_text)].append(i)
    remaining: Deque[int] = deque(range(len(players)))
    available = set(range(len(players)))

    def pop_from_queue(q: Deque[int]) -> Optional[int]:
        while q and q[0] not in available:
            q.popleft()
        if not q:
            return None
        k = q.popleft()
        available.remove(k)
        return k

    out: List[Optional[SourcePlayer]] = []
    for rec in records:
        idx = pop_from_queue(by_pos[rec.pos_code])
        if idx is None:
            idx = pop_from_queue(remaining)
        out.append(players[idx] if idx is not None else None)
    return out


def patch_record(
    blob: bytearray,
    chunk_start: int,
    rec: PlayerRecord,
    src: SourcePlayer,
    season_year: int,
    attributes_mode: str,
) -> Dict[str, int]:
    stats = {
        "name_changed": 0,
        "fullname_changed": 0,
        "birth_year_changed": 0,
        "attrs_changed": 0,
        "truncated_fields": 0,
    }

    short_name = short_name_from_full(src.name)
    full_name = src.name

    name_payload, fitted_short = encode_fixed_text(short_name, rec.name_len)
    if fitted_short != short_name.strip():
        stats["truncated_fields"] += 1
    full_payload, fitted_full = encode_fixed_text(full_name, rec.full_len)
    if fitted_full != full_name.strip():
        stats["truncated_fields"] += 1

    name_abs = chunk_start + rec.name_text_off
    full_abs = chunk_start + rec.full_text_off
    if blob[name_abs : name_abs + rec.name_len] != name_payload:
        blob[name_abs : name_abs + rec.name_len] = name_payload
        stats["name_changed"] = 1
    if blob[full_abs : full_abs + rec.full_len] != full_payload:
        blob[full_abs : full_abs + rec.full_len] = full_payload
        stats["fullname_changed"] = 1

    if src.age is not None and 14 <= src.age <= 45:
        new_year = max(1958, min(2010, season_year - src.age))
        year_abs = chunk_start + rec.year_off
        old_year = blob[year_abs] | (blob[year_abs + 1] << 8)
        if old_year != new_year:
            blob[year_abs] = new_year & 0xFF
            blob[year_abs + 1] = (new_year >> 8) & 0xFF
            stats["birth_year_changed"] = 1

    if attributes_mode == "source":
        attrs_new = [
            src.VE,
            src.RE,
            src.AG,
            src.CA,
            src.REMATE,
            src.REGATE,
            src.PASE,
            src.TIRO,
            src.ENTRADA,
            src.PORTERO,
        ]
        attrs_abs = chunk_start + rec.attrs_off
        old_attrs = list(blob[attrs_abs : attrs_abs + 10])
        if old_attrs != attrs_new:
            blob[attrs_abs : attrs_abs + 10] = bytes(attrs_new)
            stats["attrs_changed"] = 1

    return stats


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pkf", type=Path, default=DEFAULT_PKF)
    parser.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    parser.add_argument("--players-csv", type=Path, default=DEFAULT_PLAYERS_CSV)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--min-score", type=float, default=0.0)
    parser.add_argument("--only-accepted", action="store_true")
    parser.add_argument("--min-chain-players", type=int, default=8)
    parser.add_argument(
        "--season-year",
        type=int,
        default=2026,
        help="Reference year for converting age -> birth year",
    )
    parser.add_argument(
        "--attributes-mode",
        choices=["reuse-existing", "source"],
        default="source",
        help=(
            "reuse-existing: keep current PCF attributes and only patch names/birth year; "
            "source: overwrite attributes using players CSV."
        ),
    )
    parser.add_argument("--in-place", action="store_true")
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument(
        "--skip-indices",
        nargs="*",
        type=int,
        default=[],
        help="PCF slot indices to skip explicitly.",
    )
    parser.add_argument(
        "--guard-chain",
        action="store_true",
        default=True,
        help="Revert per-team chunk if player chain becomes invalid after patch (default: enabled).",
    )
    parser.add_argument(
        "--no-guard-chain",
        dest="guard_chain",
        action="store_false",
        help="Disable per-team post-patch chain validation/revert.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    mapping = load_mapping(args.mapping, args.min_score, args.only_accepted)
    source_players = load_source_players(args.players_csv)
    skip_indices = set(args.skip_indices or [])

    original = args.pkf.read_bytes()
    blob = bytearray(original)
    pointers = {p.index: p for p in parse_pointers(original)}

    report_rows: List[Dict[str, object]] = []
    totals = {
        "teams_requested": len(mapping),
        "teams_with_chain": 0,
        "teams_patched": 0,
        "players_detected": 0,
        "players_patched": 0,
        "name_changed": 0,
        "fullname_changed": 0,
        "birth_year_changed": 0,
        "attrs_changed": 0,
        "truncated_fields": 0,
        "teams_missing_source": 0,
        "teams_skipped": 0,
        "teams_reverted_guard": 0,
    }

    for idx, row in sorted(mapping.items()):
        if idx in skip_indices:
            totals["teams_skipped"] += 1
            report_rows.append(
                {
                    "pcf_index": idx,
                    "tm_team": row.get("tm_team", ""),
                    "status": "skipped_by_index",
                }
            )
            continue

        ptr = pointers.get(idx)
        if ptr is None:
            report_rows.append({"pcf_index": idx, "status": "missing_pointer"})
            continue

        chunk_before = bytes(blob[ptr.start : ptr.start + ptr.size])
        records = detect_player_chain(chunk_before, min_players=args.min_chain_players)
        if not records:
            report_rows.append(
                {
                    "pcf_index": idx,
                    "tm_team": row.get("tm_team", ""),
                    "status": "no_player_chain",
                    "chunk_size": ptr.size,
                }
            )
            continue

        totals["teams_with_chain"] += 1
        totals["players_detected"] += len(records)

        team_key = normalize(row.get("tm_team", ""))
        src_players = source_players.get(team_key, [])
        if not src_players:
            totals["teams_missing_source"] += 1
            report_rows.append(
                {
                    "pcf_index": idx,
                    "tm_team": row.get("tm_team", ""),
                    "status": "missing_source_team",
                    "players_detected": len(records),
                }
            )
            continue

        assigned = assign_sources(records, src_players)
        patched = 0
        team_stats = {
            "name_changed": 0,
            "fullname_changed": 0,
            "birth_year_changed": 0,
            "attrs_changed": 0,
            "truncated_fields": 0,
        }

        for rec, src in zip(records, assigned):
            if src is None:
                continue
            st = patch_record(
                blob=blob,
                chunk_start=ptr.start,
                rec=rec,
                src=src,
                season_year=args.season_year,
                attributes_mode=args.attributes_mode,
            )
            patched += 1
            for k in team_stats:
                team_stats[k] += st[k]

        if args.guard_chain:
            chunk_after = bytes(blob[ptr.start : ptr.start + ptr.size])
            records_after = detect_player_chain(chunk_after, min_players=args.min_chain_players)
            if not records_after:
                blob[ptr.start : ptr.start + ptr.size] = chunk_before
                totals["teams_reverted_guard"] += 1
                report_rows.append(
                    {
                        "pcf_index": idx,
                        "pcf_team": row.get("pcf_team", ""),
                        "tm_team": row.get("tm_team", ""),
                        "status": "reverted_guard_no_chain",
                        "players_detected_before": len(records),
                        "players_source": len(src_players),
                    }
                )
                continue

        if patched > 0:
            totals["teams_patched"] += 1
        totals["players_patched"] += patched
        for k in team_stats:
            totals[k] += team_stats[k]

        report_rows.append(
            {
                "pcf_index": idx,
                "pcf_team": row.get("pcf_team", ""),
                "tm_team": row.get("tm_team", ""),
                "status": "patched",
                "players_detected": len(records),
                "players_source": len(src_players),
                "players_patched": patched,
                **team_stats,
            }
        )

    timestamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    backup = args.pkf.with_suffix(args.pkf.suffix + f".{timestamp}.bak")
    backup.write_bytes(original)

    if args.in_place:
        out_path = args.pkf
    else:
        out_path = args.output or args.pkf.with_name(args.pkf.stem + ".players_patched" + args.pkf.suffix)
    out_path.write_bytes(bytes(blob))

    report = {
        "pkf_input": str(args.pkf),
        "pkf_output": str(out_path),
        "backup": str(backup),
        "mapping": str(args.mapping),
        "players_csv": str(args.players_csv),
        "attributes_mode": args.attributes_mode,
        "totals": totals,
        "teams": report_rows,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Backup: {backup}")
    print(f"Output: {out_path}")
    print(f"Attributes mode: {args.attributes_mode}")
    print(
        "Totals: "
        + ", ".join(
            [
                f"teams_requested={totals['teams_requested']}",
                f"teams_with_chain={totals['teams_with_chain']}",
                f"teams_patched={totals['teams_patched']}",
                f"players_detected={totals['players_detected']}",
                f"players_patched={totals['players_patched']}",
                f"attrs_changed={totals['attrs_changed']}",
                f"birth_year_changed={totals['birth_year_changed']}",
                f"truncated={totals['truncated_fields']}",
                f"teams_skipped={totals['teams_skipped']}",
                f"teams_reverted_guard={totals['teams_reverted_guard']}",
            ]
        )
    )
    print(f"Report: {args.report}")


if __name__ == "__main__":
    main()

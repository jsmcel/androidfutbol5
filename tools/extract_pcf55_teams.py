#!/usr/bin/env python
"""Extract team metadata candidates from PC Futbol 5.5 EQUIPOS.PKF."""

from __future__ import annotations

import argparse
import csv
import json
import re
import struct
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import List, Optional


DEFAULT_PKF = (
    Path(__file__).resolve().parents[2]
    / "PCF55"
    / "FUTBOL5"
    / "FUTBOL5"
    / "DBDAT"
    / "EQUIPOS.PKF"
)

PLAUSIBLE_TEXT_RE = re.compile(r"^[A-Za-z0-9À-ÿ][A-Za-z0-9À-ÿ '\-.,()/&]{1,64}$")
GENERIC_BLOCKLIST = {
    "patrocinador",
    "proveedor",
    "copyright",
    "no tiene",
}
CLUB_HINTS = (
    "club",
    "cf",
    "fc",
    "deportivo",
    "athletic",
    "real",
    "s.a.d",
    "futbol",
    "futbol club",
    "football club",
    "sportclub",
    "olympique",
)


@dataclass
class Pointer:
    index: int
    start: int
    size: int
    uid_hex: str


@dataclass
class Candidate:
    pos: int
    length: int
    text: str


@dataclass
class ExtractedTeam:
    index: int
    start: int
    size: int
    uid_hex: str
    team_name: str
    team_pos: Optional[int]
    team_len: Optional[int]
    stadium_name: str
    stadium_pos: Optional[int]
    stadium_len: Optional[int]
    full_name: str
    full_pos: Optional[int]
    full_len: Optional[int]
    confidence: float
    candidates: List[Candidate]


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


def decode_pcx_text(raw: bytes) -> str:
    decoded = bytes(bijective(b) for b in raw)
    return decoded.decode("cp1252", errors="replace")


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
            uid = blob[i + 5 : i + 9]
            start = struct.unpack_from("<I", blob, i + 26)[0]
            size = struct.unpack_from("<I", blob, i + 30)[0]
            pointers.append(
                Pointer(
                    index=idx,
                    start=start,
                    size=size,
                    uid_hex=uid.hex(" "),
                )
            )
            i += 38
            idx += 1
            offset += 38
            continue

        if b == 0x05 and blob[i + 1 : i + 5] == b"\x00\x00\x00\x00":
            break

        i += 1

    return pointers


def normalize(s: str) -> str:
    return re.sub(r"\s+", " ", s.strip().lower())


def is_plausible_text(s: str) -> bool:
    s = s.replace("\x00", " ").strip()
    if not s:
        return False
    if not PLAUSIBLE_TEXT_RE.match(s):
        return False
    letters = sum(ch.isalpha() for ch in s)
    return letters >= max(2, int(len(s) * 0.35))


def scan_candidates(chunk: bytes, max_scan: int = 260, max_len: int = 64) -> List[Candidate]:
    out: List[Candidate] = []
    pos = 0
    limit = min(max_scan, max(0, len(chunk) - 2))

    while pos < limit:
        n = chunk[pos] | (chunk[pos + 1] << 8)
        if 2 <= n <= max_len and pos + 2 + n <= len(chunk):
            text = decode_pcx_text(chunk[pos + 2 : pos + 2 + n]).replace("\x00", " ").strip()
            if is_plausible_text(text):
                out.append(Candidate(pos=pos, length=n, text=text))
                pos += 2 + n
                continue
        pos += 1

    return out


def is_generic(text: str) -> bool:
    t = normalize(text)
    return any(word in t for word in GENERIC_BLOCKLIST)


def looks_like_sentence(text: str) -> bool:
    t = text.strip()
    return len(t.split()) >= 5 and not t.isupper()


def has_club_hint(text: str) -> bool:
    t = normalize(text)
    return any(h in t for h in CLUB_HINTS)


def pick_fields(candidates: List[Candidate]) -> tuple[Optional[Candidate], Optional[Candidate], Optional[Candidate], float]:
    team: Optional[Candidate] = None
    stadium: Optional[Candidate] = None
    full_name: Optional[Candidate] = None

    for cand in candidates:
        if is_generic(cand.text):
            continue
        if looks_like_sentence(cand.text):
            continue
        if 3 <= len(cand.text) <= 28:
            team = cand
            break

    if team:
        start_idx = candidates.index(team) + 1
    else:
        start_idx = 0

    for cand in candidates[start_idx:]:
        if is_generic(cand.text):
            continue
        if looks_like_sentence(cand.text):
            continue
        if has_club_hint(cand.text):
            if full_name is None:
                full_name = cand
            continue
        if 4 <= len(cand.text) <= 35:
            stadium = cand
            break

    if full_name is None:
        for cand in candidates[start_idx:]:
            if is_generic(cand.text):
                continue
            if has_club_hint(cand.text):
                full_name = cand
                break

    confidence = 0.0
    if team:
        confidence += 0.6
    if stadium:
        confidence += 0.3
    if full_name:
        confidence += 0.1

    return team, stadium, full_name, round(confidence, 2)


def extract(pkf_path: Path, max_scan: int) -> List[ExtractedTeam]:
    blob = pkf_path.read_bytes()
    pointers = parse_pointers(blob)

    teams: List[ExtractedTeam] = []
    for ptr in pointers:
        chunk = blob[ptr.start : ptr.start + ptr.size]
        candidates = scan_candidates(chunk, max_scan=max_scan)
        team, stadium, full_name, confidence = pick_fields(candidates)

        teams.append(
            ExtractedTeam(
                index=ptr.index,
                start=ptr.start,
                size=ptr.size,
                uid_hex=ptr.uid_hex,
                team_name=team.text if team else "",
                team_pos=team.pos if team else None,
                team_len=team.length if team else None,
                stadium_name=stadium.text if stadium else "",
                stadium_pos=stadium.pos if stadium else None,
                stadium_len=stadium.length if stadium else None,
                full_name=full_name.text if full_name else "",
                full_pos=full_name.pos if full_name else None,
                full_len=full_name.length if full_name else None,
                confidence=confidence,
                candidates=candidates[:10],
            )
        )
    return teams


def write_outputs(teams: List[ExtractedTeam], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    json_path = out_dir / "pcf55_teams_extracted.json"
    csv_path = out_dir / "pcf55_teams_extracted.csv"

    with json_path.open("w", encoding="utf-8") as f:
        json.dump(
            [
                {
                    **asdict(team),
                    "candidates": [asdict(c) for c in team.candidates],
                }
                for team in teams
            ],
            f,
            ensure_ascii=False,
            indent=2,
        )

    with csv_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(
            [
                "index",
                "start",
                "size",
                "uid_hex",
                "team_name",
                "team_pos",
                "team_len",
                "stadium_name",
                "stadium_pos",
                "stadium_len",
                "full_name",
                "full_pos",
                "full_len",
                "confidence",
                "candidate_preview",
            ]
        )
        for team in teams:
            preview = " | ".join(c.text for c in team.candidates[:4])
            writer.writerow(
                [
                    team.index,
                    team.start,
                    team.size,
                    team.uid_hex,
                    team.team_name,
                    team.team_pos,
                    team.team_len,
                    team.stadium_name,
                    team.stadium_pos,
                    team.stadium_len,
                    team.full_name,
                    team.full_pos,
                    team.full_len,
                    f"{team.confidence:.2f}",
                    preview,
                ]
            )

    print(f"Extracted teams: {len(teams)}")
    print(f"JSON: {json_path}")
    print(f"CSV : {csv_path}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pkf", type=Path, default=DEFAULT_PKF, help="Path to EQUIPOS.PKF")
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path(__file__).resolve().parent / "out",
        help="Output directory",
    )
    parser.add_argument(
        "--max-scan",
        type=int,
        default=260,
        help="Scan length per team chunk to detect text candidates",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    teams = extract(args.pkf, max_scan=args.max_scan)
    write_outputs(teams, args.out_dir)


if __name__ == "__main__":
    main()

#!/usr/bin/env python
"""Fetch Transfermarkt competition and squad data for a season."""

from __future__ import annotations

import argparse
import json
import re
import time
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Dict, List, Optional
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup, Tag


BASE_URL = "https://www.transfermarkt.com"
DEFAULT_COMPETITIONS = ["ES1", "ES2"]
DEFAULT_SEASON = 2025  # season 2025/2026

COMPETITION_SLUGS = {
    "ES1": "laliga",
    "ES2": "laliga2",
    "GB1": "premier-league",
    "IT1": "serie-a",
    "FR1": "ligue-1",
    "L1": "bundesliga",
}

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36"
    ),
    "Accept-Language": "en-US,en;q=0.9",
}


@dataclass
class Player:
    name: str
    position: str
    age: Optional[int]
    market_value_text: str
    market_value_eur: Optional[float]


@dataclass
class Team:
    competition: str
    team_name: str
    team_url: str
    team_market_value_text: str
    team_market_value_eur: Optional[float]
    stadium_name: str
    stadium_capacity: Optional[int]
    squad: List[Player]
    squad_total_value_eur: float


def parse_money_to_eur(text: str) -> Optional[float]:
    if not text:
        return None
    raw = text.strip().replace(",", ".").replace(" ", "")
    if "€" not in raw and "â‚¬" not in raw:
        return None

    m = re.search(r"(?:€|â‚¬)([0-9]+(?:\.[0-9]+)?)(bn|m|k)?", raw, flags=re.IGNORECASE)
    if not m:
        return None
    value = float(m.group(1))
    unit = (m.group(2) or "").lower()

    if unit == "bn":
        return value * 1_000_000_000
    if unit == "m":
        return value * 1_000_000
    if unit == "k":
        return value * 1_000
    return value

def text_or_empty(node: Optional[Tag]) -> str:
    if node is None:
        return ""
    return node.get_text(" ", strip=True)


def get_soup(session: requests.Session, url: str) -> BeautifulSoup:
    res = session.get(url, timeout=30)
    res.raise_for_status()
    return BeautifulSoup(res.text, "html.parser")


def build_competition_url(comp: str, season: int) -> str:
    slug = COMPETITION_SLUGS.get(comp.upper(), comp.lower())
    return f"{BASE_URL}/{slug}/startseite/wettbewerb/{comp}?saison_id={season}"


def extract_team_market_value(row: Tag) -> str:
    cells = [c.get_text(" ", strip=True) for c in row.select("td")]
    for value in reversed(cells):
        if "â‚¬" in value or "€" in value:
            return value
    return ""

def parse_competition_teams(soup: BeautifulSoup, competition: str) -> List[Dict[str, str]]:
    teams: List[Dict[str, str]] = []
    for row in soup.select("table.items tbody tr"):
        classes = row.get("class", [])
        if not any(c in ("odd", "even") for c in classes):
            continue

        anchor = row.select_one("td.no-border-links.hauptlink a")
        if anchor is None:
            anchor = row.select_one("td.hauptlink a")
        if anchor is None or not anchor.get("href"):
            continue

        href = anchor["href"].split("?")[0]
        # Keep only actual club rows.
        if "/startseite/verein/" not in href:
            continue

        name = (anchor.get("title") or anchor.get_text(" ", strip=True)).strip()
        team_url = urljoin(BASE_URL, href)
        mv_text = extract_team_market_value(row)
        teams.append(
            {
                "competition": competition,
                "team_name": name,
                "team_url": team_url,
                "team_market_value_text": mv_text,
            }
        )
    return teams


def build_squad_url(team_url: str, season: int) -> str:
    m = re.search(r"/([^/]+)/startseite/verein/([0-9]+)", team_url)
    if not m:
        return team_url
    slug, club_id = m.group(1), m.group(2)
    return f"{BASE_URL}/{slug}/kader/verein/{club_id}/saison_id/{season}/plus/1"


def parse_stadium_meta(soup: BeautifulSoup) -> tuple[str, Optional[int]]:
    stadium_name = ""
    capacity: Optional[int] = None

    # New layout: list entries with labels and values.
    for li in soup.select("li.data-header__label"):
        txt = li.get_text(" ", strip=True)
        if "stadium" in txt.lower():
            # Keep text after the first colon if available.
            if ":" in txt:
                value = txt.split(":", 1)[1].strip()
            else:
                value = txt

            cap_match = re.search(r"([0-9][0-9\.\,]+)\s*(seats|seat|places)?", value, re.I)
            if cap_match:
                try:
                    capacity = int(re.sub(r"[^0-9]", "", cap_match.group(1)))
                except ValueError:
                    capacity = None
                value = value[: cap_match.start()].strip(" -")

            stadium_name = value
            break

    # Fallback for older layout.
    if not stadium_name:
        header = soup.select_one("div.data-header")
        if header:
            txt = header.get_text(" ", strip=True)
            m = re.search(r"Stadium:\s*([A-Za-z0-9Ã€-Ã¿ \-\'\.\(\)&]+)", txt)
            if m:
                stadium_name = m.group(1).strip()

            if capacity is None:
                c = re.search(r"([0-9][0-9\.\,]+)\s*seats", txt, re.I)
                if c:
                    try:
                        capacity = int(re.sub(r"[^0-9]", "", c.group(1)))
                    except ValueError:
                        capacity = None

    return stadium_name, capacity


def parse_squad_players(soup: BeautifulSoup) -> List[Player]:
    players: List[Player] = []
    rows = soup.select("table.items tbody tr")
    for row in rows:
        classes = row.get("class", [])
        if not any(c in ("odd", "even") for c in classes):
            continue

        name_node = row.select_one("td.posrela td.hauptlink a")
        if name_node is None:
            name_node = row.select_one("td.hauptlink a")
        name = text_or_empty(name_node)
        if not name:
            continue

        pos_node = row.select_one("td.posrela table.inline-table tr:nth-of-type(2) td")
        position = text_or_empty(pos_node)

        age: Optional[int] = None
        cells = row.select("td")

        # Usual Transfermarkt layout: birth date + "(age)" in column 6.
        if len(cells) > 5:
            birth_col = cells[5].get_text(" ", strip=True)
            m_age = re.search(r"\((\d{1,2})\)", birth_col)
            if m_age:
                val = int(m_age.group(1))
                if 14 <= val <= 45:
                    age = val

        # Fallback if page layout changes.
        if age is None:
            for n in row.select("td.zentriert"):
                txt = n.get_text(" ", strip=True)
                m_age = re.search(r"\((\d{1,2})\)", txt)
                if not m_age:
                    continue
                val = int(m_age.group(1))
                if 14 <= val <= 45:
                    age = val
                    break

        mv_text = ""
        mv_cells = row.select("td.rechts.hauptlink")
        for cell in reversed(mv_cells):
            txt = cell.get_text(" ", strip=True)
            if "â‚¬" in txt or "€" in txt:
                mv_text = txt
                break
        mv_eur = parse_money_to_eur(mv_text)
        players.append(
            Player(
                name=name,
                position=position,
                age=age,
                market_value_text=mv_text,
                market_value_eur=mv_eur,
            )
        )
    return players

def fetch_team(session: requests.Session, team_row: Dict[str, str], season: int, sleep_sec: float) -> Team:
    squad_url = build_squad_url(team_row["team_url"], season)
    soup = get_soup(session, squad_url)
    stadium_name, stadium_capacity = parse_stadium_meta(soup)
    squad = parse_squad_players(soup)
    squad_total = sum(p.market_value_eur or 0.0 for p in squad)

    team = Team(
        competition=team_row["competition"],
        team_name=team_row["team_name"],
        team_url=team_row["team_url"],
        team_market_value_text=team_row["team_market_value_text"],
        team_market_value_eur=parse_money_to_eur(team_row["team_market_value_text"]),
        stadium_name=stadium_name,
        stadium_capacity=stadium_capacity,
        squad=squad,
        squad_total_value_eur=squad_total,
    )

    if sleep_sec > 0:
        time.sleep(sleep_sec)
    return team


def fetch_all(competitions: List[str], season: int, sleep_sec: float) -> Dict[str, object]:
    session = requests.Session()
    session.headers.update(HEADERS)

    all_teams: List[Team] = []
    for comp in competitions:
        url = build_competition_url(comp, season)
        soup = get_soup(session, url)
        rows = parse_competition_teams(soup, comp)
        print(f"[{comp}] teams on page: {len(rows)}")
        for n, row in enumerate(rows, start=1):
            print(f"  - ({n}/{len(rows)}) {row['team_name']}")
            try:
                team = fetch_team(session, row, season, sleep_sec=sleep_sec)
                all_teams.append(team)
            except Exception as exc:  # noqa: BLE001
                print(f"    ! failed: {exc}")

    payload = {
        "source": "transfermarkt",
        "season": season,
        "competitions": competitions,
        "teams": [asdict(t) for t in all_teams],
    }
    return payload


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--competitions",
        nargs="+",
        default=DEFAULT_COMPETITIONS,
        help="Transfermarkt competition codes, e.g. ES1 ES2",
    )
    parser.add_argument(
        "--season",
        type=int,
        default=DEFAULT_SEASON,
        help="Transfermarkt season id (2025 means 2025/2026)",
    )
    parser.add_argument(
        "--sleep-sec",
        type=float,
        default=0.35,
        help="Delay between team requests",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path(__file__).resolve().parent / "out" / "transfermarkt_teams.json",
        help="Output JSON path",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    payload = fetch_all(args.competitions, args.season, args.sleep_sec)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()


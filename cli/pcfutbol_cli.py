#!/usr/bin/env python3
"""
PC FÃºtbol 5 â€” Clon CLI (Python)
Temporada 2025/26  Â·  Datos reales extraÃ­dos del juego original

Uso:
    python pcfutbol_cli.py

Requiere:
    Python 3.9+  (stdlib sÃ³lo, sin dependencias externas)
"""

import csv
import io
import json
import math
import os
import random
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

# Forzar UTF-8 en Windows para caracteres especiales
if sys.platform == "win32":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
def _resolve_assets_dir() -> Path:
    here = Path(__file__).resolve()
    candidates = [
        here.parent.parent / "android" / "core" / "data" / "src" / "main" / "assets",
        here.parent.parent / "core" / "data" / "src" / "main" / "assets",
    ]
    for candidate in candidates:
        if (candidate / "pcf55_players_2526.csv").exists() and (candidate / "pcf55_teams_extracted.json").exists():
            return candidate
    return candidates[0]


ASSETS_DIR = _resolve_assets_dir()
PLAYERS_CSV  = ASSETS_DIR / "pcf55_players_2526.csv"
TEAMS_JSON   = ASSETS_DIR / "pcf55_teams_extracted.json"

# ---------------------------------------------------------------------------
# ConfiguraciÃ³n de competiciones (cÃ³digo CSV â†’ metadatos)
# Solo se cargan competiciones con â‰¥14 equipos en el CSV
# ---------------------------------------------------------------------------
COMP_INFO: dict[str, dict] = {
    "ES1":  {"name": "Primera DivisiÃ³n",       "country": "EspaÃ±a",      "n_rel": 3, "tot_md": 38, "min_teams": 14},
    "ES2":  {"name": "Segunda DivisiÃ³n",        "country": "EspaÃ±a",      "n_rel": 4, "tot_md": 42, "min_teams": 14},
    "E3G1": {"name": "1Âª RFEF Grupo 1",        "country": "EspaÃ±a",      "n_rel": 4, "tot_md": 38, "min_teams": 14},
    "E3G2": {"name": "1Âª RFEF Grupo 2",        "country": "EspaÃ±a",      "n_rel": 4, "tot_md": 38, "min_teams": 14},
    "GB1":  {"name": "Premier League",          "country": "Inglaterra",  "n_rel": 3, "tot_md": 38, "min_teams": 14},
    "IT1":  {"name": "Serie A",                 "country": "Italia",      "n_rel": 3, "tot_md": 38, "min_teams": 14},
    "L1":   {"name": "Bundesliga",              "country": "Alemania",    "n_rel": 2, "tot_md": 34, "min_teams": 14},
    "FR1":  {"name": "Ligue 1",                 "country": "Francia",     "n_rel": 3, "tot_md": 34, "min_teams": 14},
    "NL1":  {"name": "Eredivisie",              "country": "PaÃ­ses Bajos","n_rel": 2, "tot_md": 34, "min_teams": 14},
    "PO1":  {"name": "Primeira Liga",           "country": "Portugal",    "n_rel": 2, "tot_md": 34, "min_teams": 14},
    "BE1":  {"name": "Pro League",              "country": "BÃ©lgica",     "n_rel": 2, "tot_md": 30, "min_teams": 14},
    "TR1":  {"name": "SÃ¼per Lig",              "country": "TurquÃ­a",     "n_rel": 3, "tot_md": 34, "min_teams": 14},
}

# ---------------------------------------------------------------------------
# Data models
# ---------------------------------------------------------------------------
@dataclass
class Player:
    slot_id:   int
    team_name: str
    comp:      str
    citizenship: str
    name:      str
    position:  str
    age:       int
    market_value: int
    ve: int   # velocidad
    re: int   # resistencia
    ag: int   # agresividad
    ca: int   # calidad
    me: int   # media (calculada)
    portero: int
    entrada: int
    regate:  int
    remate:  int
    pase:    int
    tiro:    int
    estado_forma: int = 50
    moral: int = 50

    @property
    def overall(self) -> int:
        return self.me

    @property
    def is_goalkeeper(self) -> bool:
        return self.position in ("Goalkeeper",)

    def attack_strength(self) -> float:
        return (self.remate * 0.35 + self.regate * 0.25 + self.tiro * 0.2 +
                self.pase * 0.1 + self.ve * 0.1)

    def defense_strength(self) -> float:
        return (self.entrada * 0.4 + self.re * 0.25 + self.ag * 0.2 +
                self.ca * 0.15)

    def gk_strength(self) -> float:
        return (self.portero * 0.6 + self.re * 0.2 + self.ag * 0.1 + self.ca * 0.1)


@dataclass
class Team:
    slot_id:   int
    name:      str
    comp:      str            # ES1 = LIGA1, ES2 = LIGA2
    players:   list[Player] = field(default_factory=list)

    @property
    def competition(self) -> str:
        return "LIGA1" if self.comp == "ES1" else "LIGA2"

    def strength(self) -> float:
        """Fuerza global con la misma fórmula base que el motor Android."""
        return _calc_team_strength_android(self, tactic=None, is_home=False)


@dataclass
class Standing:
    team:   Team
    played: int = 0
    won:    int = 0
    drawn:  int = 0
    lost:   int = 0
    gf:     int = 0
    ga:     int = 0

    @property
    def points(self) -> int:
        return self.won * 3 + self.drawn

    @property
    def gd(self) -> int:
        return self.gf - self.ga

    def sort_key(self):
        return (-self.points, -self.gd, -self.gf)


@dataclass
class MatchResult:
    home: Team
    away: Team
    home_goals: int
    away_goals: int
    matchday: int

    def __str__(self) -> str:
        hg = str(self.home_goals).rjust(2)
        ag = str(self.away_goals).ljust(2)
        return f"  {self.home.name:<22} {hg} - {ag} {self.away.name}"


# ---------------------------------------------------------------------------
# Data loading
# ---------------------------------------------------------------------------

def load_teams() -> dict[str, Team]:
    """Carga equipos de todas las competiciones definidas en COMP_INFO."""
    teams: dict[str, Team] = {}  # key = slot_id str
    players_by_team: dict[str, list[Player]] = {}

    def infer_country_from_comp(comp_code: str) -> str:
        if comp_code in ("ES1", "ES2", "E3G1", "E3G2"):
            return "ES"
        mapping = {
            "GB1": "GB",
            "IT1": "IT",
            "L1": "DE",
            "FR1": "FR",
            "NL1": "NL",
            "PO1": "PT",
            "BE1": "BE",
            "TR1": "TR",
        }
        return mapping.get(comp_code, "")

    if not PLAYERS_CSV.exists():
        print(f"[ERROR] No se encuentra el CSV: {PLAYERS_CSV}")
        sys.exit(1)

    with open(PLAYERS_CSV, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            comp = row["competition"]
            if comp not in COMP_INFO:
                continue
            slot = int(row["teamSlotId"])
            key  = str(slot)

            if key not in teams:
                teams[key] = Team(slot_id=slot, name=row["teamName"], comp=comp)
                players_by_team[key] = []

            try:
                citizenship = str(row.get("citizenship", "")).upper()[:2]
                if not citizenship:
                    citizenship = infer_country_from_comp(comp)
                p = Player(
                    slot_id   = slot,
                    team_name = row["teamName"],
                    comp      = comp,
                    citizenship = citizenship,
                    name      = row["playerName"],
                    position  = row["position"],
                    age       = int(row["age"]),
                    market_value = int(row["marketValueEur"]),
                    ve = int(row["VE"]),
                    re = int(row["RE"]),
                    ag = int(row["AG"]),
                    ca = int(row["CA"]),
                    me = int(row["ME"]),
                    portero = int(row["PORTERO"]),
                    entrada = int(row["ENTRADA"]),
                    regate  = int(row["REGATE"]),
                    remate  = int(row["REMATE"]),
                    pase    = int(row["PASE"]),
                    tiro    = int(row["TIRO"]),
                )
                players_by_team[key].append(p)
            except (ValueError, KeyError):
                continue

    for key, team in teams.items():
        team.players = players_by_team.get(key, [])

    return teams


# ---------------------------------------------------------------------------
# Match simulator  (Poisson-based, seed-deterministic)
# ---------------------------------------------------------------------------

_MASK_32 = 0xFFFFFFFF
_INT_32_MIN = -0x80000000


def _int32(value: int) -> int:
    value &= _MASK_32
    return value if value < 0x80000000 else value - 0x100000000


def _ushr32(value: int, bits: int) -> int:
    return (value & _MASK_32) >> bits


class KotlinXorWowRandom:
    """
    Implementación Python de kotlin.random.Random(seed) (XorWowRandom),
    para reproducir seeds del motor Android.
    """
    def __init__(self, seed: int):
        s0 = _int32(seed)
        s1 = _int32(seed >> 32)
        self.x = _int32(s0)
        self.y = _int32(s1)
        self.z = 0
        self.w = 0
        self.v = _int32(s0 ^ -1)
        self.addend = _int32((s0 << 10) ^ _ushr32(s1, 4))
        if (self.x | self.y | self.z | self.w | self.v) == 0:
            raise ValueError("Initial state must have at least one non-zero element.")
        for _ in range(64):
            self.next_int_raw()

    def next_int_raw(self) -> int:
        t = _int32(self.x)
        t = _int32(t ^ _ushr32(t, 2))
        self.x = self.y
        self.y = self.z
        self.z = self.w
        vv = self.v
        self.w = vv
        t = _int32(t ^ _int32(t << 1) ^ vv ^ _int32(vv << 4))
        self.v = _int32(t)
        self.addend = _int32(self.addend + 362437)
        return _int32(self.v + self.addend)

    def next_bits(self, bit_count: int) -> int:
        if bit_count <= 0:
            return 0
        return _ushr32(self.next_int_raw(), 32 - bit_count)

    def next_boolean(self) -> bool:
        return self.next_bits(1) != 0

    def next_double(self) -> float:
        hi = self.next_bits(26)
        lo = self.next_bits(27)
        return float((hi << 27) + lo) / 9007199254740992.0

    def next_int(self, bound: int) -> int:
        return self.next_int_range(0, bound)

    def next_int_range(self, from_inclusive: int, until_exclusive: int) -> int:
        if not until_exclusive > from_inclusive:
            raise ValueError("Random range is empty.")
        n = _int32(until_exclusive - from_inclusive)
        if n > 0 or n == _INT_32_MIN:
            if n > 0 and (n & -n) == n:
                v = self.next_bits(n.bit_length() - 1)
            else:
                while True:
                    bits = _ushr32(self.next_int_raw(), 1)
                    v = bits % n
                    if bits - v + (n - 1) >= 0:
                        break
            return _int32(from_inclusive + v)

        while True:
            v = self.next_int_raw()
            if from_inclusive <= v < until_exclusive:
                return v


def _match_squad(team: Team) -> list[Player]:
    # Android toma los 11 "mejores" por CA en runtime.
    return sorted(team.players, key=lambda p: (p.ca, p.me), reverse=True)[:11]


def _safe_avg(values: list[float], fallback: float = 50.0) -> float:
    if not values:
        return fallback
    return sum(values) / len(values)


def _form_factor(player: Player) -> float:
    return (player.estado_forma - 50) * 0.05


def _runtime_bonus(player: Player) -> float:
    return (player.estado_forma - 50) * 0.02 + ((player.moral - 50) / 100.0) * 2.0


def _gk_strength_android(player: Player) -> float:
    return player.portero * 0.6 + player.re * 0.2 + player.ca * 0.2 + _form_factor(player)


def _def_strength_android(player: Player) -> float:
    return player.entrada * 0.4 + player.ca * 0.3 + player.ve * 0.2 + player.re * 0.1 + _form_factor(player)


def _mid_strength_android(player: Player) -> float:
    return player.pase * 0.35 + player.ca * 0.3 + player.re * 0.2 + player.tiro * 0.15 + _form_factor(player)


def _fwd_strength_android(player: Player) -> float:
    return player.remate * 0.4 + player.regate * 0.25 + player.ca * 0.2 + player.tiro * 0.15 + _form_factor(player)


def _calc_team_strength_android(team: Team, tactic: Optional[dict], is_home: bool) -> float:
    squad = _match_squad(team)
    if not squad:
        return 50.0

    gk = squad[0]
    defenders = squad[1:5]
    mids = squad[5:9]
    fwds = squad[9:11]

    gk_score = _gk_strength_android(gk)
    def_score = _safe_avg([_def_strength_android(p) for p in defenders])
    mid_score = _safe_avg([_mid_strength_android(p) for p in mids])
    fwd_score = _safe_avg([_fwd_strength_android(p) for p in fwds])

    base = gk_score * 0.15 + def_score * 0.3 + mid_score * 0.3 + fwd_score * 0.25
    tactical_bonus = _tactic_adj(tactic, is_home=is_home)
    runtime = _safe_avg([_runtime_bonus(p) for p in squad], fallback=0.0)
    return max(10.0, min(99.0, base + tactical_bonus + runtime))


def _strength_to_lambda(strength: float, is_home: bool) -> float:
    norm = (strength - 10.0) / 89.0
    # Calibracion realista (5 ultimas temporadas principales):
    # objetivo global ~2.4-2.9 goles por partido.
    lam = 0.29 + norm * 1.78
    return lam * 1.08 if is_home else lam


def _apply_expulsion_penalty(lam: float, red_cards: int) -> float:
    if red_cards <= 0:
        return lam
    return max(0.1, lam * 0.8)


def _pace_factors(home_tactic: Optional[dict], away_tactic: Optional[dict]) -> tuple[float, float]:
    home_waste = int((home_tactic or {}).get("perdidaTiempo", 0)) == 1
    away_waste = int((away_tactic or {}).get("perdidaTiempo", 0)) == 1
    if home_waste and away_waste:
        return 0.82, 0.82
    if home_waste:
        return 0.88, 0.92
    if away_waste:
        return 0.92, 0.88
    return 1.0, 1.0


_GOAL_FACTOR_BY_COMP: dict[str, float] = {
    # Calibrado sobre medias recientes de ligas reales (5 temporadas).
    "ES1": 0.93,
    "ES2": 0.81,
    "GB1": 1.08,
    "IT1": 1.02,
    "L1": 1.15,
    "FR1": 1.02,
    "NL1": 1.12,
    "PO1": 0.96,
    "BE1": 1.08,
    "TR1": 1.04,
}


def _competition_goal_factor(comp_code: str) -> float:
    return _GOAL_FACTOR_BY_COMP.get(str(comp_code), 1.0)


def _pick_eligible_slot(lineup_size: int, dismissed: set[int], rng: KotlinXorWowRandom) -> Optional[int]:
    if lineup_size <= 0:
        return None
    eligible = [idx for idx in range(lineup_size) if idx not in dismissed]
    if not eligible:
        return None
    return eligible[rng.next_int(len(eligible))]


def _discipline_red_cards(
    home_lineup: list[Player],
    away_lineup: list[Player],
    home_tactic: Optional[dict],
    away_tactic: Optional[dict],
    rng: KotlinXorWowRandom,
) -> tuple[int, int]:
    home_yellows = [0] * len(home_lineup)
    away_yellows = [0] * len(away_lineup)
    home_dismissed: set[int] = set()
    away_dismissed: set[int] = set()
    home_red = 0
    away_red = 0

    yellow_count = _poisson_goals(1.5, rng) + _poisson_goals(1.5, rng)
    for _ in range(yellow_count):
        if rng.next_boolean():
            slot = _pick_eligible_slot(len(home_lineup), home_dismissed, rng)
            if slot is None:
                continue
            home_yellows[slot] += 1
            if home_yellows[slot] >= 2:
                home_red += 1
                home_dismissed.add(slot)
        else:
            slot = _pick_eligible_slot(len(away_lineup), away_dismissed, rng)
            if slot is None:
                continue
            away_yellows[slot] += 1
            if away_yellows[slot] >= 2:
                away_red += 1
                away_dismissed.add(slot)

    if int((home_tactic or {}).get("faltas", 2)) == 3 and rng.next_double() < 0.05:
        slot = _pick_eligible_slot(len(home_lineup), home_dismissed, rng)
        if slot is not None:
            home_red += 1
            home_dismissed.add(slot)

    if int((away_tactic or {}).get("faltas", 2)) == 3 and rng.next_double() < 0.05:
        slot = _pick_eligible_slot(len(away_lineup), away_dismissed, rng)
        if slot is not None:
            away_red += 1
            away_dismissed.add(slot)

    return home_red, away_red


def _apply_var_to_goals(goals: int, rng: KotlinXorWowRandom) -> int:
    remaining = goals
    for _ in range(goals):
        if rng.next_double() < 0.18 and rng.next_double() < 0.38:
            remaining -= 1
    return max(0, remaining)

def _poisson_goals(lam: float, rng: KotlinXorWowRandom) -> int:
    """Poisson con Knuth, alineado con MatchSimulator Android."""
    L   = math.exp(-lam)
    k   = 0
    p   = 1.0
    while True:
        k += 1
        p *= rng.next_double()
        if p <= L:
            return max(0, min(8, k - 1))


def _tactic_adj(tactic: Optional[dict], is_home: bool) -> float:
    """Misma fórmula que StrengthCalculator.tacticalAdjustment()."""
    t = tactic or {}
    adj = 3.0 if is_home else 0.0
    tj = int(t.get("tipoJuego", 2))
    if tj == 3:
        adj += 1.5
    elif tj == 1:
        adj -= 1.0
    tp = int(t.get("tipoPresion", 2))
    if tp == 3:
        adj += 1.0
    elif tp == 1:
        adj -= 0.5
    if int(t.get("tipoMarcaje", 1)) == 1:
        adj += 0.3
    if int(t.get("faltas", 2)) == 3:
        adj += 0.2
    if int(t.get("porcContra", 30)) > 60:
        adj += 0.3
    if int(t.get("tipoDespejes", 1)) == 2:
        adj += 0.2
    if int(t.get("perdidaTiempo", 0)) == 1:
        adj -= 0.4
    return adj


def simulate_match(home: Team, away: Team, seed: int,
                   home_tactic: Optional[dict] = None,
                   away_tactic: Optional[dict] = None) -> tuple[int, int]:
    """
    Simulador determinista alineado con Android:
    - StrengthCalculator (misma fórmula)
    - MatchSimulator (Poisson + VAR + rojas + pérdida de tiempo)
    """
    rng = KotlinXorWowRandom(int(seed))
    home_t = home_tactic or {}
    away_t = away_tactic or {}

    home_strength = _calc_team_strength_android(home, home_t, is_home=True)
    away_strength = _calc_team_strength_android(away, away_t, is_home=False)
    home_lineup = _match_squad(home)
    away_lineup = _match_squad(away)
    home_red, away_red = _discipline_red_cards(home_lineup, away_lineup, home_t, away_t, rng)
    comp_factor = (_competition_goal_factor(home.comp) + _competition_goal_factor(away.comp)) * 0.5

    home_pace, away_pace = _pace_factors(home_t, away_t)
    home_lambda = max(
        0.1,
        _apply_expulsion_penalty(_strength_to_lambda(home_strength, True), home_red) * home_pace * comp_factor,
    )
    away_lambda = max(
        0.1,
        _apply_expulsion_penalty(_strength_to_lambda(away_strength, False), away_red) * away_pace * comp_factor,
    )

    home_raw = _poisson_goals(home_lambda, rng)
    away_raw = _poisson_goals(away_lambda, rng)
    return _apply_var_to_goals(home_raw, rng), _apply_var_to_goals(away_raw, rng)


# ---------------------------------------------------------------------------
# Fixture generator  (round-robin doble vuelta)
# ---------------------------------------------------------------------------

def generate_fixtures(teams: list[Team]) -> list[tuple[Team, Team]]:
    """Genera calendario con el mismo algoritmo de rotación del engine Android."""
    if len(teams) < 2:
        return []
    rotation = list(teams)
    if len(rotation) % 2 != 0:
        rotation.append(Team(slot_id=-1, name="BYE", comp=""))

    n = len(rotation)
    rounds = n - 1
    matches_per_round = n // 2
    fixtures: list[tuple[Team, Team]] = []

    for _ in range(rounds):
        for m in range(matches_per_round):
            home = rotation[m]
            away = rotation[n - 1 - m]
            if home.slot_id != -1 and away.slot_id != -1:
                fixtures.append((home, away))
        last = rotation.pop()
        rotation.insert(1, last)

    first_leg = list(fixtures)
    fixtures.extend((away, home) for home, away in first_leg)
    return fixtures


# ---------------------------------------------------------------------------
# Season simulation
# ---------------------------------------------------------------------------

def simulate_season(
    teams: list[Team],
    competition: str,
    seed_base: int = 12345,
    silent: bool = False,
) -> list[Standing]:
    """Simula temporada completa con seed por fixture (masterSeed XOR fixtureId)."""
    fixtures = generate_fixtures(teams)
    standings = {t.slot_id: Standing(team=t) for t in teams}
    results: list[MatchResult] = []
    fixtures_per_md = max(1, len(teams) // 2)
    matchday = 1

    for i, (home, away) in enumerate(fixtures):
        if i > 0 and i % fixtures_per_md == 0:
            matchday += 1
        fixture_id = i + 1
        seed = int(seed_base) ^ fixture_id
        hg, ag = simulate_match(home, away, seed)

        result = MatchResult(home=home, away=away,
                             home_goals=hg, away_goals=ag, matchday=matchday)
        results.append(result)

        sh = standings[home.slot_id]
        sa = standings[away.slot_id]
        sh.played += 1; sa.played += 1
        sh.gf += hg;    sh.ga += ag
        sa.gf += ag;    sa.ga += hg
        if hg > ag:
            sh.won += 1; sa.lost += 1
        elif ag > hg:
            sa.won += 1; sh.lost += 1
        else:
            sh.drawn += 1; sa.drawn += 1

    sorted_standings = sorted(standings.values(), key=lambda s: s.sort_key())
    return sorted_standings, results


# ---------------------------------------------------------------------------
# Display helpers
# ---------------------------------------------------------------------------

CYAN   = "\033[96m"
YELLOW = "\033[93m"
GREEN  = "\033[92m"
RED    = "\033[91m"
GRAY   = "\033[90m"
BOLD   = "\033[1m"
RESET  = "\033[0m"

def _c(color: str, text: str) -> str:
    return f"{color}{text}{RESET}"


def print_standings(standings: list[Standing], title: str, relegated_from: int = 0, promoted_to: int = 0):
    print()
    print(_c(BOLD + YELLOW, f"  â•â•â• {title} â•â•â•"))
    print(_c(GRAY, f"  {'#':>3}  {'EQUIPO':<24} {'PJ':>3} {'G':>3} {'E':>3} {'P':>3} {'GF':>3} {'GC':>3} {'DG':>4} {'PTS':>4}"))
    print(_c(GRAY, "  " + "â”€" * 62))

    for pos, st in enumerate(standings, 1):
        name  = st.team.name[:23]
        pts   = str(st.points)
        gd    = f"{st.gd:+d}"

        if pos == 1:
            color = GREEN
        elif promoted_to and pos <= promoted_to:
            color = CYAN
        elif relegated_from and pos > len(standings) - relegated_from:
            color = RED
        else:
            color = RESET

        row = (f"  {pos:>3}  {name:<24} {st.played:>3} {st.won:>3} "
               f"{st.drawn:>3} {st.lost:>3} {st.gf:>3} {st.ga:>3} {gd:>4} {pts:>4}")
        print(color + row + RESET)

    print()


def print_matchday(results: list[MatchResult], matchday: int, team_filter: Optional[Team] = None):
    md_results = [r for r in results if r.matchday == matchday]
    if team_filter:
        md_results = [r for r in md_results
                      if r.home == team_filter or r.away == team_filter]

    if not md_results:
        print(_c(GRAY, f"  Sin partidos en jornada {matchday}."))
        return

    print(_c(YELLOW, f"\n  â”€â”€â”€ Jornada {matchday} â”€â”€â”€"))
    for r in md_results:
        if r.home_goals > r.away_goals:
            score_color = GREEN
        elif r.away_goals > r.home_goals:
            score_color = RED
        else:
            score_color = GRAY
        print(score_color + str(r) + RESET)
    print()


def print_squad(team: Team):
    print(_c(BOLD + YELLOW, f"\n  â•â•â• PLANTILLA: {team.name} â•â•â•"))
    print(_c(GRAY, f"  {'#':<3} {'JUGADOR':<26} {'POS':<18} {'ED':>3} {'ME':>3} {'VE':>3} {'RE':>3} {'CA':>3} {'VALOR':<12}"))
    print(_c(GRAY, "  " + "â”€" * 78))

    sorted_players = sorted(team.players, key=lambda p: p.overall, reverse=True)
    for i, p in enumerate(sorted_players, 1):
        val = f"{p.market_value // 1_000_000}M" if p.market_value >= 1_000_000 else f"{p.market_value // 1000}K"
        name = p.name[:25]
        pos  = p.position[:17]
        print(f"  {i:<3} {name:<26} {pos:<18} {p.age:>3} {p.me:>3} {p.ve:>3} {p.re:>3} {p.ca:>3} {val:<12}")
    print()


# ---------------------------------------------------------------------------
# CLI menus
# ---------------------------------------------------------------------------

def input_int(prompt: str, min_val: int, max_val: int) -> int:
    while True:
        try:
            val = int(input(prompt).strip())
            if min_val <= val <= max_val:
                return val
            print(f"  Introduce un nÃºmero entre {min_val} y {max_val}.")
        except ValueError:
            print("  NÃºmero invÃ¡lido.")
        except (EOFError, KeyboardInterrupt):
            sys.exit(0)


def input_str(prompt: str) -> str:
    try:
        return input(prompt).strip()
    except (EOFError, KeyboardInterrupt):
        sys.exit(0)


def select_team(teams_list: list[Team], prompt: str = "Elige equipo") -> Team:
    for i, t in enumerate(teams_list, 1):
        print(f"  {i:>3}. {t.name}")
    idx = input_int(f"\n  {prompt} (1-{len(teams_list)}): ", 1, len(teams_list))
    return teams_list[idx - 1]


def menu_simulate_season(liga1: list[Team], liga2: list[Team]):
    print(_c(BOLD + CYAN, "\n  â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"))
    print(_c(BOLD + CYAN,   "     SIMULAR TEMPORADA 2025/26"))
    print(_c(BOLD + CYAN,   "  â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"))

    seed = int.from_bytes(os.urandom(4), "little")

    print(_c(YELLOW, "\n  Simulando LIGA1 (Primera DivisiÃ³n)..."))
    liga1_standings, liga1_results = simulate_season(liga1, "LIGA1", seed)

    print(_c(YELLOW, "  Simulando LIGA2 (Segunda DivisiÃ³n)..."))
    liga2_standings, liga2_results = simulate_season(liga2, "LIGA2", seed ^ 0xDEAD)

    # Ascensos/descensos
    relegated   = [s.team for s in liga1_standings[-3:]]
    promoted    = [s.team for s in liga2_standings[:3]]

    print_standings(liga1_standings, "PRIMERA DIVISIÃ“N 2025/26", relegated_from=3)
    print_standings(liga2_standings, "SEGUNDA DIVISIÃ“N 2025/26", promoted_to=3)

    champion = liga1_standings[0]
    print(_c(BOLD + GREEN, f"  ðŸ† CAMPEÃ“N: {champion.team.name} con {champion.points} puntos"))

    print(_c(GREEN, "\n  â¬† Ascienden a Primera:"))
    for t in promoted:
        print(f"    âœ“ {t.name}")

    print(_c(RED, "\n  â¬‡ Descienden a Segunda:"))
    for t in relegated:
        print(f"    âœ— {t.name}")

    # MenÃº de resultados
    while True:
        print(_c(CYAN, "\n  â”Œâ”€ Ver resultados â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”"))
        print(_c(CYAN, "  â”‚ 1. Jornada especÃ­fica LIGA1      â”‚"))
        print(_c(CYAN, "  â”‚ 2. Jornada especÃ­fica LIGA2      â”‚"))
        print(_c(CYAN, "  â”‚ 3. Resultados de un equipo       â”‚"))
        print(_c(CYAN, "  â”‚ 4. Plantilla de un equipo        â”‚"))
        print(_c(CYAN, "  â”‚ 0. Volver                        â”‚"))
        print(_c(CYAN, "  â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜"))

        op = input_int("  OpciÃ³n: ", 0, 4)
        if op == 0:
            break
        elif op == 1:
            md = input_int("  Jornada (1-38): ", 1, 38)
            print_matchday(liga1_results, md)
        elif op == 2:
            md = input_int("  Jornada (1-42): ", 1, 42)
            print_matchday(liga2_results, md)
        elif op == 3:
            all_teams_sorted = sorted(liga1 + liga2, key=lambda t: t.name)
            team = select_team(all_teams_sorted, "Elige equipo")
            comp_results = liga1_results if team.comp == "ES1" else liga2_results
            total_mds = 38 if team.comp == "ES1" else 42
            for md in range(1, total_mds + 1):
                print_matchday(comp_results, md, team_filter=team)
        elif op == 4:
            all_teams_sorted = sorted(liga1 + liga2, key=lambda t: t.name)
            team = select_team(all_teams_sorted, "Elige equipo")
            print_squad(team)


def menu_view_squad(liga1: list[Team], liga2: list[Team]):
    all_teams = sorted(liga1 + liga2, key=lambda t: t.name)
    print(_c(YELLOW, "\n  Equipos disponibles:"))
    team = select_team(all_teams, "Ver plantilla de")
    print_squad(team)


def menu_quick_match(liga1: list[Team], liga2: list[Team]):
    all_teams = sorted(liga1 + liga2, key=lambda t: t.name)
    print(_c(YELLOW, "\n  Selecciona equipo LOCAL:"))
    home = select_team(all_teams)
    print(_c(YELLOW, "\n  Selecciona equipo VISITANTE:"))
    away = select_team(all_teams)
    if away.slot_id == home.slot_id:
        print(_c(RED, "  Error: el equipo local y visitante no pueden ser el mismo.\n"))
        return

    seed = int.from_bytes(os.urandom(4), "little")
    hg, ag = simulate_match(home, away, seed)

    print()
    print(_c(BOLD + YELLOW, "  â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"))
    print(_c(BOLD + YELLOW, "         RESULTADO FINAL"))
    print(_c(BOLD + YELLOW, "  â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"))
    print()
    if hg > ag:
        result_color = GREEN
    elif ag > hg:
        result_color = RED
    else:
        result_color = GRAY

    print(result_color + f"  {home.name:<25} {hg:>2} - {ag:<2} {away.name}" + RESET)
    print()

    hs = home.strength()
    aws = away.strength()
    print(_c(GRAY, f"  Fuerza {home.name[:20]}: {hs:.1f}"))
    print(_c(GRAY, f"  Fuerza {away.name[:20]}: {aws:.1f}"))
    print()


def menu_top_players(liga1: list[Team], liga2: list[Team]):
    all_players = []
    for t in liga1 + liga2:
        all_players.extend(t.players)

    print(_c(CYAN, "\n  Ordenar por:"))
    print("    1. ValoraciÃ³n media (ME)")
    print("    2. Delanteros (remate)")
    print("    3. Porteros")
    print("    4. Defensas")
    print("    5. Centrocampistas (pase)")

    op = input_int("  OpciÃ³n (1-5): ", 1, 5)
    n  = input_int("  Â¿CuÃ¡ntos jugadores? (5-50): ", 5, 50)

    if op == 1:
        top = sorted(all_players, key=lambda p: p.overall, reverse=True)[:n]
    elif op == 2:
        top = sorted([p for p in all_players if "Forward" in p.position or "Winger" in p.position or "Striker" in p.position],
                     key=lambda p: p.remate, reverse=True)[:n]
    elif op == 3:
        top = sorted([p for p in all_players if p.is_goalkeeper],
                     key=lambda p: p.portero, reverse=True)[:n]
    elif op == 4:
        top = sorted([p for p in all_players if "Back" in p.position or "Centre-Back" in p.position],
                     key=lambda p: p.entrada, reverse=True)[:n]
    elif op == 5:
        top = sorted([p for p in all_players if "Midfield" in p.position],
                     key=lambda p: p.pase, reverse=True)[:n]
    else:
        return

    print(_c(BOLD + YELLOW, f"\n  TOP {len(top)} JUGADORES"))
    print(_c(GRAY, f"  {'#':<3} {'JUGADOR':<26} {'EQUIPO':<22} {'POS':<18} {'ME':>3}"))
    print(_c(GRAY, "  " + "â”€" * 74))
    for i, p in enumerate(top, 1):
        print(f"  {i:<3} {p.name[:25]:<26} {p.team_name[:21]:<22} {p.position[:17]:<18} {p.me:>3}")
    print()


def menu_team_strength(liga1: list[Team], liga2: list[Team]):
    print(_c(BOLD + YELLOW, "\n  â•â•â• RANKING DE FORTALEZA â•â•â•"))
    all_teams = sorted(liga1 + liga2, key=lambda t: t.strength(), reverse=True)

    print(_c(GRAY, f"\n  {'#':>3}  {'EQUIPO':<26} {'LIGA':<8} {'FUERZA':>7}"))
    print(_c(GRAY, "  " + "â”€" * 48))
    for i, t in enumerate(all_teams, 1):
        comp = "1Âª DIV" if t.comp == "ES1" else "2Âª DIV"
        bar_len = int(t.strength() / 2)
        bar = "â–ˆ" * bar_len
        if i <= 5:
            color = GREEN
        elif i > len(all_teams) - 5:
            color = RED
        else:
            color = RESET
        print(color + f"  {i:>3}  {t.name:<26} {comp:<8} {t.strength():>7.1f}  {bar}" + RESET)
    print()


REAL_LEAGUES = [
    ("Spanish La Liga",            4335, "LIGA1"),
    ("Spanish La Liga 2",          4400, "LIGA2"),
    ("English Premier League",     4328, "PRML"),
    ("Italian Serie A",            4332, "SERIA"),
    ("French Ligue 1",             4334, "LIG1"),
    ("German Bundesliga",          4331, "BUN1"),
    ("Dutch Eredivisie",           4337, "ERED"),
    ("Portuguese Primeira Liga",   4344, "PRIM"),
    ("Belgian Pro League",         4338, "BELGA"),
    ("Turkish Super Lig",          4339, "SUPERL"),
    ("Scottish Premiership",       4330, "SCOT"),
    ("Russian Premier League",     4355, "RPL"),
    ("Danish Superliga",           4340, "DSL"),
    ("Polish Ekstraklasa",         4422, "EKSTR"),
    ("Austrian Bundesliga",        4621, "ABUND"),
    ("Argentine Primera Division", 4406, "ARGPD"),
    ("Brazilian Serie A",          4351, "BRASEA"),
    ("Mexican Liga MX",            4350, "LIGAMX"),
    ("Saudi Pro League",           4668, "SPL"),
]


def menu_real_football() -> None:
    """Muestra clasificacion y resultados reales de cualquier liga via TheSportsDB."""
    import urllib.request
    import json

    BASE = "https://www.thesportsdb.com/api/v1/json/3"

    while True:
        print(_c(CYAN, "\n=== ACTUALIDAD FUTBOLISTICA ==="))
        for i, (name, _, code) in enumerate(REAL_LEAGUES, 1):
            print(f"  {i:>2}. {name} ({code})")
        print("   0. Volver")

        op = input_int("  Liga: ", 0, len(REAL_LEAGUES))
        if op == 0:
            return

        name, league_id, _ = REAL_LEAGUES[op - 1]
        print(_c(YELLOW, f"\n  {name}"))
        print("  1. Clasificacion")
        print("  2. Ultimos resultados")
        sub = input_int("  Opcion: ", 1, 2)

        try:
            if sub == 1:
                url = f"{BASE}/lookuptable.php?l={league_id}&s=2024-2025"
                with urllib.request.urlopen(url, timeout=8) as r:
                    data = json.loads(r.read())
                rows = data.get("table") or []
                if not rows:
                    print(_c(RED, "  Sin datos para esta liga."))
                else:
                    print(f"\n{'Pos':<4} {'Equipo':<28} {'PJ':>3} {'G':>3} {'E':>3} {'P':>3} {'GF':>4} {'GC':>4} {'Pts':>4}")
                    print("-" * 58)
                    for i, row in enumerate(rows, 1):
                        print(
                            f"{i:<4} {row.get('name', '')[:27]:<28} "
                            f"{row.get('played', '')!s:>3} {row.get('win', '')!s:>3} "
                            f"{row.get('draw', '')!s:>3} {row.get('loss', '')!s:>3} "
                            f"{row.get('goalsfor', '')!s:>4} {row.get('goalsagainst', '')!s:>4} "
                            f"{row.get('total', '')!s:>4}"
                        )
            else:
                url = f"{BASE}/eventspastleague.php?id={league_id}"
                with urllib.request.urlopen(url, timeout=8) as r:
                    data = json.loads(r.read())
                events = (data.get("events") or [])[-15:][::-1]
                if not events:
                    print(_c(RED, "  Sin resultados disponibles."))
                else:
                    print()
                    for ev in events:
                        hs = ev.get("intHomeScore") or "-"
                        aws = ev.get("intAwayScore") or "-"
                        home = ev.get("strHomeTeam", "")[:22]
                        away = ev.get("strAwayTeam", "")[:22]
                        date = ev.get("dateEvent", "")
                        print(f"  {date}  {home:>22} {hs}-{aws}  {away}")
        except Exception as e:
            print(_c(RED, f"  Error de conexion: {e}"))

        _pause()

# ===========================================================================
# PRO MANAGER â€” MODO CARRERA
# ===========================================================================

CAREER_SAVE = Path.home() / ".pcfutbol_career.json"

DEFAULT_STAFF_PROFILE: dict[str, int] = {
    "segundo_entrenador": 50,
    "fisio": 50,
    "psicologo": 50,
    "asistente": 50,
    "secretario": 50,
    "ojeador": 50,
    "juveniles": 50,
    "cuidador": 50,
}

DEFAULT_TRAINING_PLAN: dict[str, str] = {
    "intensity": "MEDIUM",
    "focus": "BALANCED",
}

TRAINING_INTENSITIES = ("LOW", "MEDIUM", "HIGH")
TRAINING_FOCUSES = ("BALANCED", "PHYSICAL", "DEFENSIVE", "TECHNICAL", "ATTACKING")

TRAINING_INTENSITY_LABELS = {
    "LOW": "Suave",
    "MEDIUM": "Media",
    "HIGH": "Alta",
}

TRAINING_FOCUS_LABELS = {
    "BALANCED": "Equilibrado",
    "PHYSICAL": "FÃƒÂ­sico",
    "DEFENSIVE": "Defensivo",
    "TECHNICAL": "TÃƒÂ©cnico",
    "ATTACKING": "Ataque",
}

STAFF_LABELS = {
    "segundo_entrenador": "Segundo entrenador",
    "fisio": "Fisio",
    "psicologo": "PsicÃƒÂ³logo",
    "asistente": "Asistente",
    "secretario": "Secretario",
    "ojeador": "Ojeador",
    "juveniles": "Juv. cantera",
    "cuidador": "Cuidador",
}

MANAGER_PLAY_MODE_LABELS = {
    "BASIC": "Basico",
    "STANDARD": "Estandar",
    "TOTAL": "Total",
}

PRESIDENT_CAP_MODES = {
    "STRICT": 1.05,
    "BALANCED": 1.20,
    "FLEX": 1.45,
}

PRESIDENT_CAP_LABELS = {
    "STRICT": "Estricto",
    "BALANCED": "Equilibrado",
    "FLEX": "Flexible",
}


def _prestige_label(p: int) -> str:
    return "â˜…" * min(p, 5) + "â˜†" * (5 - min(p, 5))


def _pause():
    if sys.stdin.isatty():
        input(_c(GRAY, "  [ENTER para continuar]"))


def _safe_int(value, default: int) -> int:
    try:
        return int(value)
    except Exception:
        return default


def _ensure_manager_play_mode(data: dict) -> str:
    manager = data.setdefault("manager", {})
    if not isinstance(manager, dict):
        manager = {}
        data["manager"] = manager
    raw = str(manager.get("play_mode", "STANDARD")).strip().upper()
    alias = {
        "BASIC": "BASIC",
        "BASICO": "BASIC",
        "STANDARD": "STANDARD",
        "ESTANDAR": "STANDARD",
        "TOTAL": "TOTAL",
    }
    mode = alias.get(raw, "STANDARD")
    manager["play_mode"] = mode
    return mode


def _play_mode_label(mode: str) -> str:
    return MANAGER_PLAY_MODE_LABELS.get(mode, "Estandar")


def _play_mode_allows_coach_match(mode: str) -> bool:
    return mode in ("STANDARD", "TOTAL")


def _play_mode_allows_manager_depth(mode: str) -> bool:
    return mode in ("STANDARD", "TOTAL")


def _play_mode_allows_president(mode: str) -> bool:
    return mode == "TOTAL"


def _play_mode_menu(data: dict, persist: bool = True) -> str:
    current = _ensure_manager_play_mode(data)
    print(_c(BOLD + YELLOW, "\n  ═══ NIVEL DE CONTROL ═══"))
    print(_c(GRAY, f"  Actual: {_play_mode_label(current)}"))
    print()
    print(_c(CYAN, "  1. Basico    (partidas rapidas, menos gestion manual)"))
    print(_c(CYAN, "  2. Estandar  (manager clasico, con tactica y staff)"))
    print(_c(CYAN, "  3. Total     (toda la capa manager/presidencia)"))
    print(_c(CYAN, "  0. Cancelar"))
    op = input_int("  Opcion: ", 0, 3)
    if op == 0:
        return current
    next_mode = {1: "BASIC", 2: "STANDARD", 3: "TOTAL"}[op]
    data.setdefault("manager", {})["play_mode"] = next_mode
    if persist:
        _save_career(data)
    print(_c(GREEN, f"  ✓ Nivel cambiado a {_play_mode_label(next_mode)}.\n"))
    return next_mode


def _ensure_manager_depth(data: dict):
    manager = data.setdefault("manager", {})
    if not isinstance(manager, dict):
        manager = {}
        data["manager"] = manager
    manager.setdefault("name", "Manager")
    manager["prestige"] = max(1, _safe_int(manager.get("prestige", 1), 1))
    manager["total_seasons"] = max(0, _safe_int(manager.get("total_seasons", 0), 0))
    if not isinstance(manager.get("history"), list):
        manager["history"] = []

    raw_staff = manager.get("staff", {})
    if not isinstance(raw_staff, dict):
        raw_staff = {}
    normalized_staff: dict[str, int] = {}
    for key, default in DEFAULT_STAFF_PROFILE.items():
        normalized_staff[key] = max(0, min(100, _safe_int(raw_staff.get(key, default), default)))
    manager["staff"] = normalized_staff

    raw_training = manager.get("training", {})
    if not isinstance(raw_training, dict):
        raw_training = {}
    intensity = str(raw_training.get("intensity", DEFAULT_TRAINING_PLAN["intensity"])).upper()
    focus = str(raw_training.get("focus", DEFAULT_TRAINING_PLAN["focus"])).upper()
    if intensity not in TRAINING_INTENSITIES:
        intensity = DEFAULT_TRAINING_PLAN["intensity"]
    if focus not in TRAINING_FOCUSES:
        focus = DEFAULT_TRAINING_PLAN["focus"]
    manager["training"] = {"intensity": intensity, "focus": focus}
    _ensure_manager_play_mode(data)


def _estimate_weekly_wage_k(player: "Player") -> int:
    # Estimacion simple de masa salarial semanal en miles de euros.
    return max(120, int(player.market_value / 220_000))


def _team_wage_bill_k(team: "Team") -> int:
    return sum(_estimate_weekly_wage_k(p) for p in team.players)


def _ensure_president_profile(data: dict, mgr_team: Optional["Team"] = None) -> dict:
    profile = data.get("president")
    if not isinstance(profile, dict):
        profile = {}
    ownership = str(profile.get("ownership", "SOCIOS")).upper()
    if ownership not in ("SOCIOS", "PRIVATE", "STATE", "LISTED"):
        ownership = "SOCIOS"
    cap_mode = str(profile.get("salary_cap_mode", "BALANCED")).upper()
    if cap_mode not in PRESIDENT_CAP_MODES:
        cap_mode = "BALANCED"
    salary_cap_k = max(0, _safe_int(profile.get("salary_cap_k", 0), 0))
    pressure = max(0, _safe_int(profile.get("pressure", 0), 0))
    investor_rounds = max(0, _safe_int(profile.get("investor_rounds", 0), 0))
    ipo_done = bool(profile.get("ipo_done", False))
    pelotazo_done = bool(profile.get("pelotazo_done", False))
    last_review_md = max(0, _safe_int(profile.get("last_review_md", 0), 0))
    next_review_md = max(1, _safe_int(profile.get("next_review_md", 1), 1))
    last_cap_penalty_md = _safe_int(profile.get("last_cap_penalty_md", -99), -99)

    if mgr_team is not None:
        bill_k = _team_wage_bill_k(mgr_team)
        if salary_cap_k <= 0:
            salary_cap_k = int(bill_k * PRESIDENT_CAP_MODES[cap_mode])
        salary_cap_k = max(salary_cap_k, int(bill_k * 1.01))

    normalized = {
        "ownership": ownership,
        "salary_cap_mode": cap_mode,
        "salary_cap_k": salary_cap_k,
        "pressure": pressure,
        "investor_rounds": investor_rounds,
        "ipo_done": ipo_done,
        "pelotazo_done": pelotazo_done,
        "last_review_md": last_review_md,
        "next_review_md": next_review_md,
        "last_cap_penalty_md": last_cap_penalty_md,
    }
    data["president"] = normalized
    return normalized


def _set_salary_cap_mode(data: dict, mgr_team: "Team", mode: str):
    profile = _ensure_president_profile(data, mgr_team)
    mode = mode.upper()
    if mode not in PRESIDENT_CAP_MODES:
        return
    bill_k = _team_wage_bill_k(mgr_team)
    profile["salary_cap_mode"] = mode
    profile["salary_cap_k"] = max(int(bill_k * PRESIDENT_CAP_MODES[mode]), int(bill_k * 1.01))


def _can_register_with_cap(data: dict, mgr_team: "Team", player: "Player") -> tuple[bool, int, int]:
    profile = _ensure_president_profile(data, mgr_team)
    cap_k = max(0, _safe_int(profile.get("salary_cap_k", 0), 0))
    current_k = _team_wage_bill_k(mgr_team)
    projected_k = current_k + _estimate_weekly_wage_k(player)
    return projected_k <= cap_k, projected_k, cap_k


def _print_career_summary(data: dict):
    manager = data.get("manager", {})
    history = manager.get("history", [])
    if not isinstance(history, list) or not history:
        return

    print()
    print(_c(BOLD + YELLOW, "  â•â•â• RESUMEN DE CARRERA â•â•â•"))
    print(
        _c(
            GRAY,
            f"  Manager: {manager.get('name', '?')}  |  "
            f"Temporadas: {len(history)}  |  Prestigio: {_prestige_label(int(manager.get('prestige', 1)))}",
        )
    )
    for h in history[-8:]:
        season = h.get("season", "?")
        team = h.get("team", "?")
        comp = str(h.get("comp", "?"))
        pos = h.get("position", "?")
        met = bool(h.get("met", False))
        if "Segunda" in comp and isinstance(pos, int) and pos <= 3:
            outcome = "âœ“ ascenso"
        else:
            outcome = "âœ“ objetivo" if met else "âœ— objetivo"
        comp_short = "1Âª" if "Primera" in comp else ("2Âª" if "Segunda" in comp else comp[:2])
        print(f"  {season}: {team:<16} {comp_short:<2}  Pos {pos:<2}  {outcome}")
    print()


# ---- Save / Load -----------------------------------------------------------

def _save_career(data: dict):
    with open(CAREER_SAVE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def _load_career() -> Optional[dict]:
    if not CAREER_SAVE.exists():
        return None
    try:
        with open(CAREER_SAVE, encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, dict):
            _ensure_manager_depth(data)
        return data
    except Exception:
        return None


def _season_year(season: str) -> int:
    try:
        return int(str(season).split("-")[0])
    except Exception:
        return 2025


def _player_snapshot_from_team_player(player: Player, team: Team, season_year: int) -> dict:
    birth_year = season_year - max(player.age, 15)
    return {
        "name": player.name,
        "position": player.position,
        "citizenship": player.citizenship,
        "birth_year": birth_year,
        "me": player.me,
        "ve": player.ve,
        "re": player.re,
        "ag": player.ag,
        "ca": player.ca,
        "remate": player.remate,
        "regate": player.regate,
        "pase": player.pase,
        "tiro": player.tiro,
        "entrada": player.entrada,
        "portero": player.portero,
        "market_value": player.market_value,
        "team_slot_id": team.slot_id,
        "team_comp": team.comp,
        "status": "OK",
    }


def _build_players_snapshot(liga1: list[Team], liga2: list[Team], season: str) -> list[dict]:
    season_year = _season_year(season)
    players: list[dict] = []
    for team in liga1 + liga2:
        for player in team.players:
            players.append(_player_snapshot_from_team_player(player, team, season_year))
    return players


def _format_attr_delta(delta: int, attr: str) -> str:
    label = {"me": "ME", "ve": "VE", "re": "RE", "ca": "CA", "ag": "AG"}.get(attr, attr.upper())
    return f"{delta:+d} {label}"


def _training_plan_summary(training: dict) -> str:
    intensity = str(training.get("intensity", "MEDIUM")).upper()
    focus = str(training.get("focus", "BALANCED")).upper()
    i_label = TRAINING_INTENSITY_LABELS.get(intensity, intensity)
    f_label = TRAINING_FOCUS_LABELS.get(focus, focus)
    return f"{i_label} Â· {f_label}"


def _training_focus_attr_pool(focus: str, is_goalkeeper: bool) -> list[str]:
    focus = str(focus).upper()
    if is_goalkeeper:
        base = ["portero", "re", "ag", "ca", "pase", "ve"]
        if focus == "PHYSICAL":
            return base + ["re", "ag", "ve", "re"]
        if focus == "DEFENSIVE":
            return base + ["entrada", "re", "ag"]
        if focus == "TECHNICAL":
            return base + ["pase", "ca", "portero", "pase"]
        if focus == "ATTACKING":
            return base + ["ca", "pase"]
        return base

    base = ["ve", "re", "ag", "ca", "remate", "regate", "pase", "tiro", "entrada"]
    if focus == "PHYSICAL":
        return base + ["ve", "re", "ag", "ve", "re"]
    if focus == "DEFENSIVE":
        return base + ["entrada", "re", "ag", "entrada"]
    if focus == "TECHNICAL":
        return base + ["pase", "regate", "ca", "pase"]
    if focus == "ATTACKING":
        return base + ["remate", "tiro", "regate", "remate"]
    return base


def _pick_unique_attrs(rng: random.Random, pool: list[str], count: int) -> list[str]:
    if count <= 0 or not pool:
        return []
    unique: list[str] = []
    attempts = 0
    max_attempts = max(8, count * 8)
    while len(unique) < count and attempts < max_attempts:
        attr = rng.choice(pool)
        if attr not in unique:
            unique.append(attr)
        attempts += 1
    if len(unique) < count:
        for attr in ["ve", "re", "ag", "ca", "remate", "regate", "pase", "tiro", "entrada", "portero"]:
            if attr not in unique:
                unique.append(attr)
                if len(unique) >= count:
                    break
    return unique[:count]


def _pick_youth_position(rng: random.Random, ojeador: int) -> str:
    roll = rng.randint(0, 99)
    if ojeador >= 80:
        if roll < 9:
            return "Goalkeeper"
        if roll < 35:
            return "Defender"
        if roll < 70:
            return "Midfielder"
        return "Forward"
    if ojeador >= 60:
        if roll < 10:
            return "Goalkeeper"
        if roll < 36:
            return "Defender"
        if roll < 72:
            return "Midfielder"
        return "Forward"
    if roll < 12:
        return "Goalkeeper"
    if roll < 40:
        return "Defender"
    if roll < 73:
        return "Midfielder"
    return "Forward"


def _sample_attr(
    rng: random.Random,
    minimum: int,
    maximum: int,
    floor_bonus: int = 0,
    consistency: int = 0,
) -> int:
    low = min(maximum, minimum + floor_bonus)
    high = max(low, maximum + floor_bonus)
    value = rng.randint(low, high)
    for _ in range(max(0, consistency)):
        value = (value + rng.randint(low, high)) // 2
    return max(0, min(99, value))


def _print_development_summary(summary: dict):
    improved = summary.get("improved", [])
    declined = summary.get("declined", [])
    retired = summary.get("retired", [])
    youth_added = int(summary.get("youth_added", 0))
    training_line = str(summary.get("training_line", "")).strip()
    staff_line = str(summary.get("staff_line", "")).strip()

    print()
    print(_c(BOLD + YELLOW, "  ═══ EVOLUCION DE TU PLANTILLA ═══"))
    if training_line:
        print(f"  ENTRENAMIENTO: {training_line}")
    if staff_line:
        print(f"  STAFF CLAVE: {staff_line}")
    print(f"  MEJORAN:  {', '.join(improved[:4]) if improved else 'Sin cambios destacados'}")
    print(f"  BAJAN:    {', '.join(declined[:4]) if declined else 'Sin bajadas destacadas'}")
    print(f"  RETIRAN:  {', '.join(retired[:4]) if retired else 'Ningun jugador se retira'}")
    print(f"  CANTERANOS: {youth_added} jovenes (17 anos) suben al primer equipo")
    print()


def _apply_season_development(data: dict) -> dict:
    """Aplica evolucion/retiro de jugadores tras fin de temporada y devuelve resumen."""
    players = data.get("players", [])
    if not isinstance(players, list):
        return {"improved": [], "declined": [], "retired": [], "youth_added": 0}

    _ensure_manager_depth(data)
    manager = data.get("manager", {})
    staff = manager.get("staff", {})
    training = manager.get("training", {})
    intensity = str(training.get("intensity", "MEDIUM")).upper()
    focus = str(training.get("focus", "BALANCED")).upper()
    segundo = _safe_int(staff.get("segundo_entrenador", 50), 50)
    fisio = _safe_int(staff.get("fisio", 50), 50)
    ojeador = _safe_int(staff.get("ojeador", 50), 50)
    juveniles = _safe_int(staff.get("juveniles", 50), 50)

    season_str = str(data.get("season", "2025-26"))
    year = _season_year(season_str)
    season_hash = sum((i + 1) * ord(ch) for i, ch in enumerate(season_str))
    seed = int(data.get("season_seed", 0)) ^ season_hash
    rng = random.Random(seed)
    attrs = ["ve", "re", "ag", "ca", "remate", "regate", "pase", "tiro", "entrada", "portero"]

    manager_slot = int(data.get("team_slot", -1))
    improved: list[str] = []
    declined: list[str] = []
    retired: list[str] = []

    retired_now = 0
    for p in players:
        if not isinstance(p, dict):
            continue

        birth_year = int(p.get("birth_year", year - 22))
        age = year - birth_year
        is_goalkeeper = str(p.get("position", "")).lower().startswith("goal")
        is_manager_player = int(p.get("team_slot_id", -1)) == manager_slot
        before = {
            "me": int(p.get("me", 50)),
            "ve": int(p.get("ve", 50)),
            "re": int(p.get("re", 50)),
            "ca": int(p.get("ca", 50)),
            "ag": int(p.get("ag", 50)),
        }

        will_retire = age >= 37 or (age >= 35 and int(p.get("ve", 50)) <= 30)
        if will_retire:
            if p.get("status") != "RETIRED":
                retired_now += 1
                if is_manager_player:
                    retired.append(f"{p.get('name', 'Jugador')} ({age} anos)")
            p["status"] = "RETIRED"
            continue

        if age < 24:
            base_count = rng.randint(1, 3)
            intensity_adjust = 1 if intensity == "HIGH" else (-1 if intensity == "LOW" and base_count > 1 and rng.random() < 0.40 else 0)
            coach_adjust = 1 if age <= 21 and segundo >= 70 else 0
            improve_count = max(1, min(5, base_count + intensity_adjust + coach_adjust))
            pool = _training_focus_attr_pool(focus, is_goalkeeper=is_goalkeeper)
            weakest = sorted(attrs, key=lambda key: int(p.get(key, 50)))[:improve_count]
            targets: list[str] = []
            for attr in weakest:
                if attr not in targets:
                    targets.append(attr)
            for attr in _pick_unique_attrs(rng, pool, improve_count + 1):
                if attr not in targets:
                    targets.append(attr)
                if len(targets) >= improve_count:
                    break
            gain = 0
            for attr in targets[:improve_count]:
                intensity_bonus = 1 if intensity == "HIGH" and rng.random() < 0.55 else (1 if intensity == "MEDIUM" and rng.random() < 0.20 else 0)
                coach_bonus = 1 if segundo >= 75 and rng.random() < 0.35 else 0
                inc = min(4, rng.randint(1, 3) + intensity_bonus + coach_bonus)
                p[attr] = min(99, int(p.get(attr, 50)) + inc)
                gain += inc
            p["me"] = min(99, int(p.get("me", 50)) + max(1, round(gain / max(1, len(targets[:improve_count])))))
        elif age >= 31:
            decline = 2 if age >= 34 else 1
            if intensity == "HIGH" and age >= 33:
                decline += 1
            if fisio >= 85:
                decline -= 1
            elif fisio >= 65 and age >= 34:
                decline -= 1
            decline = max(0, decline)
            if decline > 0:
                p["ve"] = max(1, int(p.get("ve", 50)) - decline)
                p["re"] = max(1, int(p.get("re", 50)) - decline)
            if decline > 0 and intensity == "HIGH" and fisio < 40 and age >= 34 and rng.random() < 0.35:
                p["ag"] = max(0, int(p.get("ag", 50)) - 1)
            p["me"] = max(1, int(p.get("me", 50)) - (1 if decline >= 2 else 0))
        else:
            adjust_count = rng.randint(0, 2)
            if intensity == "HIGH" and rng.random() < 0.35:
                adjust_count = min(3, adjust_count + 1)
            elif intensity == "LOW" and adjust_count > 0 and rng.random() < 0.35:
                adjust_count -= 1
            prime_pool = _training_focus_attr_pool(focus, is_goalkeeper=is_goalkeeper)
            focus_attrs = set(_training_focus_attr_pool(focus, is_goalkeeper=is_goalkeeper))
            for attr in _pick_unique_attrs(rng, prime_pool, adjust_count):
                delta = rng.randint(-1, 1)
                if delta > 0 and focus != "BALANCED" and attr in focus_attrs and rng.random() < 0.45:
                    delta += 1
                if delta < 0 and segundo >= 75 and rng.random() < 0.40:
                    delta += 1
                p[attr] = max(0, min(99, int(p.get(attr, 50)) + delta))
            p["me"] = max(1, min(99, int(p.get("me", 50)) + rng.randint(-1, 1)))

        if not is_manager_player:
            continue

        after = {
            "me": int(p.get("me", 50)),
            "ve": int(p.get("ve", 50)),
            "re": int(p.get("re", 50)),
            "ca": int(p.get("ca", 50)),
            "ag": int(p.get("ag", 50)),
        }
        deltas = {k: after[k] - before[k] for k in before}
        pos_changes = [(k, v) for k, v in deltas.items() if v > 0]
        neg_changes = [(k, v) for k, v in deltas.items() if v < 0]
        player_name = str(p.get("name", "Jugador"))
        if pos_changes:
            attr, delta = max(pos_changes, key=lambda item: item[1])
            improved.append(f"{player_name} ({_format_attr_delta(delta, attr)})")
        if neg_changes:
            attr, delta = min(neg_changes, key=lambda item: item[1])
            veteran_note = f" - veterano {age} anos" if age >= 33 else ""
            declined.append(f"{player_name} ({_format_attr_delta(delta, attr)}){veteran_note}")

    youth_added = 0
    if manager_slot > 0:
        youth_count = 2 + (1 if juveniles >= 85 and ojeador >= 70 else 0)
        floor_bonus = min(6, juveniles // 20 + ojeador // 30)
        consistency = min(3, segundo // 30)
        for _ in range(youth_count):
            position = _pick_youth_position(rng, ojeador)
            is_goalkeeper = position == "Goalkeeper"
            ve = _sample_attr(rng, 35, 50, floor_bonus=floor_bonus, consistency=consistency)
            re = _sample_attr(rng, 35, 50, floor_bonus=floor_bonus, consistency=consistency)
            ag = _sample_attr(rng, 30, 55, floor_bonus=max(0, floor_bonus - 1), consistency=consistency)
            ca = _sample_attr(rng, 35, 55, floor_bonus=floor_bonus, consistency=consistency)
            remate = _sample_attr(
                rng,
                5 if is_goalkeeper else 20,
                25 if is_goalkeeper else 45,
                floor_bonus=max(0, floor_bonus - (2 if is_goalkeeper else 0)),
                consistency=consistency,
            )
            regate = _sample_attr(
                rng,
                5 if is_goalkeeper else 20,
                25 if is_goalkeeper else 45,
                floor_bonus=max(0, floor_bonus - (2 if is_goalkeeper else 0)),
                consistency=consistency,
            )
            pase = _sample_attr(
                rng,
                10 if is_goalkeeper else 25,
                30 if is_goalkeeper else 50,
                floor_bonus=max(0, floor_bonus - (1 if is_goalkeeper else 0)),
                consistency=consistency,
            )
            tiro = _sample_attr(
                rng,
                5 if is_goalkeeper else 20,
                20 if is_goalkeeper else 45,
                floor_bonus=max(0, floor_bonus - (2 if is_goalkeeper else 0)),
                consistency=consistency,
            )
            entrada = _sample_attr(
                rng,
                10 if is_goalkeeper else 25,
                30 if is_goalkeeper else 50,
                floor_bonus=max(0, floor_bonus - (1 if is_goalkeeper else 0)),
                consistency=consistency,
            )
            portero = _sample_attr(
                rng,
                35 if is_goalkeeper else 0,
                55 if is_goalkeeper else 0,
                floor_bonus=floor_bonus if is_goalkeeper else 0,
                consistency=consistency,
            )
            quality_hint = (ca + pase + regate + remate + tiro) // 5
            players.append({
                "name": f"Cantera {rng.randint(100, 999)}",
                "position": position,
                "birth_year": year - 17,
                "me": max(40, min(70, quality_hint + 8)),
                "ve": ve,
                "re": re,
                "ag": ag,
                "ca": ca,
                "remate": remate,
                "regate": regate,
                "pase": pase,
                "tiro": tiro,
                "entrada": entrada,
                "portero": portero if is_goalkeeper else 0,
                "team_slot_id": manager_slot,
                "status": "OK",
                "market_value": max(100_000, min(1_200_000, quality_hint * 18_000)),
            })
            youth_added += 1

    data["players"] = [p for p in players if isinstance(p, dict) and p.get("status") != "RETIRED"]
    data["retired_count"] = int(data.get("retired_count", 0)) + retired_now

    return {
        "improved": improved,
        "declined": declined,
        "retired": retired,
        "youth_added": youth_added,
        "training_line": _training_plan_summary(training),
        "staff_line": f"2o {segundo} · Fisio {fisio} · Ojeador {ojeador} · Juveniles {juveniles}",
    }


# ---- Standings from saved results ------------------------------------------

def _standings_from_results(results: list[dict], teams_by_slot: dict[int, Team]) -> list[Standing]:
    st: dict[int, Standing] = {slot: Standing(team=t) for slot, t in teams_by_slot.items()}
    for r in results:
        h, a, hg, ag = r["h"], r["a"], r["hg"], r["ag"]
        if h not in st or a not in st:
            continue
        sh, sa = st[h], st[a]
        sh.played += 1;  sa.played += 1
        sh.gf += hg;     sh.ga += ag
        sa.gf += ag;     sa.ga += hg
        if hg > ag:    sh.won  += 1; sa.lost  += 1
        elif ag > hg:  sa.won  += 1; sh.lost  += 1
        else:          sh.drawn += 1; sa.drawn += 1
    return sorted(st.values(), key=lambda s: s.sort_key())


# ---- Offer pool ------------------------------------------------------------

def _generate_offers(prestige: int, liga1: list[Team], liga2: list[Team], liga_rfef: list[Team] = [], liga_foreign: dict[str, list[Team]] = {}, data: Optional[dict] = None) -> list[Team]:
    l_rfef = sorted(liga_rfef, key=lambda t: t.strength())
    l2 = sorted(liga2, key=lambda t: t.strength())
    l1 = sorted(liga1, key=lambda t: t.strength())
    # Todas las ligas extranjeras mezcladas, ordenadas por fuerza media del equipo
    l_foreign: list[Team] = sorted(
        [t for teams in liga_foreign.values() for t in teams],
        key=lambda t: t.strength(),
    )
    pool: list[Team] = []

    manager = (data or {}).get("manager", {})
    history = manager.get("history", []) if isinstance(manager, dict) else []
    last = history[-1] if isinstance(history, list) and history else {}
    last_comp = str(last.get("comp", ""))
    last_pos = int(last.get("position", 999)) if isinstance(last.get("position", None), int) else 999
    team_slot = int((data or {}).get("team_slot", -1))
    was_promoted_rfef = "RFEF" in last_comp and last_pos <= 3
    was_promoted = "Segunda" in last_comp and last_pos <= 3
    stayed_segunda = "Segunda" in last_comp and not was_promoted

    if prestige == 1:
        pool = l_rfef if l_rfef else l2[:8]
    elif prestige <= 2:
        pool = l2[:12] if not was_promoted_rfef else l2[:12]
    elif prestige <= 4:
        pool = list(l2)
    elif prestige <= 5:
        pool = l2[-8:] + l1[:8]
    elif prestige <= 6:
        if stayed_segunda:
            idx = next((i for i, t in enumerate(l2) if t.slot_id == team_slot), len(l2) // 2)
            similar = l2[max(0, idx - 4): min(len(l2), idx + 5)]
            pool = similar + l1[:4]
        else:
            pool = l1[:16]
    elif prestige <= 7:
        # Primera DivisiÃ³n espaÃ±ola + ligas top extranjeras (mÃ¡s dÃ©biles)
        pool = list(l1) + l_foreign[:10]
    elif prestige <= 9:
        # Mezcla de Primera espaÃ±ola y todas las ligas extranjeras
        pool = l1[-10:] + l_foreign
    else:
        # Prestige 10: solo las mejores ligas extranjeras + cima de la Primera
        pool = l_foreign[-20:] + l1[-5:]

    uniq: list[Team] = []
    seen: set[int] = set()
    for t in pool:
        if t.slot_id in seen:
            continue
        uniq.append(t)
        seen.add(t.slot_id)
    pool = uniq
    random.shuffle(pool)
    return pool[:4] if len(pool) >= 4 else pool


# ---- Objectives ------------------------------------------------------------

def _assign_objective(team: Team, liga1: list[Team], liga2: list[Team],
                       liga_rfef: list[Team] = [], liga_foreign: "dict[str, list[Team]]" = {}) -> str:
    comp = team.comp
    if comp == "ES1":
        rank = sorted(liga1, key=lambda t: t.strength(), reverse=True)
    elif comp == "ES2":
        rank = sorted(liga2, key=lambda t: t.strength(), reverse=True)
    elif comp in ("E3G1", "E3G2"):
        group = [t for t in liga_rfef if t.comp == comp] or list(liga_rfef)
        rank = sorted(group, key=lambda t: t.strength(), reverse=True)
    else:
        fl = liga_foreign.get(comp, [])
        rank = sorted(fl, key=lambda t: t.strength(), reverse=True) if fl else sorted(liga1, key=lambda t: t.strength(), reverse=True)
    total = len(rank)
    idx = next((i for i, t in enumerate(rank) if t.slot_id == team.slot_id), total // 2)
    pos = idx + 1
    direct_bottom = 6 if total >= 22 else 4
    direct_start = max(1, total - direct_bottom + 1)

    if pos <= 3:
        return "GANAR LA LIGA"
    if pos <= 10:
        return "CLASIFICACION TOP 10"
    if pos >= direct_start:
        return "EVITAR EL DESCENSO DIRECTO"
    return "EVITAR EL DESCENSO"


def _check_objective(objective: str, position: int, total: int, comp: str) -> bool:
    o = objective.upper()
    direct_bottom = 6 if total >= 22 else 4
    safe_limit = total - direct_bottom
    if "DESCENSO DIRECTO" in o:
        return position <= safe_limit
    if "DESCENSO" in o:
        return position <= safe_limit
    elif "ASCENDER" in o: return position <= 3
    elif "TOP 10"   in o: return position <= 10
    elif "MITAD"    in o: return position <= total // 2
    elif "EUROPA"   in o: return position <= 6
    elif "LIGA"     in o or "TITULO" in o or "GANAR" in o: return position == 1
    return False


# ---- PM display helpers ----------------------------------------------------

def _pm_header(data: dict, matchday: int, total_md: int, mgr_team: Optional["Team"] = None):
    m = data["manager"]
    mode = _ensure_manager_play_mode(data)
    print()
    print(_c(BOLD + CYAN, f"  â•â•â• PROMANAGER Â· TEMPORADA {data['season']} â•â•â•"))
    print(_c(YELLOW, f"  Manager : {m['name']:<20}  Prestigio: {_prestige_label(m['prestige'])}"))
    print(_c(YELLOW, f"  Equipo  : {data['team_name']:<20}  Jornada  : {matchday}/{total_md}"))
    print(_c(CYAN,   f"  Objetivo: {data['objective']}"))
    print(_c(CYAN,   f"  Nivel   : {_play_mode_label(mode)}"))
    if mgr_team is not None and _play_mode_allows_president(mode):
        profile = _ensure_president_profile(data, mgr_team)
        bill_k = _team_wage_bill_k(mgr_team)
        cap_k = int(profile.get("salary_cap_k", 0))
        margin_k = cap_k - bill_k
        margin_c = GREEN if margin_k >= 0 else RED
        print(
            _c(
                GRAY,
                f"  Presidente: {profile.get('ownership', 'SOCIOS')}  |  Presion {profile.get('pressure', 0)}  |  "
                f"Tope â‚¬{cap_k:,}K · Masa â‚¬{bill_k:,}K · Margen {_c(margin_c, f'â‚¬{margin_k:+,}K')}",
            )
        )
    print()


def _mini_standings(standings: list[Standing], mgr_slot: int, n_rel: int):
    total   = len(standings)
    mgr_idx = next((i for i, s in enumerate(standings) if s.team.slot_id == mgr_slot), 0)
    show    = set()
    show.update(range(min(3, total)))
    show.update(range(max(0, mgr_idx - 2), min(total, mgr_idx + 3)))
    show.update(range(max(0, total - 3), total))
    print(_c(GRAY, f"  {'#':>3}  {'EQUIPO':<24} {'PJ':>3} {'PTS':>4}"))
    prev = -1
    for pos in sorted(show):
        if pos >= total:
            continue
        if prev >= 0 and pos > prev + 1:
            print(_c(GRAY, "        ..."))
        if pos == total - n_rel:
            print(_c(RED, "       â”€â”€â”€ zona de descenso â”€â”€"))
        s   = standings[pos]
        mgr = s.team.slot_id == mgr_slot
        if pos == 0:             color = GREEN
        elif pos >= total-n_rel: color = RED
        elif mgr:                color = YELLOW
        else:                    color = RESET
        print(color + f"  {pos+1:>3}  {s.team.name[:23]:<24} {s.played:>3} {s.points:>4}" +
              (" â—„" if mgr else "") + RESET)
        prev = pos
    print()


def _show_md_results(md_res: list[dict], tbs: dict[int, Team], mgr_slot: int):
    print(_c(YELLOW, "  â”€â”€â”€ RESULTADOS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€"))
    for r in md_res:
        ht = tbs.get(r["h"]); at = tbs.get(r["a"])
        if not ht or not at:
            continue
        hg, ag  = r["hg"], r["ag"]
        is_mgr  = r["h"] == mgr_slot or r["a"] == mgr_slot
        row     = f"  {'â–¶' if is_mgr else ' '} {ht.name[:21]:<22} {hg:>2} - {ag:<2} {at.name}"
        if is_mgr:
            won  = (r["h"] == mgr_slot and hg > ag) or (r["a"] == mgr_slot and ag > hg)
            color = GREEN if won else (GRAY if hg == ag else RED)
        else:
            color = RESET
        print(color + row + RESET)
    print()


def _news_item(r: dict, tbs: dict[int, Team], mgr_slot: int,
               mgr_name: str, pos: int, total: int, n_rel: int) -> str:
    is_home = r["h"] == mgr_slot
    opp     = tbs.get(r["a"] if is_home else r["h"])
    oname   = opp.name if opp else "el rival"
    mg      = r["hg"] if is_home else r["ag"]
    og      = r["ag"] if is_home else r["hg"]
    venue   = "en casa" if is_home else "a domicilio"
    drop    = pos > total - n_rel
    if mg > og:
        return random.choice([
            f"Victoria {venue} ante {oname} ({mg}-{og}). Equipo en puesto {pos}.",
            f"Tres puntos {venue}. {mgr_name} vence a {oname} y sube posiciones.",
        ])
    elif mg == og:
        extra = " Sigue en zona de descenso." if drop else f" El equipo es {pos}Âº."
        return f"Empate {venue} ante {oname} ({mg}-{mg}).{extra}"
    else:
        extra = " La junta convoca urgencia." if drop else f" PosiciÃ³n {pos}."
        return f"Derrota {venue} ante {oname} ({mg}-{og}).{extra}"


def _pick_player_name(team: Team, rng: random.Random, prefer_attack: bool = False) -> str:
    if not team.players:
        return "Jugador"
    if prefer_attack:
        attackers = [
            p for p in team.players
            if ("Forward" in p.position or "Winger" in p.position or "Attacker" in p.position)
        ]
        if attackers:
            return rng.choice(attackers).name
    return rng.choice(team.players).name


def _append_dynamic_news(
    news: list[str],
    md: int,
    md_res: list[dict],
    tbs: dict[int, Team],
    mgr_slot: int,
    mgr_team: Team,
    mgr_name: str,
    standings: list[Standing],
    n_rel: int,
) -> list[str]:
    total = len(standings)
    mgr_pos = next((i + 1 for i, s in enumerate(standings) if s.team.slot_id == mgr_slot), 0)
    my_r = next((r for r in md_res if r["h"] == mgr_slot or r["a"] == mgr_slot), None)
    noise = sum((r["h"] * 31 + r["a"] * 17 + r["hg"] * 7 + r["ag"]) for r in md_res) if md_res else 0
    rng = random.Random(md * 1315423911 ^ mgr_slot ^ noise)

    candidates: list[str] = []
    if my_r:
        candidates.append(_news_item(my_r, tbs, mgr_slot, mgr_name, mgr_pos, total, n_rel))

    striker = _pick_player_name(mgr_team, rng, prefer_attack=True)
    candidates.append(f"Tu delantero {striker} ha marcado 2 goles - forma al maximo.")

    injured = _pick_player_name(mgr_team, rng)
    weeks = rng.randint(2, 4)
    candidates.append(f"Lesion de {injured} - baja {weeks} semanas.")

    if mgr_pos:
        if mgr_pos <= max(3, total // 4):
            candidates.append(f"La junta esta satisfecha con tu rendimiento (pos {mgr_pos}Âª).")
        elif mgr_pos > total - n_rel:
            candidates.append(f"La junta exige reaccion inmediata (pos {mgr_pos}Âª).")
        else:
            candidates.append(f"La junta mantiene la calma con tu rendimiento (pos {mgr_pos}Âª).")

    rivals = [t for sid, t in tbs.items() if sid != mgr_slot]
    if rivals:
        rival = rng.choice(rivals)
        fichaje = _pick_player_name(rival, rng, prefer_attack=True)
        fee = rng.randint(1, 8)
        candidates.append(f"Rival {rival.name} acaba de fichar a {fichaje} por {fee}Mâ‚¬.")

    unique: list[str] = []
    seen: set[str] = set()
    for item in candidates:
        if item in seen:
            continue
        unique.append(item)
        seen.add(item)

    if not unique:
        return []
    rng.shuffle(unique)
    take = 2 if len(unique) > 1 and rng.random() < 0.65 else 1
    selected = unique[:take]
    for item in selected:
        news.append(f"J{md}: {item}")
    return selected


def _president_matchday_effects(
    data: dict,
    md: int,
    mgr_team: Team,
    my_r: Optional[dict],
    mgr_pos: int,
    total: int,
    n_rel: int,
    news: list[str],
) -> list[str]:
    profile = _ensure_president_profile(data, mgr_team)
    rng = random.Random(int(data.get("season_seed", 0)) ^ (md * 65537) ^ mgr_team.slot_id ^ 0x50E)

    pressure = int(profile.get("pressure", 0))
    ownership = str(profile.get("ownership", "SOCIOS"))
    budget = int(data.get("budget", 0))
    last_review_md = int(profile.get("last_review_md", 0))
    next_review_md = int(profile.get("next_review_md", max(1, md + 1)))
    last_cap_penalty_md = int(profile.get("last_cap_penalty_md", -99))
    events: list[str] = []

    # Presion base por resultado/tabla.
    if my_r:
        is_home = my_r.get("h") == mgr_team.slot_id
        mg = int(my_r.get("hg", 0)) if is_home else int(my_r.get("ag", 0))
        og = int(my_r.get("ag", 0)) if is_home else int(my_r.get("hg", 0))
        diff = mg - og
        if diff >= 2:
            pressure -= 1
        elif diff == 1:
            pressure = pressure
        elif diff == 0:
            pressure += 0
        elif diff == -1:
            pressure += 1
        else:
            pressure += 2

    if mgr_pos > total - n_rel:
        pressure += 1
    elif mgr_pos <= max(3, total // 4):
        pressure -= 1
    pressure = max(0, min(12, pressure))

    bill_k = _team_wage_bill_k(mgr_team)
    cap_k = int(profile.get("salary_cap_k", 0))
    cap_crisis = bill_k > cap_k
    relegation_alert = mgr_pos > total - n_rel and md >= max(7, total // 4)
    high_pressure = pressure >= 9
    review_due = md >= max(1, next_review_md)
    review_now = review_due or cap_crisis or relegation_alert or high_pressure

    # Dinamica economica por propiedad.
    if review_now:
        if ownership == "PRIVATE":
            if pressure >= 8 and rng.random() < 0.35:
                injection = int(rng.uniform(2_000_000, 5_000_000))
                budget += injection
                pressure = max(0, pressure - 1)
                events.append(f"Consejo privado aprueba ampliacion de caja (+â‚¬{injection:,.0f}).")
            elif pressure <= 2 and rng.random() < 0.22:
                payout = int(rng.uniform(800_000, 2_200_000))
                budget = max(0, budget - payout)
                events.append(f"Consejo privado extrae dividendos (â‚¬{payout:,.0f}).")

        elif ownership == "STATE":
            # Aportaciones puntuales, no semanales.
            if (md % 6 == 0 and rng.random() < 0.70) or rng.random() < 0.20:
                injection = int(rng.uniform(1_800_000, 4_500_000))
                budget += injection
                profile["salary_cap_k"] = int(int(profile.get("salary_cap_k", 0)) * 1.01)
                events.append(f"Aportacion institucional extraordinaria (+â‚¬{injection:,.0f}).")

        elif ownership == "LISTED":
            if my_r:
                is_home = my_r.get("h") == mgr_team.slot_id
                mg = int(my_r.get("hg", 0)) if is_home else int(my_r.get("ag", 0))
                og = int(my_r.get("ag", 0)) if is_home else int(my_r.get("hg", 0))
                diff = mg - og
                if diff > 0 and rng.random() < 0.65:
                    delta = int(rng.uniform(300_000, 1_200_000))
                    budget += delta
                    events.append(f"La cotizacion sube tras la victoria (+â‚¬{delta:,.0f}).")
                elif diff < 0 and rng.random() < 0.70:
                    delta = int(rng.uniform(300_000, 1_400_000))
                    budget = max(0, budget - delta)
                    events.append(f"La cotizacion cae tras la derrota (â‚¬{delta:,.0f}).")

    # Control del tope salarial: sancion espaciada para evitar spam semanal.
    if cap_crisis and (md - last_cap_penalty_md >= 4 or review_now):
        overflow_k = bill_k - cap_k
        penalty = min(int(overflow_k * 90), max(500_000, int(budget * 0.12)))
        budget = max(0, budget - penalty)
        pressure = min(12, pressure + 2)
        last_cap_penalty_md = md
        events.append(
            f"Incumplimiento de tope salarial: multa de control financiero (â‚¬{penalty:,.0f})."
        )
    elif cap_crisis:
        pressure = min(12, pressure + 1)

    # Junta endurece/relaja disciplina salarial segun presion.
    current_mode = str(profile.get("salary_cap_mode", "BALANCED"))
    if pressure >= 9 and current_mode != "STRICT" and review_now:
        _set_salary_cap_mode(data, mgr_team, "STRICT")
        events.append("La junta impone tope salarial estricto por alta presion.")
    elif pressure <= 2 and current_mode == "STRICT" and review_now and md >= 8:
        _set_salary_cap_mode(data, mgr_team, "BALANCED")
        events.append("La junta relaja el tope salarial a modo equilibrado.")

    if review_now:
        profile["last_review_md"] = md
        # Frecuencia de reunion: cada 3-6 jornadas (mas rapida en crisis).
        min_gap = 2 if pressure >= 8 else 3
        max_gap = 4 if pressure >= 8 else 6
        profile["next_review_md"] = md + rng.randint(min_gap, max_gap)
    else:
        profile["last_review_md"] = last_review_md
        profile["next_review_md"] = max(next_review_md, md + 1)

    profile["pressure"] = pressure
    profile["last_cap_penalty_md"] = last_cap_penalty_md
    data["budget"] = budget
    for e in events:
        news.append(f"J{md}: {e}")
    return events


def _season_end_screen(data: dict, standings: list[Standing], mgr_slot: int,
                        mgr_team: Team, is_l1: bool, n_rel: int) -> bool:
    comp_name = _comp_name(data.get("competition", "ES1"))
    total     = len(standings)
    mgr_pos   = next((i+1 for i, s in enumerate(standings) if s.team.slot_id == mgr_slot), 0)
    met       = _check_objective(data["objective"], mgr_pos, total, data["competition"])
    mgr_st    = next((s for s in standings if s.team.slot_id == mgr_slot), None)

    print()
    print(_c(BOLD + YELLOW, "  â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—"))
    print(_c(BOLD + YELLOW, "  â•‘        FIN DE TEMPORADA           â•‘"))
    print(_c(BOLD + YELLOW, "  â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"))
    print()
    print(_c(GREEN, f"  ðŸ† CAMPEÃ“N: {standings[0].team.name} ({standings[0].points} pts)"))
    print()
    print(_c(YELLOW, f"  TU RESULTADO â€” {mgr_team.name}"))
    if mgr_st:
        print(f"  PosiciÃ³n: {mgr_pos}Âº / {total}")
        print(f"  PJ:{mgr_st.played}  G-E-P: {mgr_st.won}-{mgr_st.drawn}-{mgr_st.lost}  "
              f"GF:{mgr_st.gf}  GC:{mgr_st.ga}  DG:{mgr_st.gd:+d}  PTS:{mgr_st.points}")
    print()
    print(f"  Objetivo: {data['objective']}")
    if met:
        print(_c(GREEN, "  âœ“  OBJETIVO CUMPLIDO"))
    else:
        print(_c(RED,   "  âœ—  OBJETIVO NO CUMPLIDO"))
    mode = _ensure_manager_play_mode(data)
    if _play_mode_allows_president(mode):
        profile = _ensure_president_profile(data, mgr_team)
        print(
            _c(
                GRAY,
                f"  Propiedad: {profile.get('ownership', 'SOCIOS')}  |  Presion junta: {profile.get('pressure', 0)}",
            )
        )
    else:
        print(_c(GRAY, f"  Nivel de control: {_play_mode_label(mode)}"))

    rel_teams = [standings[total - n_rel + i].team for i in range(n_rel)]
    if mgr_team in rel_teams:
        print(_c(RED,   f"\n  â¬‡  {mgr_team.name} DESCIENDE de {comp_name}"))
    elif not is_l1 and mgr_pos <= 3:
        comp_key = data.get("competition", "ES2")
        if comp_key == "ES2":
            print(_c(GREEN, f"\n  â¬†  {mgr_team.name} ASCIENDE a Primera DivisiÃ³n"))
        elif comp_key in ("E3G1", "E3G2"):
            print(_c(GREEN, f"\n  â¬†  {mgr_team.name} ASCIENDE a Segunda DivisiÃ³n"))
        else:
            print(_c(GREEN, f"\n  â¬†  {mgr_team.name} â€” ASCENSO clasificado"))

    old_p = data["manager"]["prestige"]
    new_p = min(10, old_p + 1) if met else max(1, old_p - 1)
    print()
    print(_c(CYAN, f"  Prestigio: {_prestige_label(old_p)}  â†’  {_prestige_label(new_p)}"))
    print()
    print_standings(standings, f"{comp_name} â€” {data['season']}", relegated_from=n_rel)

    data["manager"]["prestige"]      = new_p
    data["manager"]["total_seasons"] += 1
    data["manager"]["history"].append({
        "season":    data["season"],
        "team":      mgr_team.name,
        "comp":      comp_name,
        "position":  mgr_pos,
        "objective": data["objective"],
        "met":       met,
    })
    # La presion se recalibra en cambio de temporada solo en modo Total.
    if _play_mode_allows_president(mode):
        profile = _ensure_president_profile(data, mgr_team)
        profile["pressure"] = max(0, int(profile.get("pressure", 0)) - (2 if met else 0))
    dev_summary = _apply_season_development(data)
    _print_development_summary(dev_summary)
    data["phase"] = "POSTSEASON"
    _save_career(data)
    print(_c(CYAN, "  1. Continuar carrera"))
    print(_c(CYAN, "  2. Salir al menu principal"))
    op = input_int("  Opcion: ", 1, 2)
    keep_playing = op == 1
    data["continue_career_next"] = keep_playing
    _save_career(data)
    return keep_playing


# ---- Tactics ---------------------------------------------------------------

DEFAULT_TACTIC: dict = {
    "tipoJuego":      2,   # 1=DEFENSIVO 2=EQUILIBRADO 3=OFENSIVO
    "tipoMarcaje":    1,   # 1=AL HOMBRE 2=ZONA
    "tipoPresion":    2,   # 1=BAJA 2=MEDIA 3=ALTA
    "tipoDespejes":   1,   # 1=LARGO 2=CONTROLADO
    "faltas":         2,   # 1=LIMPIO 2=NORMAL 3=DURO
    "porcToque":     50,   # 0-100
    "porcContra":    30,   # 0-100
    "perdidaTiempo":  0,   # 0=NO  1=ACTIVA
}

_TACTIC_LABELS = {
    "tipoJuego":    {1: "DEFENSIVO", 2: "EQUILIBRADO", 3: "OFENSIVO"},
    "tipoMarcaje":  {1: "AL HOMBRE", 2: "ZONA"},
    "tipoPresion":  {1: "BAJA",      2: "MEDIA",       3: "ALTA"},
    "tipoDespejes": {1: "LARGO",     2: "CONTROLADO"},
    "faltas":       {1: "LIMPIO",    2: "NORMAL",       3: "DURO"},
}


def _tactic_summary(t: dict) -> str:
    tj = _TACTIC_LABELS["tipoJuego"].get(t.get("tipoJuego", 2), "?")
    tm = _TACTIC_LABELS["tipoMarcaje"].get(t.get("tipoMarcaje", 1), "?")
    tp = _TACTIC_LABELS["tipoPresion"].get(t.get("tipoPresion", 2), "?")
    pt = "  [PÃ‰RD.TIEMPO]" if t.get("perdidaTiempo", 0) == 1 else ""
    return f"{tj} Â· {tm} Â· PresiÃ³n {tp} Â· Toque {t.get('porcToque',50)}% Â· Contra {t.get('porcContra',30)}%{pt}"


def _tactic_menu(data: dict):
    t = data.setdefault("tactic", dict(DEFAULT_TACTIC))
    print(_c(BOLD + YELLOW, "\n  â•â•â• TÃCTICA â•â•â•"))

    while True:
        print(_c(GRAY, f"\n  Ajuste tÃ¡ctico actual: {_tactic_adj(t, is_home=False):+.1f}"))
        print(f"  1. Tipo de juego    : {_c(CYAN, _TACTIC_LABELS['tipoJuego'][t['tipoJuego']])}")
        print(f"  2. Marcaje          : {_c(CYAN, _TACTIC_LABELS['tipoMarcaje'][t['tipoMarcaje']])}")
        print(f"  3. PresiÃ³n          : {_c(CYAN, _TACTIC_LABELS['tipoPresion'][t['tipoPresion']])}")
        print(f"  4. Despejes         : {_c(CYAN, _TACTIC_LABELS['tipoDespejes'][t['tipoDespejes']])}")
        print(f"  5. Faltas           : {_c(CYAN, _TACTIC_LABELS['faltas'][t['faltas']])}")
        print(f"  6. % Toque          : {_c(CYAN, str(t['porcToque']))}%")
        print(f"  7. % Contragolpe    : {_c(CYAN, str(t['porcContra']))}%")
        pt_lbl = _c(YELLOW, "ACTIVA") if t.get("perdidaTiempo", 0) == 1 else _c(GRAY, "NO")
        print(f"  8. PÃ©rdida de tiempo: {pt_lbl}  {_c(GRAY, '(mÃ¡s amarillas Â· +tiempo aÃ±adido)')}")
        print(_c(CYAN, "  0. Volver"))

        op = input_int("  OpciÃ³n: ", 0, 8)
        if op == 0:
            _save_career(data)
            break
        elif op == 1:
            print("    1.DEFENSIVO  2.EQUILIBRADO  3.OFENSIVO")
            t["tipoJuego"] = input_int("  Elige (1-3): ", 1, 3)
        elif op == 2:
            print("    1.AL HOMBRE  2.ZONA")
            t["tipoMarcaje"] = input_int("  Elige (1-2): ", 1, 2)
        elif op == 3:
            print("    1.BAJA  2.MEDIA  3.ALTA")
            t["tipoPresion"] = input_int("  Elige (1-3): ", 1, 3)
        elif op == 4:
            print("    1.LARGO  2.CONTROLADO")
            t["tipoDespejes"] = input_int("  Elige (1-2): ", 1, 2)
        elif op == 5:
            print("    1.LIMPIO  2.NORMAL  3.DURO")
            t["faltas"] = input_int("  Elige (1-3): ", 1, 3)
        elif op == 6:
            t["porcToque"] = input_int("  % Toque (0-100): ", 0, 100)
        elif op == 7:
            t["porcContra"] = input_int("  % Contragolpe (0-100): ", 0, 100)
        elif op == 8:
            t["perdidaTiempo"] = 1 - t.get("perdidaTiempo", 0)  # toggle


def _manager_depth_menu(data: dict):
    _ensure_manager_depth(data)
    manager = data.setdefault("manager", {})
    training = manager.setdefault("training", dict(DEFAULT_TRAINING_PLAN))
    staff = manager.setdefault("staff", dict(DEFAULT_STAFF_PROFILE))
    staff_keys = list(DEFAULT_STAFF_PROFILE.keys())

    while True:
        print(_c(BOLD + YELLOW, "\n  ═══ ENTRENAMIENTO Y STAFF ═══"))
        print(f"  Plan actual: {_training_plan_summary(training)}")
        print(f"  1. Intensidad: {_c(CYAN, TRAINING_INTENSITY_LABELS.get(training['intensity'], training['intensity']))}")
        print(f"  2. Enfoque   : {_c(CYAN, TRAINING_FOCUS_LABELS.get(training['focus'], training['focus']))}")
        print()
        for idx, key in enumerate(staff_keys, 3):
            print(f"  {idx}. {STAFF_LABELS.get(key, key):<18} {_c(CYAN, str(staff.get(key, 50)))}")
        print(_c(CYAN, "  0. Volver"))

        op = input_int("  Opcion: ", 0, 2 + len(staff_keys))
        if op == 0:
            _save_career(data)
            return
        if op == 1:
            print("    1.Suave  2.Media  3.Alta")
            selected = input_int("  Elige (1-3): ", 1, 3)
            training["intensity"] = TRAINING_INTENSITIES[selected - 1]
            continue
        if op == 2:
            print("    1.Equilibrado  2.Fisico  3.Defensivo  4.Tecnico  5.Ataque")
            selected = input_int("  Elige (1-5): ", 1, 5)
            training["focus"] = TRAINING_FOCUSES[selected - 1]
            continue
        staff_idx = op - 3
        if 0 <= staff_idx < len(staff_keys):
            key = staff_keys[staff_idx]
            current = _safe_int(staff.get(key, 50), 50)
            print(f"  {STAFF_LABELS.get(key, key)} actual: {current}")
            staff[key] = input_int("  Nuevo nivel (0-100): ", 0, 100)


def _president_menu(data: dict, mgr_team: "Team"):
    profile = _ensure_president_profile(data, mgr_team)
    rng = random.Random(int(data.get("season_seed", 0)) ^ int(data.get("current_matchday", 1)) ^ 0xC1A5)
    while True:
        profile = _ensure_president_profile(data, mgr_team)
        budget = int(data.get("budget", 0))
        bill_k = _team_wage_bill_k(mgr_team)
        cap_k = int(profile.get("salary_cap_k", 0))
        room_k = cap_k - bill_k
        room_color = GREEN if room_k >= 0 else RED
        print(_c(BOLD + YELLOW, "\n  â•â•â• DESPACHO DEL PRESIDENTE â•â•â•"))
        print(f"  Club: {mgr_team.name}  |  Propiedad: {_c(CYAN, profile.get('ownership', 'SOCIOS'))}")
        print(f"  Presupuesto: {_c(YELLOW, f'â‚¬{budget:,.0f}')}")
        print(
            f"  Tope salarial ({PRESIDENT_CAP_LABELS.get(profile.get('salary_cap_mode', 'BALANCED'), 'Equilibrado')}): "
            f"{_c(CYAN, f'â‚¬{cap_k:,}K/sem')}  |  Masa actual: {_c(CYAN, f'â‚¬{bill_k:,}K/sem')}  |  "
            f"Margen: {_c(room_color, f'â‚¬{room_k:+,}K/sem')}"
        )
        print(f"  Presion junta/mercado: {_c(GRAY, str(profile.get('pressure', 0)))}")
        print()
        print(_c(CYAN, "  1. Ajustar tope salarial"))
        print(_c(CYAN, "  2. Entrada de inversor privado"))
        print(_c(CYAN, "  3. Operacion club-estado"))
        print(_c(CYAN, "  4. Salida a bolsa (IPO)"))
        print(_c(CYAN, "  5. Pelotazo inmobiliario"))
        print(_c(CYAN, "  0. Volver"))
        op = input_int("  Opcion: ", 0, 5)
        if op == 0:
            _save_career(data)
            return

        if op == 1:
            print(_c(CYAN, "  1.Estricto  2.Equilibrado  3.Flexible"))
            mode_opt = input_int("  Modo (1-3): ", 1, 3)
            mode = ("STRICT", "BALANCED", "FLEX")[mode_opt - 1]
            _set_salary_cap_mode(data, mgr_team, mode)
            _save_career(data)
            continue

        if op == 2:
            rounds = int(profile.get("investor_rounds", 0))
            if rounds >= 2:
                print(_c(RED, "  Ya has agotado las dos rondas de inversores esta etapa.\n"))
                continue
            prestige = int(data.get("manager", {}).get("prestige", 1))
            injection = int((8_000_000 + prestige * 2_500_000) * (0.85 + rng.random() * 0.30))
            data["budget"] = int(data.get("budget", 0)) + injection
            profile["ownership"] = "PRIVATE"
            profile["investor_rounds"] = rounds + 1
            profile["pressure"] = int(profile.get("pressure", 0)) + 1
            profile["salary_cap_k"] = int(profile.get("salary_cap_k", 0) * 1.08)
            data.setdefault("news", []).append(
                f"Consejo: entra inversor privado (+â‚¬{injection:,.0f}). Exigencia deportiva al alza."
            )
            _save_career(data)
            print(_c(GREEN, f"  âœ“ Inversor firmado. Inyeccion: â‚¬{injection:,.0f}\n"))
            continue

        if op == 3:
            if profile.get("ownership") == "STATE":
                print(_c(GRAY, "  El club ya opera como proyecto de estado.\n"))
                continue
            injection = int(35_000_000 * (0.9 + rng.random() * 0.25))
            data["budget"] = int(data.get("budget", 0)) + injection
            profile["ownership"] = "STATE"
            profile["pressure"] = int(profile.get("pressure", 0)) + 2
            profile["salary_cap_k"] = int(profile.get("salary_cap_k", 0) * 1.35)
            data.setdefault("news", []).append(
                f"Operacion club-estado cerrada (+â‚¬{injection:,.0f}). El liston institucional sube."
            )
            _save_career(data)
            print(_c(GREEN, f"  âœ“ Operacion cerrada. Fondos: â‚¬{injection:,.0f}\n"))
            continue

        if op == 4:
            if bool(profile.get("ipo_done", False)):
                print(_c(GRAY, "  El club ya cotiza en bolsa.\n"))
                continue
            injection = int(22_000_000 * (0.9 + rng.random() * 0.20))
            data["budget"] = int(data.get("budget", 0)) + injection
            profile["ipo_done"] = True
            profile["ownership"] = "LISTED"
            profile["pressure"] = int(profile.get("pressure", 0)) + 1
            profile["salary_cap_k"] = int(profile.get("salary_cap_k", 0) * 1.15)
            data.setdefault("news", []).append(
                f"Salida a bolsa completada (+â‚¬{injection:,.0f}). Mercado exige resultados trimestrales."
            )
            _save_career(data)
            print(_c(GREEN, f"  âœ“ IPO completada. Captacion: â‚¬{injection:,.0f}\n"))
            continue

        if op == 5:
            if bool(profile.get("pelotazo_done", False)):
                print(_c(GRAY, "  Ya ejecutaste un pelotazo inmobiliario esta etapa.\n"))
                continue
            injection = int(16_000_000 * (0.9 + rng.random() * 0.35))
            data["budget"] = int(data.get("budget", 0)) + injection
            profile["pelotazo_done"] = True
            profile["pressure"] = int(profile.get("pressure", 0)) + 1
            profile["salary_cap_k"] = int(profile.get("salary_cap_k", 0) * 1.05)
            data.setdefault("news", []).append(
                f"Pelotazo inmobiliario aprobado (+â‚¬{injection:,.0f}). Aumenta la tension social en el entorno."
            )
            _save_career(data)
            print(_c(GREEN, f"  âœ“ Operacion urbanistica cerrada. Entrada: â‚¬{injection:,.0f}\n"))


# ---------------------------------------------------------------------------
# Modo Entrenador â€” animated pitch view
# ---------------------------------------------------------------------------

_COMM_ATT_OUR = [
    "Centro raso al area, corta la defensa.",
    "Disparo lejano, fuera por poco.",
    "Corner a favor, despeja el primer palo.",
    "Pase al hueco, llega antes el portero.",
    "Remate de cabeza, gran parada.",
    "Balon suelto en el area, nadie empuja.",
    "Buena pared por dentro, remate bloqueado.",
    "Falta peligrosa al borde del area rival.",
]

_COMM_ATT_RIVAL = [
    "El rival llega por banda, centro pasado.",
    "Disparo rival desde media distancia, fuera.",
    "Corner en contra, despeja tu central.",
    "Pase filtrado rival, salva tu portero.",
    "Remate visitante, toca en un defensor.",
    "Balon muerto en tu area, consigues sacar.",
    "Combinacion rival en frontal, chute alto.",
    "Falta lateral para el rival, respiras al despejar.",
]

_COMM_MID = [
    "Duelo fisico en el centro del campo.",
    "La aficion aprieta y el ritmo sube.",
    "Balon dividido en la medular.",
    "El arbitro corta una transicion por falta.",
    "Intercambio de posesion sin dominador claro.",
    "El partido entra en fase de ida y vuelta.",
    "Los dos equipos ajustan lineas en medio campo.",
    "Minutos de control con tension tactica.",
]

def _squad_rows(players: list["Player"]):
    """Split squad into GK / DEF / MID / ATT for pitch display (â‰¤4 each)."""
    gk = [p for p in players if p.is_goalkeeper]
    df = [p for p in players if not p.is_goalkeeper and
          ("Back" in p.position or
           ("Centre" in p.position and "Midfield" not in p.position))]
    md = [p for p in players if "Midfield" in p.position or "Midfielder" in p.position]
    at = [p for p in players if "Forward" in p.position or "Winger" in p.position
          or "Attacker" in p.position or "Centre-Forward" in p.position]
    known = {p.name for p in gk + df + md + at}
    md += [p for p in players if p.name not in known and not p.is_goalkeeper]
    return gk[:1], df[:4], md[:4], at[:3]


def _draw_frame(
    home: "Team", away: "Team", is_home_mgr: bool,
    hg: int, ag: int,
    minute,           # int or str (e.g. "45+2", "HT", "FT", "90+3")
    ball_zone: int,   # 0=opp_goal  1=opp_def  2=midfield  3=our_def  4=our_goal
    message: str = "",
):
    """Clear terminal and draw one animated pitch frame."""
    opp = away if is_home_mgr else home
    mgr = home if is_home_mgr else away

    opp_gk, opp_df, opp_md, opp_at = _squad_rows(opp.players)
    mgr_gk, mgr_df, mgr_md, mgr_at = _squad_rows(mgr.players)

    mgr_sc = hg if is_home_mgr else ag
    opp_sc = ag if is_home_mgr else hg
    sc     = GREEN if mgr_sc > opp_sc else (RED if mgr_sc < opp_sc else CYAN)
    if isinstance(minute, int):
        phase   = "1Âª" if minute <= 45 else "2Âª"
        min_str = f"{minute:02d}'"
    else:
        phase   = "2Âª" if str(minute).startswith("90") else "1Âª"
        min_str = str(minute)
    score  = f"{hg}-{ag}" if is_home_mgr else f"{ag}-{hg}"

    os.system("cls" if os.name == "nt" else "clear")

    BALL = "âš½"

    def tokens(players, n=4):
        ts = [f"[{p.name.split()[-1][:4]:^4}]" for p in players[:n]]
        return "  ".join(ts) if ts else "[   â€”   ]"

    def prow(players, color, n=4, ball=False):
        bm = f" {BALL} " if ball else "   "
        print(f"   {bm}{_c(color, tokens(players, n))}")

    def gkrow(gk_list, color, ball=False):
        nm = gk_list[0].name.split()[-1][:8] if gk_list else "---"
        bm = f" {BALL} " if ball else "   "
        print(f"   {bm}{_c(color, f'[ GK Â· {nm} ]')}")

    print()
    print(_c(BOLD + CYAN, f"  {'â•'*52}"))
    print(f"  {home.name[:18]:<18}  {phase} {min_str}  {_c(sc+BOLD, score)}  {away.name[:18]}")
    print(_c(BOLD + CYAN, f"  {'â•'*52}"))
    print()

    # â”€â”€ Opponent half (top) â”€â”€
    gkrow(opp_gk, GRAY,   ball=(ball_zone == 0))
    prow (opp_df, GRAY,   ball=(ball_zone == 1))
    prow (opp_md, GRAY,   ball=False)
    prow (opp_at, GRAY,   ball=(ball_zone == 2))
    print()

    # â”€â”€ Centre line â”€â”€
    ml   = list("Â· " * 24)
    if ball_zone == 2:
        ml[len(ml)//2] = BALL
    print(_c(GRAY, f"   {''.join(ml)[:48]}"))
    print()

    # â”€â”€ Manager half (bottom) â”€â”€
    prow (mgr_at, YELLOW, ball=(ball_zone == 2))
    prow (mgr_md, YELLOW, ball=False)
    prow (mgr_df, YELLOW, ball=(ball_zone == 3))
    gkrow(mgr_gk, YELLOW, ball=(ball_zone == 4))
    print()

    print(_c(BOLD + CYAN,   f"  {'â•'*52}"))
    print(_c(YELLOW + BOLD, f"  â–º {mgr.name}"))
    print(_c(BOLD + CYAN,   f"  {'â•'*52}"))

    if message:
        print()
        print(f"  {message}")
    print()


def _ball_drift(zone: int, rng: random.Random, our_str: float, opp_str: float) -> int:
    """Random-walk the ball one step, biased toward the stronger team's goal."""
    total = max(our_str + opp_str, 0.01)
    p_ours = our_str / total          # prob ball moves toward opp goal (zone 0)
    r = rng.random()
    if r < p_ours * 0.55:
        return max(0, zone - 1)
    elif r < p_ours * 0.55 + (1 - p_ours) * 0.55:
        return min(4, zone + 1)
    return zone


def _entrenador_substitution(mgr_team: "Team", subs_made: list) -> bool:
    """Cosmetic substitution during entrenador mode."""
    squad = sorted(mgr_team.players, key=lambda p: p.overall, reverse=True)
    if len(squad) < 2:
        print(_c(GRAY, "  No hay suficientes jugadores.\n"))
        return False
    print(_c(YELLOW + BOLD, "\n  â”€â”€ SUSTITUCIÃ“N â”€â”€"))
    print(_c(GRAY, f"  {'#':>3}  {'JUGADOR':<26} {'POSICIÃ“N':<18} {'ME':>3}"))
    print(_c(GRAY, "  " + "â”€" * 52))
    for i, p in enumerate(squad, 1):
        tag = _c(RED, " Â·saleÂ·") if p.name in subs_made else ""
        print(f"  {i:>3}  {p.name[:25]:<26} {p.position[:17]:<18} {p.me:>3}{tag}")
    print()
    out_i = input_int(f"  Sale  (1-{len(squad)}, 0=cancelar): ", 0, len(squad))
    if out_i == 0:
        return False
    in_i = input_int(f"  Entra (1-{len(squad)}, 0=cancelar): ", 0, len(squad))
    if in_i == 0 or in_i == out_i:
        return False
    subs_made.append(squad[out_i - 1].name)
    print(_c(GREEN, f"  âœ“ Sale {squad[out_i-1].name}  â†’  Entra {squad[in_i-1].name}\n"))
    return True


def _match_entrenador(
    home: "Team", away: "Team", seed: int,
    mgr_slot: int, data: dict,
) -> tuple:
    """
    Modo entrenador avanzado.
    El partido se genera minuto a minuto y las decisiones en vivo del manager
    modifican ritmo, ocasiones, conversion y riesgo disciplinario.
    """
    tactic_ref = [data.get("tactic", dict(DEFAULT_TACTIC))]
    rng = random.Random(seed)

    is_home_mgr = home.slot_id == mgr_slot
    mgr_team_obj = home if is_home_mgr else away

    home_base = home.strength() * 1.05
    away_base = away.strength()
    our_base = home_base if is_home_mgr else away_base
    opp_base = away_base if is_home_mgr else home_base
    comp_goal_factor = (_competition_goal_factor(home.comp) + _competition_goal_factor(away.comp)) * 0.5

    # Mutable state refs
    cur = [0, 0]              # score home/away
    ball = [2]                # 0..4 (towards manager attack -> 0)
    subs = [5]
    wins = [3]
    concuss = [True]          # one extra concussion sub
    smade: list[str] = []

    halfdone = [False]
    yellows = [0, 0]
    reds = [0, 0]             # [manager, rival]
    var_rev = [0, 0]
    goals_h = [0, 0]
    subs_h = [0, 0]

    attack_boost = [0]
    defend_boost = [0]
    press_boost = [0]
    calm_boost = [0]
    momentum = [0.0]          # positive favours manager

    speed_mode_raw = os.getenv("PCF_COACH_SPEED", "human").strip().lower()
    if speed_mode_raw in ("fast", "qa", "bot"):
        speed_mode = "FAST"
        TICK = 0.11
        sleep_scale = 0.40
    else:
        # Modo humano: experiencia de 5-8 minutos por partido aprox.
        speed_mode = "HUMAN"
        TICK = 3.5
        sleep_scale = 1.0

    if speed_mode == "HUMAN":
        # Partidos más cargados de sucesos y narración frecuente.
        event_boost = 1.50
        narration_every_minutes = max(1, int(round(10.0 / max(TICK, 0.01))))
        micro_event_base = 0.14
    else:
        event_boost = 1.0
        narration_every_minutes = 0
        micro_event_base = 0.04
    last_narration_minute = [0]

    def _wait(seconds: float):
        if seconds <= 0:
            return
        time.sleep(seconds * sleep_scale)

    def clamp(v: float, lo: float, hi: float) -> float:
        return max(lo, min(hi, v))

    def manager_score() -> int:
        return cur[0] if is_home_mgr else cur[1]

    def rival_score() -> int:
        return cur[1] if is_home_mgr else cur[0]

    def manager_leading() -> bool:
        return manager_score() > rival_score()

    def current_half_idx() -> int:
        return 1 if halfdone[0] else 0

    def order_status() -> str:
        st = []
        if attack_boost[0] > 0:
            st.append(f"Ataque total {attack_boost[0]}'")
        if defend_boost[0] > 0:
            st.append(f"Bloque bajo {defend_boost[0]}'")
        if press_boost[0] > 0:
            st.append(f"Presion alta {press_boost[0]}'")
        if calm_boost[0] > 0:
            st.append(f"Calmar juego {calm_boost[0]}'")
        return " | ".join(st) if st else "Sin orden especial"

    def draw(minute, msg: str = ""):
        status = _c(CYAN, f"Ordenes: {order_status()} · Mom {momentum[0]:+.2f} · Rojas {reds[0]}-{reds[1]}")
        payload = (msg + "\n  " + status) if msg else status
        _draw_frame(home, away, is_home_mgr, cur[0], cur[1], minute, ball[0], payload)

    def narration_message(minute: int) -> str:
        if narration_every_minutes <= 0:
            return ""
        if minute - last_narration_minute[0] < narration_every_minutes:
            return ""
        last_narration_minute[0] = minute
        if ball[0] <= 1:
            msg = rng.choice(_COMM_ATT_OUR)
        elif ball[0] >= 3:
            msg = rng.choice(_COMM_ATT_RIVAL)
        else:
            msg = rng.choice(_COMM_MID)
        return _c(GRAY, f"NARRADOR: {msg}")

    def decay_orders():
        for ref in (attack_boost, defend_boost, press_boost, calm_boost):
            if ref[0] > 0:
                ref[0] -= 1

    def set_live_order(code: str, minute) -> bool:
        code = code.strip().upper()
        if code == "A":
            attack_boost[0] = 10
            defend_boost[0] = 0
            calm_boost[0] = 0
            momentum[0] += 0.12
            draw(minute, _c(BOLD + YELLOW, "ORDEN: Todo al ataque (10')"))
            _wait(0.9)
            return True
        if code == "D":
            defend_boost[0] = 10
            attack_boost[0] = 0
            press_boost[0] = 0
            momentum[0] -= 0.05
            draw(minute, _c(BOLD + CYAN, "ORDEN: Bloque bajo (10')"))
            _wait(0.9)
            return True
        if code == "P":
            press_boost[0] = 8
            calm_boost[0] = 0
            momentum[0] += 0.08
            draw(minute, _c(BOLD + YELLOW, "ORDEN: Presion alta (8') · mas riesgo de amarillas"))
            _wait(0.9)
            return True
        if code == "C":
            calm_boost[0] = 8
            press_boost[0] = 0
            momentum[0] += 0.03 if manager_leading() else -0.03
            draw(minute, _c(BOLD + CYAN, "ORDEN: Calmar partido / pausa (8')"))
            _wait(0.9)
            return True
        return False

    def sub_window(half_idx: int):
        while subs[0] > 0:
            if not _entrenador_substitution(mgr_team_obj, smade):
                break
            subs[0] -= 1
            subs_h[half_idx] += 1
            momentum[0] += 0.06
            if subs[0] == 0:
                break
            nx = input(_c(CYAN, f"  Otro cambio? ({subs[0]} restantes) [S=Si / Intro=No]: ")).strip().upper()
            if nx != "S":
                break
        wins[0] -= 1

    def offer_stop(minute, msg: str):
        draw(minute, msg)
        print()
        if wins[0] > 0 and subs[0] > 0:
            print(_c(CYAN, f"  W. Ventana de cambios ({wins[0]} ventanas · {subs[0]} cambios)"))
        print(_c(CYAN, "  A. Todo al ataque (10')"))
        print(_c(CYAN, "  D. Bloque bajo (10')"))
        print(_c(CYAN, "  P. Presion alta (8', mas tarjetas)"))
        print(_c(CYAN, "  C. Calmar partido (8')"))
        print(_c(CYAN, "  T. Cambiar tactica completa"))
        print(_c(CYAN, "  0. Continuar"))
        ch = input("  > ").strip().upper()

        half_idx = current_half_idx()
        if ch == "W" and wins[0] > 0 and subs[0] > 0:
            sub_window(half_idx)
        elif ch == "T":
            _tactic_menu(data)
            tactic_ref[0] = data.get("tactic", tactic_ref[0])
        else:
            set_live_order(ch, minute)

    def register_goal(scored_by_manager: bool, minute, source: str):
        half_idx = current_half_idx()

        if scored_by_manager:
            if is_home_mgr:
                cur[0] += 1
            else:
                cur[1] += 1
            gc = GREEN
            tname = mgr_team_obj.name
            momentum[0] += 0.75
        else:
            if is_home_mgr:
                cur[1] += 1
            else:
                cur[0] += 1
            gc = RED
            tname = away.name if is_home_mgr else home.name
            momentum[0] -= 0.75

        goals_h[half_idx] += 1
        draw(minute, _c(gc + BOLD, f"GOOOL {tname} ({source})  ->  {cur[0]}-{cur[1]}"))
        _wait(1.3)

        # VAR review
        if rng.random() < 0.15:
            var_rev[half_idx] += 1
            draw(minute, _c(BOLD + CYAN, "VAR revisando la accion..."))
            _wait(1.0)
            if rng.random() < 0.32:
                if scored_by_manager:
                    if is_home_mgr:
                        cur[0] -= 1
                    else:
                        cur[1] -= 1
                    momentum[0] -= 0.35
                    dc = RED
                else:
                    if is_home_mgr:
                        cur[1] -= 1
                    else:
                        cur[0] -= 1
                    momentum[0] += 0.35
                    dc = GREEN
                goals_h[half_idx] -= 1
                draw(minute, _c(dc + BOLD, "GOL ANULADO por VAR"))
                ball[0] = 2
                _wait(1.1)
                return
            draw(minute, _c(GREEN + BOLD, "GOL confirmado por VAR"))
            _wait(0.9)

        offer_stop(minute, _c(gc + BOLD, f"Tras el gol de {tname}: decide rapido."))
        ball[0] = 2

    def maybe_card(minute):
        half_idx = current_half_idx()
        faltas = int(tactic_ref[0].get("faltas", 2))
        p_card = (0.008 + (0.004 if faltas == 3 else 0.0) + (0.004 if press_boost[0] > 0 else 0.0)) * event_boost
        if rng.random() >= p_card:
            return

        manager_team_card = rng.random() < (0.52 + (0.10 if faltas == 3 else 0.0))
        if manager_team_card:
            yellows[half_idx] += 1
            draw(minute, _c(YELLOW, f"Amarilla para {mgr_team_obj.name} ({minute}')"))
            _wait(0.6)

            p_red = 0.06 + (0.08 if faltas == 3 else 0.0) + (0.05 if press_boost[0] > 0 else 0.0)
            if rng.random() < p_red:
                reds[0] += 1
                momentum[0] -= 0.45
                draw(minute, _c(RED + BOLD, f"ROJA para {mgr_team_obj.name}. Te quedas con {11 - reds[0]}."))
                _wait(0.9)
                offer_stop(minute, _c(RED + BOLD, "Con 10 (o menos): ajusta el plan."))
        else:
            yellows[half_idx] += 1
            rival_name = away.name if is_home_mgr else home.name
            draw(minute, _c(GRAY, f"Amarilla para {rival_name} ({minute}')"))
            _wait(0.5)
            if rng.random() < 0.07:
                reds[1] += 1
                momentum[0] += 0.35
                draw(minute, _c(GREEN + BOLD, f"ROJA para {rival_name}. Juegan con {11 - reds[1]}."))
                _wait(0.9)
                offer_stop(minute, _c(GREEN + BOLD, "Rival con uno menos: quieres apretar o pausar?"))

    def maybe_injury(minute):
        half_idx = current_half_idx()
        p_injury = (0.0025 + (0.0015 if press_boost[0] > 0 else 0.0)) * (1.20 if speed_mode == "HUMAN" else 1.0)
        if rng.random() >= p_injury:
            return

        manager_side = rng.random() < 0.5
        if manager_side and concuss[0]:
            draw(minute, _c(RED + BOLD, "Golpe en la cabeza en tu equipo. Tienes cambio extra."))
            if _entrenador_substitution(mgr_team_obj, smade):
                concuss[0] = False
                subs_h[half_idx] += 1
                momentum[0] -= 0.10
        elif manager_side:
            draw(minute, _c(GRAY, "Golpe leve en tu equipo. Siguen todos."))
            momentum[0] -= 0.08
        else:
            draw(minute, _c(GRAY, "Golpe leve en el rival. El juego sigue."))
            momentum[0] += 0.05
        _wait(0.7)

    def maybe_time_wasting(minute):
        manager_waste_on = int(tactic_ref[0].get("perdidaTiempo", 0)) == 1
        if manager_waste_on and manager_leading():
            p_manager = 0.045 if speed_mode == "HUMAN" else 0.020
            if minute < 60:
                p_manager *= 0.60
            if rng.random() < p_manager:
                draw(minute, _c(CYAN, "Pierdes tiempo: saques lentos y pausas en banda."))
                momentum[0] += 0.03
                _wait(0.25 if speed_mode == "HUMAN" else 0.10)
                return

        if manager_score() < rival_score() and minute >= 70:
            p_rival = 0.040 if speed_mode == "HUMAN" else 0.018
            if rng.random() < p_rival:
                draw(minute, _c(GRAY, "El rival retrasa la reanudacion y enfria el partido."))
                momentum[0] -= 0.04
                _wait(0.25 if speed_mode == "HUMAN" else 0.10)

    def minute_strengths() -> tuple[float, float, float]:
        our_adj = _tactic_adj(tactic_ref[0], is_home=False)

        tempo = 1.0
        if calm_boost[0] > 0:
            tempo *= 0.82
        if press_boost[0] > 0:
            tempo *= 1.18
        if int(tactic_ref[0].get("perdidaTiempo", 0)) == 1 and manager_leading():
            tempo *= 0.78

        our_live = our_base + our_adj + momentum[0] * 1.0
        opp_live = opp_base - momentum[0] * 0.75

        if attack_boost[0] > 0:
            our_live += 1.7
            opp_live += 0.9
        if defend_boost[0] > 0:
            our_live -= 0.6
            opp_live -= 0.8
        if press_boost[0] > 0:
            our_live += 0.8
            opp_live -= 0.4

        our_live *= max(0.62, 1.0 - reds[0] * 0.10)
        opp_live *= max(0.62, 1.0 - reds[1] * 0.10)

        return max(10.0, our_live), max(10.0, opp_live), tempo

    def maybe_chance(minute):
        our_live, opp_live, tempo = minute_strengths()
        ball[0] = _ball_drift(ball[0], rng, our_live, opp_live)

        zone_our = [1.90, 1.40, 1.00, 0.64, 0.35][ball[0]]
        zone_opp = [0.35, 0.64, 1.00, 1.40, 1.90][ball[0]]
        ratio = our_live / max(our_live + opp_live, 0.01)

        p_our = min(0.42, 0.020 * zone_our * (0.85 + ratio * 0.9) * tempo * event_boost * comp_goal_factor)
        p_opp = min(0.42, 0.020 * zone_opp * (0.85 + (1.0 - ratio) * 0.9) * tempo * event_boost * comp_goal_factor)

        r = rng.random()
        if r < p_our:
            conv = 0.18 + (our_live - opp_live) / 220.0
            if attack_boost[0] > 0:
                conv += 0.05
            if defend_boost[0] > 0:
                conv -= 0.03
            if calm_boost[0] > 0:
                conv -= 0.02
            conv *= comp_goal_factor
            conv = clamp(conv, 0.07, 0.62)

            if rng.random() < conv:
                register_goal(True, minute, "jugada")
            else:
                msg = rng.choice(_COMM_ATT_OUR)
                draw(minute, _c(YELLOW, f"Ocasion tuya: {msg}"))
                _wait(0.35)
            return

        if r < p_our + p_opp:
            conv = 0.18 + (opp_live - our_live) / 220.0
            if defend_boost[0] > 0:
                conv -= 0.04
            if attack_boost[0] > 0:
                conv += 0.04
            conv *= comp_goal_factor
            conv = clamp(conv, 0.07, 0.60)

            if rng.random() < conv:
                register_goal(False, minute, "contra rival")
            else:
                draw(minute, _c(GRAY, f"Rival avisa: {rng.choice(_COMM_ATT_RIVAL)}"))
                _wait(0.30)
            return

        # Sin ocasion clara, pero mantenemos continuidad narrativa del partido.
        p_live = micro_event_base if ball[0] != 2 else micro_event_base * 0.55
        if rng.random() < p_live:
            if ball[0] <= 1:
                draw(minute, _c(YELLOW, rng.choice(_COMM_ATT_OUR)))
            elif ball[0] >= 3:
                draw(minute, _c(GRAY, rng.choice(_COMM_ATT_RIVAL)))
            else:
                draw(minute, _c(GRAY, rng.choice(_COMM_MID)))
            _wait(0.25 if speed_mode == "HUMAN" else 0.10)

    # Kickoff
    draw(1, _c(GRAY, f"[Intro] para comenzar... ({speed_mode})"))
    input()

    # 1st half
    for minute in range(1, 46):
        maybe_chance(minute)
        maybe_card(minute)
        maybe_injury(minute)
        maybe_time_wasting(minute)

        if minute in (25, 30, 40):
            offer_stop(minute, _c(CYAN, "Parada tactica de banquillo."))
        narr = narration_message(minute)
        draw(minute, narr if narr else "")
        _wait(TICK)

        decay_orders()
        momentum[0] *= 0.92

    # Added time 1st half
    at1 = max(1, min(6, 1 + yellows[0] + var_rev[0] * 2 + goals_h[0] + reds[0] + reds[1] + rng.randint(0, 1)))
    draw(f"45+{at1}", _c(BOLD + YELLOW, f"Tiempo anadido: +{at1}"))
    _wait(1.0)
    for a in range(1, at1 + 1):
        draw(f"45+{a}")
        _wait(TICK * 0.6)

    # Halftime
    halfdone[0] = True
    draw("HT", _c(BOLD + YELLOW, "DESCANSO"))
    print()
    while True:
        print(_c(CYAN, "  T. Cambiar tactica"))
        if subs[0] > 0:
            print(_c(CYAN, f"  S. Sustitucion ({subs[0]} cambios, ventana gratis de descanso)"))
        print(_c(CYAN, "  A. Todo al ataque (10')"))
        print(_c(CYAN, "  D. Bloque bajo (10')"))
        print(_c(CYAN, "  P. Presion alta (8')"))
        print(_c(CYAN, "  C. Calmar partido (8')"))
        print(_c(CYAN, "  0. Empezar 2a parte"))
        ch = input("  Opcion: ").strip().upper()
        if ch in ("", "0"):
            break
        if ch == "T":
            _tactic_menu(data)
            tactic_ref[0] = data.get("tactic", tactic_ref[0])
            continue
        if ch == "S" and subs[0] > 0:
            while subs[0] > 0:
                if not _entrenador_substitution(mgr_team_obj, smade):
                    break
                subs[0] -= 1
                subs_h[1] += 1
                if subs[0] == 0:
                    break
                nx = input(_c(CYAN, f"  Otro cambio? ({subs[0]} restantes) [S/N]: ")).strip().upper()
                if nx != "S":
                    break
            continue
        set_live_order(ch, "HT")

    ball[0] = 2

    # 2nd half
    for minute in range(46, 91):
        maybe_chance(minute)
        maybe_card(minute)
        maybe_injury(minute)
        maybe_time_wasting(minute)

        if minute in (55, 60, 70, 80) and wins[0] > 0 and subs[0] > 0:
            offer_stop(minute, _c(CYAN, f"min {minute}: ventana de decisiones"))
        elif minute in (75, 85):
            offer_stop(minute, _c(CYAN, f"min {minute}: tramo clave, decide."))
        narr = narration_message(minute)
        draw(minute, narr if narr else "")
        _wait(TICK)

        decay_orders()
        momentum[0] *= 0.92

    # Added time 2nd half
    at2 = max(2, min(10,
           2 + yellows[1] + var_rev[1] * 2 + goals_h[1]
           + int(subs_h[1] * 0.5)
           + reds[0] + reds[1]
           + (2 if int(tactic_ref[0].get("perdidaTiempo", 0)) == 1 else 0)
           + rng.randint(0, 2)))
    draw(90, _c(BOLD + YELLOW, f"Tiempo anadido: +{at2}"))
    _wait(1.1)
    for a in range(1, at2 + 1):
        draw(f"90+{a}")
        _wait(TICK * 0.5)

    fhg, fag = cur[0], cur[1]
    won = (is_home_mgr and fhg > fag) or (not is_home_mgr and fag > fhg)
    lost = (is_home_mgr and fhg < fag) or (not is_home_mgr and fag < fhg)
    rc = GREEN if won else (RED if lost else CYAN)
    res = "VICTORIA" if won else ("DERROTA" if lost else "EMPATE")
    draw("FT", _c(rc + BOLD, f"PITIDO FINAL · {res} · {fhg}-{fag}"))
    input(_c(GRAY, "  [Intro] para continuar..."))
    return fhg, fag



# ---- Transfer market -------------------------------------------------------

def _init_budget(team: Team, liga1: list[Team], liga2: list[Team]) -> int:
    if team.comp == "ES1":
        ranked = sorted(liga1, key=lambda t: t.strength(), reverse=True)
        rank   = next((i for i, t in enumerate(ranked) if t.slot_id == team.slot_id), 10)
        scale  = [40,35,30,25,20,18,16,14,12,10, 9, 8, 8, 8, 8, 8, 8, 8, 8, 8]
    else:
        ranked = sorted(liga2, key=lambda t: t.strength(), reverse=True)
        rank   = next((i for i, t in enumerate(ranked) if t.slot_id == team.slot_id), 15)
        scale  = [10, 8, 7, 6, 5, 5, 4, 4, 4, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2]
    return scale[min(rank, len(scale) - 1)] * 1_000_000


def _is_window_open(matchday: int, total_md: int) -> bool:
    w_start = 19 if total_md == 38 else 22
    w_end   = 23 if total_md == 38 else 26
    return matchday <= 5 or w_start <= matchday <= w_end


def _apply_squad_changes(mgr_team: Team, all_slots: dict[int, Team], data: dict):
    """Aplica fichajes/ventas persistidos al objeto Team en memoria."""
    sold = set(data.get("sold", []))
    mgr_team.players = [p for p in mgr_team.players if p.name not in sold]
    existing = {p.name for p in mgr_team.players}
    for b in data.get("bought", []):
        if b["player_name"] in existing:
            continue
        src = all_slots.get(b["from_slot"])
        if not src:
            continue
        player = next((p for p in src.players if p.name == b["player_name"]), None)
        if player:
            src.players  = [p for p in src.players if p.name != player.name]
            mgr_team.players.append(player)
            existing.add(player.name)


def _market_buy(data: dict, mgr_team: Team, all_slots: dict[int, Team],
                liga1: list[Team], liga2: list[Team]):
    budget    = data.get("budget", 0)
    _ensure_president_profile(data, mgr_team)
    mgr_names = {p.name for p in mgr_team.players}

    avail: list[tuple[Team, Player]] = [
        (t, p)
        for t in liga1 + liga2
        if t.slot_id != mgr_team.slot_id
        for p in t.players
        if p.name not in mgr_names
    ]

    print(_c(CYAN, "\n  Filtrar por posiciÃ³n: 1.Todos  2.Porteros  3.Defensas  4.Medios  5.Delanteros"))
    pf = input_int("  Filtro (1-5): ", 1, 5)
    filters = {
        2: lambda p: p.is_goalkeeper,
        3: lambda p: "Back" in p.position,
        4: lambda p: "Midfield" in p.position,
        5: lambda p: "Forward" in p.position or "Winger" in p.position or "Attacker" in p.position,
    }
    if pf in filters:
        avail = [(t, p) for t, p in avail if filters[pf](p)]

    affordable = sorted([(t, p) for t, p in avail if p.market_value <= budget],
                        key=lambda x: x[1].overall, reverse=True)
    pricey     = sorted([(t, p) for t, p in avail if p.market_value > budget],
                        key=lambda x: x[1].overall, reverse=True)
    candidates = affordable[:15] + pricey[:5]

    if not candidates:
        print(_c(GRAY, "  No hay jugadores disponibles.\n"))
        return

    print(_c(BOLD + YELLOW, f"\n  Jugadores disponibles (presupuesto: â‚¬{budget:,.0f})"))
    print(_c(GRAY, f"  {'#':>3}  {'JUGADOR':<24} {'EQUIPO':<22} {'ME':>3} {'VALOR':<12}"))
    print(_c(GRAY, "  " + "â”€" * 68))
    for i, (t, p) in enumerate(candidates, 1):
        val   = f"â‚¬{p.market_value/1e6:.1f}M" if p.market_value >= 1e6 else f"â‚¬{p.market_value//1000}K"
        color = RESET if p.market_value <= budget else GRAY
        print(color + f"  {i:>3}  {p.name[:23]:<24} {t.name[:21]:<22} {p.me:>3} {val}" + RESET)
    print()

    idx = input_int(f"  Fichar (1-{len(candidates)}, 0=cancelar): ", 0, len(candidates))
    if idx == 0:
        return
    src_team, player = candidates[idx - 1]
    fee = player.market_value
    if fee > budget:
        print(_c(RED, f"  Sin presupuesto (necesitas â‚¬{fee:,.0f}, tienes â‚¬{budget:,.0f}).\n"))
        return
    cap_ok, projected_k, cap_k = _can_register_with_cap(data, mgr_team, player)
    if not cap_ok:
        print(
            _c(
                RED,
                "  Operacion bloqueada por tope salarial: "
                f"masa proyectada â‚¬{projected_k:,}K/sem > tope â‚¬{cap_k:,}K/sem.\n",
            )
        )
        return
    print(_c(YELLOW, f"\n  Fichar {player.name} de {src_team.name} por â‚¬{fee:,.0f}?"))
    if input_int("  1.Confirmar  0.Cancelar: ", 0, 1) == 0:
        return
    data["budget"] = budget - fee
    data.setdefault("bought", []).append({"player_name": player.name, "from_slot": src_team.slot_id, "paid": fee})
    src_team.players  = [p for p in src_team.players if p.name != player.name]
    mgr_team.players.append(player)
    _save_career(data)
    print(_c(GREEN, f"  âœ“ {player.name} fichado. Presupuesto restante: â‚¬{data['budget']:,.0f}\n"))


def _market_sell(data: dict, mgr_team: Team):
    if not mgr_team.players:
        print(_c(GRAY, "  Plantilla vacÃ­a.\n"))
        return
    spl = sorted(mgr_team.players, key=lambda p: p.overall, reverse=True)
    print(_c(BOLD + YELLOW, "\n  Vender jugador:"))
    print(_c(GRAY, f"  {'#':>3}  {'JUGADOR':<26} {'POS':<18} {'ME':>3}  VENTA(60%)"))
    print(_c(GRAY, "  " + "â”€" * 64))
    for i, p in enumerate(spl, 1):
        sale = int(p.market_value * 0.6)
        val  = f"â‚¬{sale/1e6:.1f}M" if sale >= 1e6 else f"â‚¬{sale//1000}K"
        print(f"  {i:>3}  {p.name[:25]:<26} {p.position[:17]:<18} {p.me:>3}  {val}")
    print()
    idx = input_int(f"  Vender (1-{len(spl)}, 0=cancelar): ", 0, len(spl))
    if idx == 0:
        return
    player = spl[idx - 1]
    sale   = int(player.market_value * 0.6)
    print(_c(YELLOW, f"\n  Vender {player.name} por â‚¬{sale:,.0f}?"))
    if input_int("  1.Confirmar  0.Cancelar: ", 0, 1) == 0:
        return
    data["budget"] = data.get("budget", 0) + sale
    data.setdefault("sold", []).append(player.name)
    mgr_team.players = [p for p in mgr_team.players if p.name != player.name]
    _save_career(data)
    print(_c(GREEN, f"  âœ“ {player.name} vendido. Presupuesto: â‚¬{data['budget']:,.0f}\n"))


def _market_menu(data: dict, mgr_team: Team, all_slots: dict[int, Team],
                 liga1: list[Team], liga2: list[Team]):
    cur_md   = data.get("current_matchday", 1)
    tot_md   = 38 if data["competition"] == "ES1" else 42
    window   = _is_window_open(cur_md, tot_md)
    budget   = data.get("budget", 0)
    profile  = _ensure_president_profile(data, mgr_team)
    bill_k   = _team_wage_bill_k(mgr_team)
    cap_k    = int(profile.get("salary_cap_k", 0))
    status   = _c(GREEN, "ABIERTA") if window else _c(RED, "CERRADA")

    print(_c(BOLD + YELLOW, "\n  â•â•â• MERCADO DE FICHAJES â•â•â•"))
    print(f"  {mgr_team.name}   |   Presupuesto: â‚¬{budget:,.0f}   |   Ventana: {status}")
    print(
        _c(
            GRAY,
            f"  Tope salarial: â‚¬{cap_k:,}K/sem  |  Masa actual: â‚¬{bill_k:,}K/sem  "
            f"({PRESIDENT_CAP_LABELS.get(profile.get('salary_cap_mode', 'BALANCED'), 'Equilibrado')})",
        )
    )
    print()
    while True:
        print(_c(CYAN, "  1. Buscar jugadores"))
        print(_c(CYAN, "  2. Vender jugador"))
        print(_c(CYAN, "  3. Ver mi plantilla"))
        print(_c(CYAN, "  0. Volver"))
        op = input_int("  OpciÃ³n: ", 0, 3)
        if op == 0:
            break
        elif op == 1:
            if not window:
                print(_c(RED, "  Ventana cerrada.\n"))
            else:
                _market_buy(data, mgr_team, all_slots, liga1, liga2)
        elif op == 2:
            if not window:
                print(_c(RED, "  Ventana cerrada.\n"))
            else:
                _market_sell(data, mgr_team)
        elif op == 3:
            print_squad(mgr_team)


def _winter_market_menu(data: dict, mgr_team: Team, comp_teams: list[Team], md: int):
    print(_c(BOLD + YELLOW, "\n  â•â•â• VENTANA DE MERCADO DE INVIERNO â•â•â•"))
    ans = input_str("  Â¿Quieres fichar algÃºn jugador? (S/N): ").strip().upper()
    if not ans.startswith("S"):
        print(_c(GRAY, "  ContinÃºa la temporada sin movimientos.\n"))
        return

    budget = int(data.get("budget", 0))
    _ensure_president_profile(data, mgr_team)
    mgr_names = {p.name for p in mgr_team.players}
    pool: list[tuple[Team, Player]] = [
        (t, p)
        for t in comp_teams
        if t.slot_id != mgr_team.slot_id
        for p in t.players
        if p.name not in mgr_names
    ]
    if not pool:
        print(_c(GRAY, "  No hay jugadores disponibles en esta ventana.\n"))
        return

    rng = random.Random(int(data.get("season_seed", 0)) ^ (md * 10007) ^ 0xA11CE)
    picks = rng.sample(pool, k=min(3, len(pool)))
    picks = sorted(picks, key=lambda item: item[1].overall, reverse=True)

    print(_c(GRAY, f"\n  Presupuesto disponible: â‚¬{budget:,.0f}"))
    print(_c(GRAY, f"  {'#':>3}  {'JUGADOR':<24} {'EQUIPO':<22} {'ME':>3} {'VALOR':<10}"))
    print(_c(GRAY, "  " + "â”€" * 66))
    for i, (team, player) in enumerate(picks, 1):
        val = f"â‚¬{player.market_value/1e6:.1f}M" if player.market_value >= 1_000_000 else f"â‚¬{player.market_value//1000}K"
        color = RESET if player.market_value <= budget else GRAY
        print(color + f"  {i:>3}  {player.name[:23]:<24} {team.name[:21]:<22} {player.me:>3} {val:<10}" + RESET)
    print()

    idx = input_int(f"  Fichar (1-{len(picks)}, 0=cancelar): ", 0, len(picks))
    if idx == 0:
        print(_c(GRAY, "  OperaciÃ³n cancelada.\n"))
        return
    src_team, player = picks[idx - 1]
    fee = int(player.market_value)
    if fee > budget:
        print(_c(RED, f"  Sin presupuesto (necesitas â‚¬{fee:,.0f}, tienes â‚¬{budget:,.0f}).\n"))
        return
    cap_ok, projected_k, cap_k = _can_register_with_cap(data, mgr_team, player)
    if not cap_ok:
        print(
            _c(
                RED,
                "  Operacion bloqueada por tope salarial: "
                f"masa proyectada â‚¬{projected_k:,}K/sem > tope â‚¬{cap_k:,}K/sem.\n",
            )
        )
        return

    if input_int("  1. Confirmar fichaje  0. Cancelar: ", 0, 1) == 0:
        print(_c(GRAY, "  OperaciÃ³n cancelada.\n"))
        return

    data["budget"] = budget - fee
    data.setdefault("bought", []).append({
        "player_name": player.name,
        "from_slot": src_team.slot_id,
        "paid": fee,
    })
    src_team.players = [p for p in src_team.players if p.name != player.name]
    mgr_team.players.append(player)
    _save_career(data)
    print(_c(GREEN, f"  âœ“ {player.name} fichado por â‚¬{fee:,.0f}. Presupuesto: â‚¬{data['budget']:,.0f}\n"))


def _init_copa_state(data: dict, liga1: list[Team], liga2: list[Team]) -> dict:
    top_l1 = sorted(liga1, key=lambda t: t.strength(), reverse=True)[:8]
    top_l2 = sorted(liga2, key=lambda t: t.strength(), reverse=True)[:8]
    participants = top_l1 + top_l2
    rng = random.Random(int(data.get("season_seed", 0)) ^ 0xC0FA)
    rng.shuffle(participants)
    return {
        "season": data.get("season"),
        "alive": [t.slot_id for t in participants],
        "round_index": 0,
        "rounds": [],
        "champion": None,
        "matchdays": [10, 20, 30, 38],
        "round_labels": ["OCTAVOS", "CUARTOS", "SEMIFINAL", "FINAL"],
    }


def _active_copa_round_label(copa: dict, md: int) -> str:
    if not isinstance(copa, dict):
        return ""
    idx = int(copa.get("round_index", 0))
    mds = copa.get("matchdays", [])
    labels = copa.get("round_labels", [])
    if idx < len(mds) and idx < len(labels) and md == int(mds[idx]):
        return str(labels[idx])
    return ""


def _play_copa_round(
    data: dict,
    md: int,
    teams_by_slot: dict[int, Team],
    mgr_slot: int,
    show_output: bool = False,
) -> list[dict]:
    copa = data.get("copa")
    if not isinstance(copa, dict):
        return []
    idx = int(copa.get("round_index", 0))
    matchdays = copa.get("matchdays", [])
    labels = copa.get("round_labels", [])
    if idx >= len(matchdays) or idx >= len(labels) or md != int(matchdays[idx]):
        return []

    alive = [sid for sid in copa.get("alive", []) if sid in teams_by_slot]
    if len(alive) < 2:
        return []

    rng = random.Random(int(data.get("season_seed", 0)) ^ (md * 1597) ^ (idx * 8191))
    rng.shuffle(alive)
    winners: list[int] = []
    matches: list[dict] = []
    tactic = data.get("tactic")

    for i in range(0, len(alive), 2):
        if i + 1 >= len(alive):
            winners.append(alive[i])
            continue
        hsid = alive[i]
        asid = alive[i + 1]
        home = teams_by_slot[hsid]
        away = teams_by_slot[asid]
        seed = int(data.get("season_seed", 0)) ^ (md * 7919 + hsid * 31 + asid * 17 + 0xC0FA)
        ht = tactic if hsid == mgr_slot else None
        at = tactic if asid == mgr_slot else None
        hg, ag = simulate_match(home, away, seed, home_tactic=ht, away_tactic=at)

        penalties = ""
        if hg > ag:
            winner = hsid
        elif ag > hg:
            winner = asid
        else:
            home_wins = rng.random() < 0.5
            winner = hsid if home_wins else asid
            penalties = f" (pen. {'5-4' if home_wins else '4-5'})"

        winners.append(winner)
        matches.append({
            "home": hsid,
            "away": asid,
            "hg": hg,
            "ag": ag,
            "winner": winner,
            "penalties": penalties,
        })

    label = str(labels[idx])
    copa.setdefault("rounds", []).append({"md": md, "label": label, "matches": matches})
    copa["alive"] = winners
    copa["round_index"] = idx + 1
    if len(winners) == 1:
        copa["champion"] = winners[0]
    data["copa"] = copa

    if show_output and matches:
        print(_c(BOLD + YELLOW, f"\n  â•â•â• COPA DEL REY Â· {label} â•â•â•"))
        for m in matches:
            h = teams_by_slot[m["home"]].name
            a = teams_by_slot[m["away"]].name
            w = teams_by_slot[m["winner"]].name
            print(f"  {h:<22} {m['hg']:>2} - {m['ag']:<2} {a}{m['penalties']}")
            print(_c(GRAY, f"     Clasifica: {w}"))
        print()

    news = data.setdefault("news", [])
    mgr_match = next((m for m in matches if m["home"] == mgr_slot or m["away"] == mgr_slot), None)
    if mgr_match:
        home_name = teams_by_slot[mgr_match["home"]].name
        away_name = teams_by_slot[mgr_match["away"]].name
        if mgr_match["winner"] == mgr_slot:
            news.append(f"J{md}: Copa {label} - {home_name} {mgr_match['hg']}-{mgr_match['ag']} {away_name}. Sigues vivo.")
        else:
            news.append(f"J{md}: Copa {label} - {home_name} {mgr_match['hg']}-{mgr_match['ag']} {away_name}. Eliminado.")

    if copa.get("champion") == mgr_slot:
        news.append(f"J{md}: Â¡Tu equipo gana la Copa del Rey!")

    _save_career(data)
    return matches


# ---- UEFA competitions ----------------------------------------------------

EURO_COMPETITIONS: list[tuple[str, str]] = [
    ("CEURO", "CHAMPIONS"),
    ("RECOPA", "EUROPA LEAGUE"),
    ("CUEFA", "CONFERENCE"),
]

EURO_ROUND_ORDER: list[str] = [
    "LP1", "LP2", "LP3", "LP4", "LP5", "LP6", "LP7", "LP8",
    "POF1", "POF2",
    "R16_1", "R16_2",
    "QF1", "QF2",
    "SF1", "SF2",
    "F",
]

EURO_LEAGUE_PRIORITY = [
    "ES1", "GB1", "IT1", "L1", "FR1", "NL1", "PO1", "BE1", "TR1",
    "ES2", "E3G1", "E3G2",
]


def _euro_comp_name(code: str) -> str:
    return dict(EURO_COMPETITIONS).get(code, code)


def _euro_round_label(round_code: str) -> str:
    if round_code.startswith("LP"):
        return f"LEAGUE PHASE {round_code[2:]}"
    labels = {
        "POF1": "PLAYOFF IDA",
        "POF2": "PLAYOFF VUELTA",
        "R16_1": "OCTAVOS IDA",
        "R16_2": "OCTAVOS VUELTA",
        "QF1": "CUARTOS IDA",
        "QF2": "CUARTOS VUELTA",
        "SF1": "SEMIFINAL IDA",
        "SF2": "SEMIFINAL VUELTA",
        "F": "FINAL",
    }
    return labels.get(round_code, round_code)


def _euro_empty_table_row() -> dict:
    return {"played": 0, "won": 0, "drawn": 0, "lost": 0, "gf": 0, "ga": 0, "pts": 0}


def _build_euro_schedule(total_matchdays: int) -> list[int]:
    start = 2
    end = max(start + len(EURO_ROUND_ORDER) - 1, total_matchdays - 3)
    end = min(total_matchdays, end)
    if end <= start:
        return [min(total_matchdays, start + i) for i in range(len(EURO_ROUND_ORDER))]

    step = (end - start) / float(max(1, len(EURO_ROUND_ORDER) - 1))
    out: list[int] = []
    prev = 0
    for idx in range(len(EURO_ROUND_ORDER)):
        md = int(round(start + idx * step))
        md = max(1, min(total_matchdays, md))
        if md <= prev:
            md = min(total_matchdays, prev + 1)
        out.append(md)
        prev = md
    return out


def _ranked_euro_pool(teams_by_slot: dict[int, Team]) -> list[int]:
    by_comp: dict[str, list[Team]] = {}
    for team in teams_by_slot.values():
        by_comp.setdefault(team.comp, []).append(team)

    ranked_by_comp: dict[str, list[Team]] = {}
    for comp, teams in by_comp.items():
        ranked_by_comp[comp] = sorted(teams, key=lambda t: t.strength(), reverse=True)

    pool: list[int] = []
    seen: set[int] = set()
    max_depth = max((len(v) for v in ranked_by_comp.values()), default=0)

    for depth in range(max_depth):
        for comp in EURO_LEAGUE_PRIORITY:
            team = ranked_by_comp.get(comp, [])
            if depth >= len(team):
                continue
            slot = team[depth].slot_id
            if slot not in seen:
                pool.append(slot)
                seen.add(slot)

    fallback = sorted(teams_by_slot.values(), key=lambda t: t.strength(), reverse=True)
    for team in fallback:
        if team.slot_id not in seen:
            pool.append(team.slot_id)
            seen.add(team.slot_id)
    return pool


def _build_league_phase_pairings(participants: list[int], seed: int) -> dict[str, list[list[int]]]:
    unique = list(dict.fromkeys(participants))
    if len(unique) % 2 == 1:
        unique = unique[:-1]
    if len(unique) < 2:
        return {}

    used_pairs: set[tuple[int, int]] = set()
    pairings: dict[str, list[list[int]]] = {}

    for round_idx in range(1, 9):
        round_code = f"LP{round_idx}"
        best_pairs: list[list[int]] = []
        best_dupes = 10**9

        for attempt in range(50):
            rng = random.Random(seed ^ (round_idx * 4099) ^ (attempt * 1315423911))
            order = unique[:]
            rng.shuffle(order)
            pairs: list[list[int]] = []
            dupes = 0

            while len(order) >= 2:
                a = order.pop(0)
                partner_idx = 0
                found_fresh = False
                for idx, b in enumerate(order):
                    key = (a, b) if a < b else (b, a)
                    if key not in used_pairs:
                        partner_idx = idx
                        found_fresh = True
                        break
                b = order.pop(partner_idx)
                if not found_fresh:
                    dupes += 1
                if (round_idx + len(pairs)) % 2 == 0:
                    home, away = a, b
                else:
                    home, away = b, a
                pairs.append([home, away])

            if dupes < best_dupes:
                best_dupes = dupes
                best_pairs = pairs
            if dupes == 0:
                break

        pairings[round_code] = best_pairs
        for home, away in best_pairs:
            key = (home, away) if home < away else (away, home)
            used_pairs.add(key)

    return pairings


def _tie_key(a: int, b: int) -> tuple[int, int]:
    return (a, b) if a < b else (b, a)


def _goals_for_team(match: dict, team_id: int) -> int:
    if int(match.get("home", -1)) == team_id:
        return int(match.get("hg", 0))
    if int(match.get("away", -1)) == team_id:
        return int(match.get("ag", 0))
    return 0


def _euro_table_ranking(comp_state: dict, teams_by_slot: dict[int, Team]) -> list[int]:
    table = comp_state.get("table", {})
    participants = [int(x) for x in comp_state.get("participants", []) if int(x) in teams_by_slot]

    def sort_key(slot_id: int):
        row = table.get(str(slot_id), _euro_empty_table_row())
        gd = int(row.get("gf", 0)) - int(row.get("ga", 0))
        strength = teams_by_slot[slot_id].strength() if slot_id in teams_by_slot else 0.0
        return (-int(row.get("pts", 0)), -gd, -int(row.get("gf", 0)), -strength, teams_by_slot[slot_id].name if slot_id in teams_by_slot else "")

    return sorted(participants, key=sort_key)


def _euro_two_leg_winners(comp_state: dict, first_leg: str, second_leg: str, seed: int) -> list[int]:
    results = comp_state.get("results_by_round", {})
    first = list(results.get(first_leg, []))
    second = list(results.get(second_leg, []))
    if not first or not second:
        return []

    second_map = {
        _tie_key(int(m.get("home", -1)), int(m.get("away", -1))): m
        for m in second
    }
    winners: list[int] = []

    for idx, m1 in enumerate(first):
        a = int(m1.get("home", -1))
        b = int(m1.get("away", -1))
        m2 = second_map.get(_tie_key(a, b))
        if not m2:
            continue
        a_goals = _goals_for_team(m1, a) + _goals_for_team(m2, a)
        b_goals = _goals_for_team(m1, b) + _goals_for_team(m2, b)
        if a_goals > b_goals:
            winner = a
        elif b_goals > a_goals:
            winner = b
        else:
            rng = random.Random(seed ^ (idx * 1013904223) ^ (a * 911 + b * 3571))
            winner = a if rng.random() < 0.5 else b
            if winner == a:
                m2["penalties"] = " (pen. 5-4)"
            else:
                m2["penalties"] = " (pen. 4-5)"
            m2["winner"] = winner
        winners.append(winner)

    return winners


def _ensure_comp_fixtures(
    comp_state: dict,
    round_code: str,
    seed: int,
    teams_by_slot: dict[int, Team],
) -> list[dict]:
    fixtures = comp_state.setdefault("fixtures", {})
    if round_code in fixtures:
        return list(fixtures.get(round_code, []))

    if round_code.startswith("LP"):
        lp = comp_state.get("lp_pairings", {})
        round_pairs = lp.get(round_code, [])
        built = [{"home": int(home), "away": int(away), "neutral": False} for home, away in round_pairs]
        fixtures[round_code] = built
        return built

    ranking = _euro_table_ranking(comp_state, teams_by_slot)

    if round_code == "POF1":
        playoff = ranking[8:24]
        built: list[dict] = []
        for i in range(8):
            if i >= len(playoff) or (len(playoff) - 1 - i) < 0:
                break
            better = playoff[i]
            lower = playoff[len(playoff) - 1 - i]
            built.append({"home": lower, "away": better, "neutral": False})
        fixtures["POF1"] = built
        fixtures["POF2"] = [{"home": m["away"], "away": m["home"], "neutral": False} for m in built]
        return built

    if round_code == "POF2":
        return list(fixtures.get("POF2", []))

    if round_code == "R16_1":
        top8 = ranking[:8]
        playoff_winners = _euro_two_leg_winners(comp_state, "POF1", "POF2", seed ^ 0x120F12)
        rng = random.Random(seed ^ 0x55AA)
        rng.shuffle(playoff_winners)
        built: list[dict] = []
        for seeded, challenger in zip(top8, playoff_winners[:8]):
            built.append({"home": challenger, "away": seeded, "neutral": False})
        fixtures["R16_1"] = built
        fixtures["R16_2"] = [{"home": m["away"], "away": m["home"], "neutral": False} for m in built]
        return built

    if round_code == "R16_2":
        return list(fixtures.get("R16_2", []))

    if round_code == "QF1":
        winners = _euro_two_leg_winners(comp_state, "R16_1", "R16_2", seed ^ 0x44DD)
        rng = random.Random(seed ^ 0x11BB)
        rng.shuffle(winners)
        built: list[dict] = []
        for i in range(0, len(winners), 2):
            if i + 1 >= len(winners):
                break
            a = winners[i]
            b = winners[i + 1]
            home, away = (a, b) if rng.random() < 0.5 else (b, a)
            built.append({"home": home, "away": away, "neutral": False})
        fixtures["QF1"] = built
        fixtures["QF2"] = [{"home": m["away"], "away": m["home"], "neutral": False} for m in built]
        return built

    if round_code == "QF2":
        return list(fixtures.get("QF2", []))

    if round_code == "SF1":
        winners = _euro_two_leg_winners(comp_state, "QF1", "QF2", seed ^ 0x2288)
        rng = random.Random(seed ^ 0x3377)
        rng.shuffle(winners)
        built: list[dict] = []
        for i in range(0, len(winners), 2):
            if i + 1 >= len(winners):
                break
            a = winners[i]
            b = winners[i + 1]
            home, away = (a, b) if rng.random() < 0.5 else (b, a)
            built.append({"home": home, "away": away, "neutral": False})
        fixtures["SF1"] = built
        fixtures["SF2"] = [{"home": m["away"], "away": m["home"], "neutral": False} for m in built]
        return built

    if round_code == "SF2":
        return list(fixtures.get("SF2", []))

    if round_code == "F":
        finalists = _euro_two_leg_winners(comp_state, "SF1", "SF2", seed ^ 0x7F7F7F)
        if len(finalists) >= 2:
            fixtures["F"] = [{"home": finalists[0], "away": finalists[1], "neutral": True}]
        else:
            fixtures["F"] = []
        return list(fixtures["F"])

    return []


def _init_euro_state(data: dict, teams_by_slot: dict[int, Team], total_matchdays: int) -> dict:
    season_seed = int(data.get("season_seed", 0))
    season = str(data.get("season", "2025-26"))
    schedule = _build_euro_schedule(total_matchdays)
    pool = _ranked_euro_pool(teams_by_slot)
    used: set[int] = set()
    cursor = 0
    competitions: dict[str, dict] = {}

    for idx, (code, _) in enumerate(EURO_COMPETITIONS):
        participants: list[int] = []
        while cursor < len(pool) and len(participants) < 36:
            sid = int(pool[cursor])
            cursor += 1
            if sid in used:
                continue
            participants.append(sid)
            used.add(sid)
        if len(participants) < 36:
            fallback = sorted(teams_by_slot.values(), key=lambda t: t.strength(), reverse=True)
            for team in fallback:
                if team.slot_id in used:
                    continue
                participants.append(team.slot_id)
                used.add(team.slot_id)
                if len(participants) >= 36:
                    break

        lp_seed = season_seed ^ (idx * 2654435761)
        competitions[code] = {
            "participants": participants,
            "round_index": 0,
            "rounds": [],
            "champion": None,
            "table": {str(sid): _euro_empty_table_row() for sid in participants},
            "lp_pairings": _build_league_phase_pairings(participants, lp_seed),
            "fixtures": {},
            "results_by_round": {},
        }

    return {
        "season": season,
        "schedule": schedule,
        "competitions": competitions,
    }


def _active_euro_round_labels(euro_state: dict, md: int) -> list[str]:
    if not isinstance(euro_state, dict):
        return []
    schedule = list(euro_state.get("schedule", []))
    comps = euro_state.get("competitions", {})
    labels: list[str] = []
    for code, _ in EURO_COMPETITIONS:
        state = comps.get(code, {})
        idx = int(state.get("round_index", 0))
        if idx >= len(EURO_ROUND_ORDER) or idx >= len(schedule):
            continue
        if int(schedule[idx]) != md:
            continue
        labels.append(f"{_euro_comp_name(code)}: {_euro_round_label(EURO_ROUND_ORDER[idx])}")
    return labels


def _play_euro_round(
    data: dict,
    md: int,
    teams_by_slot: dict[int, Team],
    mgr_slot: int,
    show_output: bool = False,
):
    euro = data.get("euro")
    if not isinstance(euro, dict):
        return

    schedule = list(euro.get("schedule", []))
    comps = euro.get("competitions", {})
    season_seed = int(data.get("season_seed", 0))
    tactic = data.get("tactic")
    news = data.setdefault("news", [])

    for comp_idx, (code, _) in enumerate(EURO_COMPETITIONS):
        comp = comps.get(code)
        if not isinstance(comp, dict):
            continue
        idx = int(comp.get("round_index", 0))
        if idx >= len(EURO_ROUND_ORDER) or idx >= len(schedule):
            continue
        if int(schedule[idx]) != md:
            continue

        round_code = EURO_ROUND_ORDER[idx]
        round_label = _euro_round_label(round_code)
        fixtures = _ensure_comp_fixtures(
            comp_state=comp,
            round_code=round_code,
            seed=season_seed ^ (comp_idx * 9187) ^ (idx * 1299721),
            teams_by_slot=teams_by_slot,
        )
        if not fixtures:
            comp["round_index"] = idx + 1
            continue

        round_results: list[dict] = []
        for fix_idx, fix in enumerate(fixtures):
            home_id = int(fix.get("home", -1))
            away_id = int(fix.get("away", -1))
            neutral = bool(fix.get("neutral", False))
            home = teams_by_slot.get(home_id)
            away = teams_by_slot.get(away_id)
            if not home or not away:
                continue

            match_seed = season_seed ^ (md * 10007) ^ (home_id * 31 + away_id * 17) ^ (fix_idx * 1009)
            ht = tactic if home_id == mgr_slot else None
            at = tactic if away_id == mgr_slot else None
            hg, ag = simulate_match(home, away, match_seed, home_tactic=ht, away_tactic=at)
            item = {
                "home": home_id,
                "away": away_id,
                "hg": hg,
                "ag": ag,
                "neutral": neutral,
                "penalties": "",
            }

            if round_code == "F" and hg == ag:
                rng = random.Random(match_seed ^ 0x4242)
                home_wins = rng.random() < 0.5
                item["winner"] = home_id if home_wins else away_id
                item["penalties"] = " (pen. 5-4)" if home_wins else " (pen. 4-5)"

            round_results.append(item)

            if round_code.startswith("LP"):
                table = comp.setdefault("table", {})
                home_row = table.setdefault(str(home_id), _euro_empty_table_row())
                away_row = table.setdefault(str(away_id), _euro_empty_table_row())
                home_row["played"] += 1
                away_row["played"] += 1
                home_row["gf"] += hg
                home_row["ga"] += ag
                away_row["gf"] += ag
                away_row["ga"] += hg
                if hg > ag:
                    home_row["won"] += 1
                    away_row["lost"] += 1
                    home_row["pts"] += 3
                elif ag > hg:
                    away_row["won"] += 1
                    home_row["lost"] += 1
                    away_row["pts"] += 3
                else:
                    home_row["drawn"] += 1
                    away_row["drawn"] += 1
                    home_row["pts"] += 1
                    away_row["pts"] += 1

        if not round_results:
            comp["round_index"] = idx + 1
            continue

        results_by_round = comp.setdefault("results_by_round", {})
        results_by_round[round_code] = round_results
        comp.setdefault("rounds", []).append(
            {"md": md, "label": round_label, "round": round_code, "matches": round_results}
        )
        comp["round_index"] = idx + 1

        if round_code == "F":
            final = round_results[0]
            if "winner" in final:
                winner = int(final.get("winner", -1))
            elif int(final.get("hg", 0)) > int(final.get("ag", 0)):
                winner = int(final.get("home", -1))
            else:
                winner = int(final.get("away", -1))
            comp["champion"] = winner

        mgr_match = next((m for m in round_results if int(m.get("home", -1)) == mgr_slot or int(m.get("away", -1)) == mgr_slot), None)
        if mgr_match:
            h_name = teams_by_slot.get(int(mgr_match["home"])).name
            a_name = teams_by_slot.get(int(mgr_match["away"])).name
            news.append(
                f"J{md}: {_euro_comp_name(code)} {round_label} - "
                f"{h_name} {mgr_match['hg']}-{mgr_match['ag']} {a_name}{mgr_match.get('penalties', '')}"
            )
        champion_slot = _safe_int(comp.get("champion", -1), -1)
        if champion_slot == mgr_slot:
            news.append(f"J{md}: {teams_by_slot[mgr_slot].name} gana {_euro_comp_name(code)}.")

        if show_output:
            print(_c(BOLD + YELLOW, f"\n  UEFA · {_euro_comp_name(code)} · {round_label}"))
            for m in round_results:
                h_name = teams_by_slot[int(m["home"])].name
                a_name = teams_by_slot[int(m["away"])].name
                print(f"  {h_name:<22} {m['hg']:>2} - {m['ag']:<2} {a_name}{m.get('penalties', '')}")
            print()

    data["euro"] = euro
    _save_career(data)


def _show_euro_status(data: dict, teams_by_slot: dict[int, Team], mgr_slot: int):
    euro = data.get("euro")
    if not isinstance(euro, dict):
        print(_c(GRAY, "\n  Competiciones UEFA no inicializadas.\n"))
        return

    schedule = list(euro.get("schedule", []))
    comps = euro.get("competitions", {})
    print(_c(BOLD + YELLOW, "\n  ═══ COMPETICIONES UEFA (FORMATO MODERNO) ═══"))
    for code, _ in EURO_COMPETITIONS:
        comp = comps.get(code, {})
        idx = int(comp.get("round_index", 0))
        champion = _safe_int(comp.get("champion", -1), -1)
        if champion in teams_by_slot:
            status = f"CAMPEON: {teams_by_slot[champion].name}"
        elif idx >= len(EURO_ROUND_ORDER):
            status = "FINALIZADO"
        else:
            rd = EURO_ROUND_ORDER[idx]
            md = schedule[idx] if idx < len(schedule) else "-"
            status = f"Siguiente: {_euro_round_label(rd)} (J{md})"
        print(_c(CYAN, f"  {dict(EURO_COMPETITIONS).get(code, code)}"))
        print(f"    {status}")

        if idx <= 8:
            ranking = _euro_table_ranking(comp, teams_by_slot)[:8]
            if ranking:
                leaders = ", ".join(teams_by_slot[s].name for s in ranking[:4] if s in teams_by_slot)
                print(_c(GRAY, f"    Top 4 fase liga: {leaders}"))
        if champion == mgr_slot:
            print(_c(GREEN, "    Tu equipo es el campeon."))
    print()


# ---- National team --------------------------------------------------------

NATIONAL_STAGES = ["CUARTOS", "SEMIFINAL", "FINAL"]
NATIONAL_EURO_RIVALS = ["Alemania", "Francia", "Inglaterra", "Italia", "Portugal", "Paises Bajos", "Croacia"]
NATIONAL_WORLD_RIVALS = ["Alemania", "Francia", "Brasil", "Argentina", "Inglaterra", "Italia", "Portugal"]


def _national_window_index(md: int) -> int:
    if 13 <= md <= 14:
        return 1
    if 26 <= md <= 27:
        return 2
    return 0


def _poisson_goals_std(lambda_value: float, rng: random.Random) -> int:
    lam = max(0.1, float(lambda_value))
    floor = math.exp(-lam)
    p = 1.0
    goals = -1
    while p > floor:
        p *= rng.random()
        goals += 1
        if goals >= 7:
            break
    return max(0, min(6, goals))


def _national_player_score(player: dict) -> float:
    ca = float(player.get("ca", 50))
    media = (
        float(player.get("ve", 50))
        + float(player.get("re", 50))
        + float(player.get("ag", 50))
        + float(player.get("remate", 50))
        + float(player.get("regate", 50))
        + float(player.get("pase", 50))
        + float(player.get("tiro", 50))
        + float(player.get("entrada", 50))
        + float(player.get("portero", 0))
    ) / 9.0
    return ca + media


def _build_national_squad(data: dict, teams_by_slot: dict[int, Team], limit: int = 23) -> list[dict]:
    players = data.get("players", [])
    if not isinstance(players, list):
        return []

    enriched: list[dict] = []
    for raw in players:
        if not isinstance(raw, dict):
            continue
        if str(raw.get("status", "OK")).upper() == "RETIRED":
            continue
        team_slot = int(raw.get("team_slot_id", -1))
        team = teams_by_slot.get(team_slot)
        team_comp = str(raw.get("team_comp", team.comp if team else ""))
        team_name = team.name if team else "Sin club"
        citizenship = str(raw.get("citizenship", "")).upper().strip()
        if not citizenship and team:
            src_name = str(raw.get("name", ""))
            match = next((p for p in team.players if p.name == src_name), None)
            if match:
                citizenship = str(match.citizenship).upper().strip()
        item = dict(raw)
        item["team_comp"] = team_comp
        item["team_name"] = team_name
        item["citizenship"] = citizenship
        item["score"] = _national_player_score(raw)
        enriched.append(item)

    spanish_liga1 = [
        p for p in enriched
        if str(p.get("citizenship", "")).upper() == "ES" and str(p.get("team_comp", "")) == "ES1"
    ]
    spanish_all = [p for p in enriched if str(p.get("citizenship", "")).upper() == "ES"]
    base_pool = spanish_liga1 if spanish_liga1 else spanish_all
    if len(base_pool) < limit:
        names = {str(p.get("name", "")) for p in base_pool}
        for p in spanish_all:
            if str(p.get("name", "")) not in names:
                base_pool.append(p)
                names.add(str(p.get("name", "")))
            if len(base_pool) >= limit:
                break
    if len(base_pool) < limit:
        names = {str(p.get("name", "")) for p in base_pool}
        liga1_pool = [p for p in enriched if str(p.get("team_comp", "")) == "ES1"]
        for p in sorted(liga1_pool, key=lambda x: float(x.get("score", 0.0)), reverse=True):
            if str(p.get("name", "")) in names:
                continue
            base_pool.append(p)
            names.add(str(p.get("name", "")))
            if len(base_pool) >= limit:
                break

    return sorted(base_pool, key=lambda x: float(x.get("score", 0.0)), reverse=True)[:limit]


def _ensure_national_state(data: dict):
    state = data.get("national")
    season = str(data.get("season", "2025-26"))
    if not isinstance(state, dict) or str(state.get("season", "")) != season:
        data["national"] = {"season": season, "windows": {}, "last": None}
        return
    state.setdefault("windows", {})
    state.setdefault("last", None)


def _simulate_national_window(
    data: dict,
    md: int,
    teams_by_slot: dict[int, Team],
    show_output: bool = False,
    force: bool = False,
) -> Optional[dict]:
    window = _national_window_index(md)
    if window == 0:
        return None

    _ensure_national_state(data)
    national = data.get("national", {})
    season = str(data.get("season", "2025-26"))
    key = f"{season}:W{window}"
    windows = national.setdefault("windows", {})
    if key in windows and not force:
        return windows.get(key)

    season_year = _season_year(season)
    tournament = "EUROCOPA" if season_year % 2 == 0 else "MUNDIAL"
    rivals = NATIONAL_EURO_RIVALS if tournament == "EUROCOPA" else NATIONAL_WORLD_RIVALS
    rng = random.Random(int(data.get("season_seed", 0)) ^ (window * 1103515245) ^ season_year)
    opponents = rng.sample(rivals, k=min(3, len(rivals)))
    while len(opponents) < 3:
        opponents.append(rivals[len(opponents) % len(rivals)])

    rival_strength = {
        "Brasil": 86.0,
        "Francia": 86.0,
        "Alemania": 86.0,
        "Argentina": 84.0,
        "Inglaterra": 84.0,
        "Italia": 82.0,
        "Portugal": 82.0,
        "Paises Bajos": 80.0,
        "Croacia": 80.0,
    }

    squad = _build_national_squad(data, teams_by_slot, limit=23)
    starters = squad[:11]
    spain_strength = sum(float(p.get("score", 75.0)) for p in starters) / float(max(1, len(starters)))
    spain_strength = max(55.0, min(95.0, spain_strength))

    eliminated = False
    champion = False
    match_lines: list[str] = []
    for idx, stage in enumerate(NATIONAL_STAGES):
        opponent = opponents[idx]
        opp = float(rival_strength.get(opponent, 78.0))
        goals_es = _poisson_goals_std((spain_strength / 100.0) * 2.15, rng)
        goals_opp = _poisson_goals_std((opp / 100.0) * 1.95, rng)
        penalties = ""
        spain_wins = goals_es > goals_opp
        if goals_es == goals_opp:
            pen_es = rng.randint(3, 5) + (1 if spain_strength >= opp else 0)
            pen_opp = rng.randint(3, 5) + (1 if opp > spain_strength else 0)
            if pen_es == pen_opp:
                if rng.random() < 0.5:
                    pen_es += 1
                else:
                    pen_opp += 1
            penalties = f" (pen {pen_es}-{pen_opp})"
            spain_wins = pen_es > pen_opp
        line = f"Espana {goals_es}-{goals_opp} {opponent}{penalties} [{stage}]"
        match_lines.append(line)
        if not spain_wins:
            eliminated = True
            break
        if idx == len(NATIONAL_STAGES) - 1:
            champion = True

    result = {
        "window": window,
        "matchday": md,
        "season": season,
        "tournament": tournament,
        "opponents": opponents,
        "matches": match_lines,
        "eliminated": eliminated,
        "champion": champion,
        "squad": [
            {
                "name": p.get("name", "Jugador"),
                "team": p.get("team_name", "Club"),
                "score": round(float(p.get("score", 0.0)), 1),
                "position": p.get("position", "?"),
            }
            for p in squad
        ],
    }

    windows[key] = result
    national["last"] = result
    data["national"] = national
    news = data.setdefault("news", [])
    if match_lines:
        news.append(f"J{md}: Seleccion - {match_lines[-1]}")
    if champion:
        news.append(f"J{md}: Espana campeona de {tournament}.")
    elif eliminated:
        news.append(f"J{md}: Espana cae en {match_lines[-1].split('[')[-1].rstrip(']')}.")

    if show_output:
        print(_c(BOLD + YELLOW, f"\n  SELECCION ESPANOLA · {tournament}"))
        for line in match_lines:
            print(f"  {line}")
        if champion:
            print(_c(GREEN, "  Espana campeona."))
        elif eliminated:
            print(_c(RED, "  Espana eliminada."))
        print()

    _save_career(data)
    return result


def _show_national_team(data: dict, md: int, teams_by_slot: dict[int, Team]):
    _ensure_national_state(data)
    season = str(data.get("season", "2025-26"))
    national = data.get("national", {})
    window = _national_window_index(md)
    key = f"{season}:W{window}" if window else ""
    windows = national.get("windows", {})
    current_result = windows.get(key) if key else None

    print(_c(BOLD + YELLOW, "\n  ═══ SELECCION ESPANOLA ═══"))
    print(_c(CYAN, f"  Temporada: {season}"))
    if window:
        print(_c(CYAN, f"  Ventana internacional abierta (J{md})"))
    else:
        print(_c(GRAY, "  Sin ventana internacional activa (J13-14 y J26-27)."))

    squad = _build_national_squad(data, teams_by_slot, limit=23)
    if squad:
        print(_c(GRAY, "  Top convocatoria:"))
        for idx, p in enumerate(squad[:11], start=1):
            print(
                f"   {idx:>2}. {str(p.get('name', 'Jugador'))[:22]:<22} "
                f"{str(p.get('team_name', 'Club'))[:18]:<18} "
                f"{float(p.get('score', 0.0)):>5.1f}"
            )
    else:
        print(_c(RED, "  No se pudo construir convocatoria."))

    if current_result:
        print()
        print(_c(YELLOW, f"  Torneo: {current_result.get('tournament', '-')}" ))
        for line in current_result.get("matches", []):
            print(f"  {line}")
        if current_result.get("champion"):
            print(_c(GREEN, "  Resultado: CAMPEON"))
        elif current_result.get("eliminated"):
            print(_c(RED, "  Resultado: ELIMINADO"))
    elif window:
        print()
        print(_c(CYAN, "  1. Simular ventana internacional"))
        print(_c(CYAN, "  0. Volver"))
        op = input_int("  Opcion: ", 0, 1)
        if op == 1:
            _simulate_national_window(data, md, teams_by_slot, show_output=True, force=True)
    print()


# ---- Main season loop ------------------------------------------------------

def _season_loop(data: dict, liga1: list[Team], liga2: list[Team], liga_rfef: list[Team] = [],
                  liga_foreign: "dict[str, list[Team]]" = {}):
    comp_key = data["competition"]
    is_l1 = comp_key == "ES1"
    is_l2 = comp_key == "ES2"
    ci     = COMP_INFO.get(comp_key, {})
    n_rel  = ci.get("n_rel",  3)
    tot_md = ci.get("tot_md", 38)
    if is_l1:
        comp_t = sorted(liga1, key=lambda t: t.slot_id)
    elif is_l2:
        comp_t = sorted(liga2, key=lambda t: t.slot_id)
    elif comp_key in ("E3G1", "E3G2"):
        comp_t = sorted([t for t in liga_rfef if t.comp == comp_key], key=lambda t: t.slot_id)
        if not comp_t:
            comp_t = sorted(liga_rfef, key=lambda t: t.slot_id)
    else:
        comp_t = sorted(liga_foreign.get(comp_key, []), key=lambda t: t.slot_id)
    if not comp_t:
        print(_c(RED, f"\n  No hay equipos cargados para la competicion {comp_key}."))
        return
    all_foreign = [t for teams in liga_foreign.values() for t in teams]
    fpm       = len(comp_t) // 2
    mgr_slot  = data["team_slot"]
    mgr_team  = {t.slot_id: t for t in comp_t}[mgr_slot]
    mgr_name  = data["manager"]["name"]
    _ensure_manager_depth(data)
    play_mode = _ensure_manager_play_mode(data)
    seed      = data["season_seed"]
    tbs       = {t.slot_id: t for t in comp_t}   # teams_by_slot
    all_slots = {t.slot_id: t for t in liga1 + liga2 + liga_rfef + all_foreign}

    # Apply saved squad changes (fichajes/ventas)
    _apply_squad_changes(mgr_team, all_slots, data)
    _ensure_president_profile(data, mgr_team)
    data.setdefault("players", _build_players_snapshot(liga1 + liga_rfef + all_foreign, liga2, data.get("season", "2025-26")))
    if not isinstance(data.get("copa"), dict) or data.get("copa", {}).get("season") != data.get("season"):
        data["copa"] = _init_copa_state(data, liga1, liga2)
        _save_career(data)
    if not isinstance(data.get("euro"), dict) or data.get("euro", {}).get("season") != data.get("season"):
        data["euro"] = _init_euro_state(data, all_slots, tot_md)
        _save_career(data)
    _ensure_national_state(data)

    # Build fixture map (deterministic, sorted by slot_id)
    all_fix    = generate_fixtures(comp_t)
    fix_by_md: dict[int, list[tuple[Team, Team]]] = {}
    fix_seed_by_key: dict[tuple[int, int, int], int] = {}
    for i, pair in enumerate(all_fix, start=1):
        md = ((i - 1) // fpm) + 1
        h, a = pair
        fix_by_md.setdefault(md, []).append(pair)
        fix_seed_by_key[(md, h.slot_id, a.slot_id)] = int(seed) ^ i

    results = data.setdefault("results", [])
    news    = data.setdefault("news", [])
    cur_md  = data.get("current_matchday", 1)

    while cur_md <= tot_md:
        play_mode = _ensure_manager_play_mode(data)
        standings = _standings_from_results(results, tbs)
        _pm_header(data, cur_md, tot_md, mgr_team)
        _mini_standings(standings, mgr_slot, n_rel)

        my_fix = next(((h, a) for h, a in fix_by_md.get(cur_md, [])
                       if h.slot_id == mgr_slot or a.slot_id == mgr_slot), None)
        if my_fix:
            h, a = my_fix
            print(_c(CYAN, f"  PRÃ“XIMO PARTIDO â€” Jornada {cur_md}:"))
            print(_c(BOLD + YELLOW, f"  {h.name}  vs  {a.name}"))
            print()
        copa_label = _active_copa_round_label(data.get("copa", {}), cur_md)
        if copa_label:
            print(_c(BOLD + CYAN, f"  COPA DEL REY: {copa_label} (ronda activa)"))
            print()
        euro_labels = _active_euro_round_labels(data.get("euro", {}), cur_md)
        if euro_labels:
            for lbl in euro_labels:
                print(_c(BOLD + CYAN, f"  UEFA: {lbl} (ronda activa)"))
            print()
        if _national_window_index(cur_md):
            print(_c(BOLD + YELLOW, f"  SELECCION ESPANOLA: ventana internacional abierta (J{cur_md})"))
            print()

        tactic  = data.setdefault("tactic", dict(DEFAULT_TACTIC))
        manager_depth = data.get("manager", {})
        training = manager_depth.get("training", DEFAULT_TRAINING_PLAN)
        staff = manager_depth.get("staff", DEFAULT_STAFF_PROFILE)
        win_tag = _c(GREEN, "[ABIERTO]") if _is_window_open(cur_md, tot_md) else _c(RED, "[CERRADO]")
        print(_c(GRAY, f"  TÃ¡ctica: {_tactic_summary(tactic)}"))
        print(_c(GRAY, f"  Entrenamiento: {_training_plan_summary(training)}"))
        print(_c(GRAY, f"  Staff clave: Fisio {staff.get('fisio', 50)} · Ojeador {staff.get('ojeador', 50)} · Juveniles {staff.get('juveniles', 50)}"))
        print()
        print(_c(CYAN, f"  1. Simular jornada {cur_md}"))
        print(_c(CYAN,  "  2. ClasificaciÃ³n completa"))
        print(_c(CYAN,  "  3. Plantilla"))
        print(_c(CYAN,  "  4. Noticias"))
        print(_c(CYAN,  "  5. Simular resto de temporada"))
        print(_c(CYAN,  f"  6. Mercado de fichajes {win_tag}"))
        print(_c(CYAN,  "  7. TÃ¡ctica"))
        print(_c(CYAN,  "  8. Entrenamiento y staff"))
        print(_c(CYAN,  "  9. Competiciones UEFA"))
        print(_c(CYAN,  " 10. Seleccion espanola"))
        print(_c(CYAN,  " 11. Despacho del presidente"))
        print(_c(CYAN,  f" 12. Nivel de control ({_play_mode_label(play_mode)})"))
        print(_c(CYAN,  "  0. Guardar y salir"))

        op = input_int("  OpciÃ³n: ", 0, 12)

        if op == 0:
            data["current_matchday"] = cur_md
            _save_career(data)
            print(_c(GREEN, "  Partida guardada.\n"))
            return

        elif op == 1:
            md_res = []
            for h, a in fix_by_md.get(cur_md, []):
                s            = fix_seed_by_key.get((cur_md, h.slot_id, a.slot_id), seed ^ (cur_md * 7919 + h.slot_id * 31 + a.slot_id))
                is_mgr_match = h.slot_id == mgr_slot or a.slot_id == mgr_slot
                ht           = tactic if h.slot_id == mgr_slot else None
                at           = tactic if a.slot_id == mgr_slot else None
                if is_mgr_match:
                    if _play_mode_allows_coach_match(play_mode):
                        print(_c(CYAN, "\n  Modo de partido:"))
                        print(_c(CYAN, "  1. Simular automÃ¡ticamente"))
                        print(_c(CYAN, "  2. Modo Entrenador  (fichas animadas)"))
                        pm = input_int("  OpciÃ³n (1-2): ", 1, 2)
                        if pm == 2:
                            hg, ag = _match_entrenador(h, a, s, mgr_slot, data)
                        else:
                            hg, ag = simulate_match(h, a, s, home_tactic=ht, away_tactic=at)
                    else:
                        print(_c(GRAY, "  Nivel Basico: partido del manager simulado automaticamente."))
                        hg, ag = simulate_match(h, a, s, home_tactic=ht, away_tactic=at)
                else:
                    hg, ag = simulate_match(h, a, s)
                r  = {"md": cur_md, "h": h.slot_id, "a": a.slot_id, "hg": hg, "ag": ag}
                md_res.append(r);  results.append(r)
            _show_md_results(md_res, tbs, mgr_slot)
            new_st = _standings_from_results(results, tbs)
            new_items = _append_dynamic_news(
                news, cur_md, md_res, tbs, mgr_slot, mgr_team, mgr_name, new_st, n_rel
            )
            my_r = next((r for r in md_res if r["h"] == mgr_slot or r["a"] == mgr_slot), None)
            mgr_pos = next((i + 1 for i, s in enumerate(new_st) if s.team.slot_id == mgr_slot), len(new_st))
            if _play_mode_allows_president(play_mode):
                _president_matchday_effects(
                    data=data,
                    md=cur_md,
                    mgr_team=mgr_team,
                    my_r=my_r,
                    mgr_pos=mgr_pos,
                    total=len(new_st),
                    n_rel=n_rel,
                    news=news,
                )
            if new_items:
                for ni in new_items:
                    print(_c(YELLOW, f"  ðŸ“° {ni}"))
                print()
            _play_copa_round(data, cur_md, all_slots, mgr_slot, show_output=True)
            _play_euro_round(data, cur_md, all_slots, mgr_slot, show_output=True)
            _simulate_national_window(data, cur_md, all_slots, show_output=True)
            cur_md += 1
            data["current_matchday"] = cur_md
            _save_career(data)
            _pause()

        elif op == 2:
            print_standings(standings, f"{_comp_name(comp_key)} {data['season']}", relegated_from=n_rel)

        elif op == 3:
            print_squad(mgr_team)

        elif op == 4:
            if not news:
                print(_c(GRAY, "  Sin noticias aÃºn.\n"))
            else:
                print(_c(YELLOW, "\n  â”€â”€â”€ NOTICIAS â”€â”€â”€"))
                for ni in news[-5:]:
                    print(f"  â€¢ {ni}")
                print()

        elif op == 6:
            _market_menu(data, mgr_team, all_slots, liga1, liga2)

        elif op == 7:
            _tactic_menu(data)
            tactic = data.get("tactic", tactic)

        elif op == 8:
            if _play_mode_allows_manager_depth(play_mode):
                _manager_depth_menu(data)
            else:
                print(_c(GRAY, "  Disponible en nivel Estandar o Total.\n"))

        elif op == 9:
            _show_euro_status(data, all_slots, mgr_slot)
            _pause()

        elif op == 10:
            _show_national_team(data, cur_md, all_slots)
            _pause()

        elif op == 11:
            if _play_mode_allows_president(play_mode):
                _president_menu(data, mgr_team)
            else:
                print(_c(GRAY, "  Despacho del presidente solo disponible en nivel Total.\n"))

        elif op == 12:
            play_mode = _play_mode_menu(data)

        elif op == 5:
            print(_c(YELLOW, f"\n  Simulando jornadas {cur_md}â€“{tot_md}..."))
            winter_md = 21 if tot_md >= 42 else max(1, tot_md // 2)
            for md in range(cur_md, tot_md + 1):
                md_res = []
                for h, a in fix_by_md.get(md, []):
                    s  = fix_seed_by_key.get((md, h.slot_id, a.slot_id), seed ^ (md * 7919 + h.slot_id * 31 + a.slot_id))
                    ht = tactic if h.slot_id == mgr_slot else None
                    at = tactic if a.slot_id == mgr_slot else None
                    hg, ag = simulate_match(h, a, s, home_tactic=ht, away_tactic=at)
                    r = {"md": md, "h": h.slot_id, "a": a.slot_id, "hg": hg, "ag": ag}
                    md_res.append(r)
                    results.append(r)
                _append_dynamic_news(
                    news,
                    md,
                    md_res,
                    tbs,
                    mgr_slot,
                    mgr_team,
                    mgr_name,
                    _standings_from_results(results, tbs),
                    n_rel,
                )
                new_st = _standings_from_results(results, tbs)
                my_r = next((r for r in md_res if r["h"] == mgr_slot or r["a"] == mgr_slot), None)
                mgr_pos = next((i + 1 for i, s in enumerate(new_st) if s.team.slot_id == mgr_slot), len(new_st))
                if _play_mode_allows_president(play_mode):
                    _president_matchday_effects(
                        data=data,
                        md=md,
                        mgr_team=mgr_team,
                        my_r=my_r,
                        mgr_pos=mgr_pos,
                        total=len(new_st),
                        n_rel=n_rel,
                        news=news,
                    )
                if md == winter_md and not data.get("winter_market_done", False):
                    data["winter_market_done"] = True
                    _save_career(data)
                    _winter_market_menu(data, mgr_team, comp_t, md)
                _play_copa_round(data, md, all_slots, mgr_slot, show_output=False)
                _play_euro_round(data, md, all_slots, mgr_slot, show_output=False)
                _simulate_national_window(data, md, all_slots, show_output=False)
            cur_md = tot_md + 1
            data["current_matchday"] = cur_md
            _save_career(data)
            print(_c(GREEN, "  âœ“ Temporada completada.\n"))
            _pause()

    continue_career = _season_end_screen(
        data,
        _standings_from_results(results, tbs),
        mgr_slot,
        mgr_team,
        is_l1,
        n_rel,
    )
    if not continue_career:
        return

    team = _show_offers(data, liga1, liga2, liga_rfef, liga_foreign)
    next_season = _next_season_str(data.get("season", "2025-26"))
    _setup_season(data, team, liga1, liga2, next_season, liga_rfef, liga_foreign)
    print(_c(GREEN, f"\n  Â¡{data['manager']['name']} ficha por {team.name}!"))
    print(_c(YELLOW, f"  Objetivo: {data['objective']}"))
    _pause()
    _season_loop(data, liga1, liga2, liga_rfef, liga_foreign)


# ---- Helpers ---------------------------------------------------------------

def _next_season_str(season: str) -> str:
    try:
        ns = int(season.split("-")[0]) + 1
        return f"{ns}-{(ns % 100) + 1:02d}"
    except Exception:
        return "2026-27"


def _setup_season(data: dict, team: Team, liga1: list[Team], liga2: list[Team], season: str,
                   liga_rfef: list[Team] = [], liga_foreign: "dict[str, list[Team]]" = {}):
    _ensure_manager_depth(data)
    m = data["manager"]
    data.update({
        "phase":            "SEASON",
        "season":           season,
        "team_slot":        team.slot_id,
        "team_name":        team.name,
        "competition":      team.comp,
        "objective":        _assign_objective(team, liga1, liga2, liga_rfef, liga_foreign),
        "current_matchday": 1,
        "season_seed":      random.randint(0, 2**32 - 1),
        "budget":           _init_budget(team, liga1, liga2),
        "bought":           [],
        "sold":             [],
        "winter_market_done": False,
        "results":          [],
        "news":             [f"Inicio de temporada {season}. {m['name']} llega a {team.name}."],
        "manager_team":     {"slot_id": team.slot_id, "name": team.name},
        "copa":             None,
        "euro":             None,
        "national":         None,
        "president":        {
            "ownership": "SOCIOS",
            "salary_cap_mode": "BALANCED",
            "salary_cap_k": 0,
            "pressure": 0,
            "investor_rounds": 0,
            "ipo_done": False,
            "pelotazo_done": False,
            "last_review_md": 0,
            "next_review_md": 2,
            "last_cap_penalty_md": -99,
        },
    })
    data.setdefault("players", _build_players_snapshot(liga1, liga2, season))
    _save_career(data)


def _comp_name(comp: str) -> str:
    info = COMP_INFO.get(comp)
    if info:
        country = info["country"]
        name = info["name"]
        return name if country == "EspaÃ±a" else f"{name} ({country})"
    return comp


def _show_offers(data: dict, liga1: list[Team], liga2: list[Team], liga_rfef: list[Team] = [], liga_foreign: dict[str, list[Team]] = {}) -> Optional[Team]:
    m      = data["manager"]
    offers = _generate_offers(m["prestige"], liga1, liga2, liga_rfef, liga_foreign, data=data)
    print(_c(BOLD + YELLOW, "\n  â•â•â• SELECCIÃ“N DE OFERTA â•â•â•"))
    print(_c(GRAY,  f"  Prestigio: {_prestige_label(m['prestige'])}  |  Temporadas: {m['total_seasons']}"))
    print()
    for i, t in enumerate(offers, 1):
        cn  = _comp_name(t.comp)
        obj = _assign_objective(t, liga1, liga2, liga_rfef, liga_foreign)
        print(_c(CYAN, f"  {i}. {t.name}"))
        print(f"       {cn}  Â·  Fuerza: {t.strength():.1f}")
        print(f"       Objetivo: {obj}")
        print()
    idx = input_int(f"  Elige oferta (1-{len(offers)}): ", 1, len(offers))
    return offers[idx - 1]


# ---- Entry point -----------------------------------------------------------

def menu_promanager(liga1: list[Team], liga2: list[Team], liga_rfef: list[Team] = [], liga_foreign: dict[str, list[Team]] = {}):
    print(_c(BOLD + CYAN, "\n  â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—"))
    print(_c(BOLD + CYAN, "  â•‘       MODO PRO MANAGER           â•‘"))
    print(_c(BOLD + CYAN, "  â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"))

    data = _load_career()

    if data:
        _ensure_manager_depth(data)
        _ensure_manager_play_mode(data)
        m = data["manager"]
        print(_c(YELLOW, f"\n  Partida guardada â€” {m['name']}  {_prestige_label(m['prestige'])}"))
        print(f"  {data.get('season','?')}  Â·  {data.get('team_name','?')}  Â·  J{data.get('current_matchday',1)}")
        print(_c(GRAY, f"  Nivel de control: {_play_mode_label(m.get('play_mode', 'STANDARD'))}"))
        _print_career_summary(data)
        print()
        if data.get("phase") == "POSTSEASON":
            print(_c(CYAN, "  1. Nueva temporada (nuevas ofertas)"))
            print(_c(CYAN, "  2. Borrar y crear nuevo manager"))
            print(_c(CYAN, "  0. Volver"))
            op = input_int("  OpciÃ³n: ", 0, 2)
            if op == 0:
                return
            elif op == 1:
                team = _show_offers(data, liga1, liga2, liga_rfef, liga_foreign)
                _setup_season(data, team, liga1, liga2, _next_season_str(data["season"]), liga_rfef, liga_foreign)
                print(_c(GREEN,  f"\n  Â¡{m['name']} ficha por {team.name}!"))
                print(_c(YELLOW, f"  Objetivo: {data['objective']}"))
                _pause()
                _season_loop(data, liga1, liga2, liga_rfef, liga_foreign)
                return
            else:
                data = None
        else:
            print(_c(CYAN, "  1. Continuar partida"))
            print(_c(CYAN, "  2. Borrar y crear nuevo manager"))
            print(_c(CYAN, "  0. Volver"))
            op = input_int("  OpciÃ³n: ", 0, 2)
            if op == 0:
                return
            elif op == 1:
                _season_loop(data, liga1, liga2, liga_rfef, liga_foreign)
                return
            else:
                data = None

    # Crear nuevo manager
    print(_c(BOLD + YELLOW, "\n  â”€â”€â”€ NUEVO MANAGER â”€â”€â”€"))
    name = input_str("  Nombre del manager: ")
    if not name:
        return
    data = {"manager": {"name": name, "prestige": 1, "total_seasons": 0, "history": []}}
    _ensure_manager_depth(data)
    _play_mode_menu(data, persist=False)

    team = _show_offers(data, liga1, liga2, liga_rfef, liga_foreign)
    _setup_season(data, team, liga1, liga2, "2025-26", liga_rfef, liga_foreign)
    m = data["manager"]
    print(_c(GREEN,  f"\n  Â¡Bienvenido a {team.name}, {m['name']}!"))
    print(_c(YELLOW, f"  Objetivo de la junta: {data['objective']}"))
    _pause()
    _season_loop(data, liga1, liga2, liga_rfef, liga_foreign)


def menu_multiplayer():
    print(_c(BOLD + CYAN, "\n  â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—"))
    print(_c(BOLD + CYAN, "  â•‘      MULTIJUGADOR POR TURNOS     â•‘"))
    print(_c(BOLD + CYAN, "  â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"))
    print(_c(YELLOW, "\n  Esta variante del CLI no incluye multijugador por turnos."))
    print(_c(GRAY,   "  Mantendremos un unico CLI canonico en cli/pcfutbol_cli.py.\n"))
    _pause()


# ===========================================================================
# MENÃš PRINCIPAL
# ===========================================================================

def main_menu():
    print(_c(BOLD + CYAN,  "\n  â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—"))
    print(_c(BOLD + CYAN,  "  â•‘    PC FÃšTBOL 5  Â·  CLI  2025/26  â•‘"))
    print(_c(BOLD + CYAN,  "  â•‘      Temporada real â€” Python      â•‘"))
    print(_c(BOLD + CYAN,  "  â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•"))

    print(_c(YELLOW, "\n  Cargando datos de temporada 2025/26..."))
    all_teams = load_teams()

    liga1 = sorted([t for t in all_teams.values() if t.comp == "ES1"], key=lambda t: t.name)
    liga2 = sorted([t for t in all_teams.values() if t.comp == "ES2"], key=lambda t: t.name)
    liga_rfef = sorted([t for t in all_teams.values() if t.comp in ("E3G1", "E3G2")], key=lambda t: t.name)
    _foreign_comps = [c for c in COMP_INFO if c not in ("ES1", "ES2", "E3G1", "E3G2")]
    liga_foreign: dict[str, list[Team]] = {
        comp: sorted([t for t in all_teams.values() if t.comp == comp], key=lambda t: t.name)
        for comp in _foreign_comps
    }
    liga_foreign = {k: v for k, v in liga_foreign.items() if v}

    print(_c(GREEN, f"  âœ“ {len(liga1)} equipos en Primera DivisiÃ³n"))
    print(_c(GREEN, f"  âœ“ {len(liga2)} equipos en Segunda DivisiÃ³n"))
    print(_c(GREEN, f"  âœ“ {len(liga_rfef)} equipos en 1Âª RFEF"))
    _n_foreign = sum(len(v) for v in liga_foreign.values())
    if _n_foreign:
        print(_c(GREEN, f"  âœ“ {_n_foreign} equipos en {len(liga_foreign)} ligas extranjeras"))
    total_players = sum(len(t.players) for t in liga1 + liga2 + liga_rfef) + sum(len(t.players) for ts in liga_foreign.values() for t in ts)
    print(_c(GREEN, f"  âœ“ {total_players} jugadores cargados\n"))

    while True:
        print(_c(CYAN,   "  â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”"))
        print(_c(CYAN,   "  â”‚         MENÃš PRINCIPAL           â”‚"))
        print(_c(CYAN,   "  â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤"))
        print(_c(CYAN,   "  â”‚  1. Simular temporada completa   â”‚"))
        print(_c(CYAN,   "  â”‚  2. Ver plantilla de equipo      â”‚"))
        print(_c(CYAN,   "  â”‚  3. Partido rÃ¡pido               â”‚"))
        print(_c(CYAN,   "  â”‚  4. Top jugadores                â”‚"))
        print(_c(CYAN,   "  â”‚  5. Ranking de fortaleza         â”‚"))
        print(_c(BOLD + CYAN, "  â”‚  6. PRO MANAGER (modo carrera)  â”‚"))
        print(_c(BOLD + CYAN, "  â”‚  7. MULTIJUGADOR por turnos     â”‚"))
        print(_c(BOLD + CYAN, "  â”‚  8. Actualidad futbolistica      â”‚"))
        print(_c(CYAN,   "  â”‚  0. Salir                        â”‚"))
        print(_c(CYAN,   "  â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜"))

        op = input_int("  OpciÃ³n: ", 0, 8)
        if op == 0:
            print(_c(GRAY, "\n  Â¡Hasta la prÃ³xima temporada!\n"))
            break
        elif op == 1:
            menu_simulate_season(liga1, liga2)
        elif op == 2:
            menu_view_squad(liga1, liga2)
        elif op == 3:
            menu_quick_match(liga1, liga2)
        elif op == 4:
            menu_top_players(liga1, liga2)
        elif op == 5:
            menu_team_strength(liga1, liga2)
        elif op == 6:
            menu_promanager(liga1, liga2, liga_rfef, liga_foreign)
        elif op == 7:
            menu_multiplayer()
        elif op == 8:
            menu_real_football()


if __name__ == "__main__":
    # Desactivar colores si no hay TTY
    if not sys.stdout.isatty():
        for name in ("CYAN", "YELLOW", "GREEN", "RED", "GRAY", "BOLD", "RESET"):
            globals()[name] = ""
    main_menu()

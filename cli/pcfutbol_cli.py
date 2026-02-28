#!/usr/bin/env python3
"""
PC Fútbol 5 — Clon CLI (Python)
Temporada 2025/26  ·  Datos reales extraídos del juego original

Uso:
    python pcfutbol_cli.py

Requiere:
    Python 3.9+  (stdlib sólo, sin dependencias externas)
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
ASSETS_DIR = Path(__file__).resolve().parent.parent / "android" / "core" / "data" / "src" / "main" / "assets"
PLAYERS_CSV  = ASSETS_DIR / "pcf55_players_2526.csv"
TEAMS_JSON   = ASSETS_DIR / "pcf55_teams_extracted.json"

# ---------------------------------------------------------------------------
# Data models
# ---------------------------------------------------------------------------
@dataclass
class Player:
    slot_id:   int
    team_name: str
    comp:      str
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
        """Fuerza global del equipo (0-100)."""
        if not self.players:
            return 50.0
        starters = sorted(self.players, key=lambda p: p.overall, reverse=True)[:11]
        gks   = [p for p in starters if p.is_goalkeeper]
        field_players = [p for p in starters if not p.is_goalkeeper]

        gk_str  = sum(p.gk_strength() for p in gks) / max(len(gks), 1)
        att_str = sum(p.attack_strength() for p in field_players) / max(len(field_players), 1)
        def_str = sum(p.defense_strength() for p in field_players) / max(len(field_players), 1)

        return gk_str * 0.25 + att_str * 0.40 + def_str * 0.35


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
    """Carga equipos de LIGA1, LIGA2 y 1ª RFEF del CSV de jugadores."""
    teams: dict[str, Team] = {}  # key = slot_id str
    players_by_team: dict[str, list[Player]] = {}

    if not PLAYERS_CSV.exists():
        print(f"[ERROR] No se encuentra el CSV: {PLAYERS_CSV}")
        sys.exit(1)

    with open(PLAYERS_CSV, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            comp = row["competition"]
            if comp not in ("ES1", "ES2", "E3G1", "E3G2"):
                continue
            slot = int(row["teamSlotId"])
            key  = str(slot)

            if key not in teams:
                teams[key] = Team(slot_id=slot, name=row["teamName"], comp=comp)
                players_by_team[key] = []

            try:
                p = Player(
                    slot_id   = slot,
                    team_name = row["teamName"],
                    comp      = comp,
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

def _poisson_goals(lam: float, rng: random.Random) -> int:
    """Genera goles con distribución Poisson usando el RNG dado."""
    L   = math.exp(-lam)
    k   = 0
    p   = 1.0
    while True:
        k += 1
        p *= rng.random()
        if p <= L:
            return k - 1


def _tactic_adj(tactic: Optional[dict]) -> float:
    """
    Ajuste de fuerza por táctica — misma fórmula que Android StrengthCalculator.
    tipoJuego:   1=DEFENSIVO(-1.0) 2=EQUILIBRADO(0) 3=OFENSIVO(+1.5)
    tipoPresion: 1=BAJA(-0.5)      2=MEDIA(0)       3=ALTA(+1.0)
    extras menores: marcaje, faltas, contragolpe.
    """
    if not tactic:
        return 0.0
    adj = 0.0
    tj = tactic.get("tipoJuego", 2)
    if tj == 3:    adj += 1.5
    elif tj == 1:  adj -= 1.0
    tp = tactic.get("tipoPresion", 2)
    if tp == 3:    adj += 1.0
    elif tp == 1:  adj -= 0.5
    if tactic.get("tipoMarcaje", 1) == 1:        adj += 0.3   # al hombre
    if tactic.get("faltas", 2) == 3:             adj += 0.2   # duro
    if tactic.get("porcContra", 30) > 60:        adj += 0.3
    if tactic.get("tipoDespejes", 1) == 2:       adj += 0.2   # controlado
    if tactic.get("perdidaTiempo", 0) == 1:      adj -= 0.4   # juego más lento
    return adj


def simulate_match(home: Team, away: Team, seed: int,
                   home_tactic: Optional[dict] = None,
                   away_tactic: Optional[dict] = None) -> tuple[int, int]:
    """
    Simulador determinístico con soporte táctico.
    Devuelve (home_goals, away_goals).
    Los ajustes tácticos siguen la misma fórmula que Android StrengthCalculator.
    """
    rng = random.Random(seed)

    # Fuerza base + ajuste táctico (igual que Android)
    home_str = (home.strength() + _tactic_adj(home_tactic)) * 1.05  # ventaja local +5%
    away_str =  away.strength() + _tactic_adj(away_tactic)

    total = max(home_str + away_str, 1.0)

    lambda_home = max(0.3, min(2.8 * (home_str / total), 4.5))
    lambda_away = max(0.3, min(2.8 * (away_str / total), 4.5))

    return _poisson_goals(lambda_home, rng), _poisson_goals(lambda_away, rng)


# ---------------------------------------------------------------------------
# Fixture generator  (round-robin doble vuelta)
# ---------------------------------------------------------------------------

def generate_fixtures(teams: list[Team]) -> list[tuple[Team, Team]]:
    """Genera todos los fixtures de una temporada completa (doble vuelta)."""
    n = len(teams)
    if n % 2 != 0:
        teams = teams + [Team(slot_id=-1, name="BYE", comp="")]

    n = len(teams)
    fixtures = []
    rounds_count = n - 1

    lst = teams[1:]
    pivot = teams[0]

    for r in range(rounds_count):
        round_home = []
        round_away = []
        round_home.append((pivot, lst[r % len(lst)]))
        for i in range(1, n // 2):
            h = lst[(r + i) % len(lst)]
            a = lst[(r - i) % len(lst)]
            if r % 2 == 0:
                round_home.append((h, a))
            else:
                round_home.append((a, h))

        for h, a in round_home:
            if h.slot_id != -1 and a.slot_id != -1:
                fixtures.append((h, a))

    # Segunda vuelta (intercambio local/visitante)
    second = [(a, h) for h, a in fixtures]
    return fixtures + second


# ---------------------------------------------------------------------------
# Season simulation
# ---------------------------------------------------------------------------

def simulate_season(
    teams: list[Team],
    competition: str,
    seed_base: int = 12345,
    silent: bool = False,
) -> list[Standing]:
    """Simula una temporada completa y devuelve la clasificación final."""
    fixtures = generate_fixtures(teams)
    standings = {t.slot_id: Standing(team=t) for t in teams}
    results: list[MatchResult] = []

    matchdays_total = (len(teams) - 1) * 2
    fixtures_per_md = len(teams) // 2
    matchday = 1

    for i, (home, away) in enumerate(fixtures):
        if i > 0 and i % fixtures_per_md == 0:
            matchday += 1

        seed = seed_base ^ (matchday * 1000 + home.slot_id * 31 + away.slot_id)
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
    print(_c(BOLD + YELLOW, f"  ═══ {title} ═══"))
    print(_c(GRAY, f"  {'#':>3}  {'EQUIPO':<24} {'PJ':>3} {'G':>3} {'E':>3} {'P':>3} {'GF':>3} {'GC':>3} {'DG':>4} {'PTS':>4}"))
    print(_c(GRAY, "  " + "─" * 62))

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

    print(_c(YELLOW, f"\n  ─── Jornada {matchday} ───"))
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
    print(_c(BOLD + YELLOW, f"\n  ═══ PLANTILLA: {team.name} ═══"))
    print(_c(GRAY, f"  {'#':<3} {'JUGADOR':<26} {'POS':<18} {'ED':>3} {'ME':>3} {'VE':>3} {'RE':>3} {'CA':>3} {'VALOR':<12}"))
    print(_c(GRAY, "  " + "─" * 78))

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
            print(f"  Introduce un número entre {min_val} y {max_val}.")
        except ValueError:
            print("  Número inválido.")
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
    print(_c(BOLD + CYAN, "\n  ══════════════════════════════"))
    print(_c(BOLD + CYAN,   "     SIMULAR TEMPORADA 2025/26"))
    print(_c(BOLD + CYAN,   "  ══════════════════════════════"))

    seed = int.from_bytes(os.urandom(4), "little")

    print(_c(YELLOW, "\n  Simulando LIGA1 (Primera División)..."))
    liga1_standings, liga1_results = simulate_season(liga1, "LIGA1", seed)

    print(_c(YELLOW, "  Simulando LIGA2 (Segunda División)..."))
    liga2_standings, liga2_results = simulate_season(liga2, "LIGA2", seed ^ 0xDEAD)

    # Ascensos/descensos
    relegated   = [s.team for s in liga1_standings[-3:]]
    promoted    = [s.team for s in liga2_standings[:3]]

    print_standings(liga1_standings, "PRIMERA DIVISIÓN 2025/26", relegated_from=3)
    print_standings(liga2_standings, "SEGUNDA DIVISIÓN 2025/26", promoted_to=3)

    champion = liga1_standings[0]
    print(_c(BOLD + GREEN, f"  🏆 CAMPEÓN: {champion.team.name} con {champion.points} puntos"))

    print(_c(GREEN, "\n  ⬆ Ascienden a Primera:"))
    for t in promoted:
        print(f"    ✓ {t.name}")

    print(_c(RED, "\n  ⬇ Descienden a Segunda:"))
    for t in relegated:
        print(f"    ✗ {t.name}")

    # Menú de resultados
    while True:
        print(_c(CYAN, "\n  ┌─ Ver resultados ────────────────┐"))
        print(_c(CYAN, "  │ 1. Jornada específica LIGA1      │"))
        print(_c(CYAN, "  │ 2. Jornada específica LIGA2      │"))
        print(_c(CYAN, "  │ 3. Resultados de un equipo       │"))
        print(_c(CYAN, "  │ 4. Plantilla de un equipo        │"))
        print(_c(CYAN, "  │ 0. Volver                        │"))
        print(_c(CYAN, "  └──────────────────────────────────┘"))

        op = input_int("  Opción: ", 0, 4)
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
    print(_c(BOLD + YELLOW, "  ══════════════════════════════"))
    print(_c(BOLD + YELLOW, "         RESULTADO FINAL"))
    print(_c(BOLD + YELLOW, "  ══════════════════════════════"))
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
    print("    1. Valoración media (ME)")
    print("    2. Delanteros (remate)")
    print("    3. Porteros")
    print("    4. Defensas")
    print("    5. Centrocampistas (pase)")

    op = input_int("  Opción (1-5): ", 1, 5)
    n  = input_int("  ¿Cuántos jugadores? (5-50): ", 5, 50)

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
    print(_c(GRAY, "  " + "─" * 74))
    for i, p in enumerate(top, 1):
        print(f"  {i:<3} {p.name[:25]:<26} {p.team_name[:21]:<22} {p.position[:17]:<18} {p.me:>3}")
    print()


def menu_team_strength(liga1: list[Team], liga2: list[Team]):
    print(_c(BOLD + YELLOW, "\n  ═══ RANKING DE FORTALEZA ═══"))
    all_teams = sorted(liga1 + liga2, key=lambda t: t.strength(), reverse=True)

    print(_c(GRAY, f"\n  {'#':>3}  {'EQUIPO':<26} {'LIGA':<8} {'FUERZA':>7}"))
    print(_c(GRAY, "  " + "─" * 48))
    for i, t in enumerate(all_teams, 1):
        comp = "1ª DIV" if t.comp == "ES1" else "2ª DIV"
        bar_len = int(t.strength() / 2)
        bar = "█" * bar_len
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
# PRO MANAGER — MODO CARRERA
# ===========================================================================

CAREER_SAVE = Path.home() / ".pcfutbol_career.json"


def _prestige_label(p: int) -> str:
    return "★" * min(p, 5) + "☆" * (5 - min(p, 5))


def _pause():
    if sys.stdin.isatty():
        input(_c(GRAY, "  [ENTER para continuar]"))


def _print_career_summary(data: dict):
    manager = data.get("manager", {})
    history = manager.get("history", [])
    if not isinstance(history, list) or not history:
        return

    print()
    print(_c(BOLD + YELLOW, "  ═══ RESUMEN DE CARRERA ═══"))
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
            outcome = "✓ ascenso"
        else:
            outcome = "✓ objetivo" if met else "✗ objetivo"
        comp_short = "1ª" if "Primera" in comp else ("2ª" if "Segunda" in comp else comp[:2])
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
            return json.load(f)
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


def _print_development_summary(summary: dict):
    improved = summary.get("improved", [])
    declined = summary.get("declined", [])
    retired = summary.get("retired", [])
    youth_added = int(summary.get("youth_added", 0))

    print()
    print(_c(BOLD + YELLOW, "  ═══ EVOLUCION DE TU PLANTILLA ═══"))
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
            improve_count = rng.randint(1, 3)
            gain = 0
            for attr in rng.sample(attrs, k=improve_count):
                inc = rng.randint(1, 3)
                p[attr] = min(99, int(p.get(attr, 50)) + inc)
                gain += inc
            p["me"] = min(99, int(p.get("me", 50)) + max(1, round(gain / max(1, improve_count))))
        elif age >= 31:
            delta = 2 if age >= 34 else 1
            p["ve"] = max(1, int(p.get("ve", 50)) - delta)
            p["re"] = max(1, int(p.get("re", 50)) - delta)
            p["me"] = max(1, int(p.get("me", 50)) - (1 if age >= 34 else 0))
        else:
            for attr in rng.sample(attrs, k=rng.randint(0, 2)):
                p[attr] = max(0, min(99, int(p.get(attr, 50)) + rng.randint(-1, 1)))
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
        for _ in range(2):
            players.append({
                "name": f"Cantera {rng.randint(100, 999)}",
                "position": rng.choice(["Defender", "Midfielder", "Forward"]),
                "birth_year": year - 17,
                "me": rng.randint(42, 55),
                "ve": rng.randint(35, 50),
                "re": rng.randint(35, 50),
                "ag": rng.randint(30, 55),
                "ca": rng.randint(35, 55),
                "remate": rng.randint(20, 45),
                "regate": rng.randint(20, 45),
                "pase": rng.randint(25, 50),
                "tiro": rng.randint(20, 45),
                "entrada": rng.randint(25, 50),
                "portero": 0,
                "team_slot_id": manager_slot,
                "status": "OK",
                "market_value": rng.randint(100_000, 600_000),
            })
            youth_added += 1

    data["players"] = [p for p in players if isinstance(p, dict) and p.get("status") != "RETIRED"]
    data["retired_count"] = int(data.get("retired_count", 0)) + retired_now

    return {
        "improved": improved,
        "declined": declined,
        "retired": retired,
        "youth_added": youth_added,
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

def _generate_offers(prestige: int, liga1: list[Team], liga2: list[Team], liga_rfef: list[Team] = [], data: Optional[dict] = None) -> list[Team]:
    l_rfef = sorted(liga_rfef, key=lambda t: t.strength())
    l2 = sorted(liga2, key=lambda t: t.strength())
    l1 = sorted(liga1, key=lambda t: t.strength())
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

    if was_promoted and prestige >= 3:
        pool = l1[:16]
    elif was_promoted_rfef or (stayed_segunda is False and prestige == 2):
        pool = l2[:12]
    elif stayed_segunda:
        idx = next((i for i, t in enumerate(l2) if t.slot_id == team_slot), len(l2) // 2)
        similar = l2[max(0, idx - 4): min(len(l2), idx + 5)]
        better = l2[max(0, idx + 1): min(len(l2), idx + 5)]
        pool = similar + better + l1[:2]
    elif prestige == 1:
        pool = l_rfef if l_rfef else l2[:8]
    elif prestige <= 2:
        pool = l2[:12]
    elif prestige <= 4:
        pool = list(l2)
    elif prestige <= 6:
        pool = l2[-8:] + l1[:8]
    elif prestige <= 8:
        pool = l1[:15]
    else:
        pool = list(l1)

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

def _assign_objective(team: Team, liga1: list[Team], liga2: list[Team]) -> str:
    rank = sorted((liga2 if team.comp == "ES2" else liga1), key=lambda t: t.strength(), reverse=True)
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

def _pm_header(data: dict, matchday: int, total_md: int):
    m = data["manager"]
    print()
    print(_c(BOLD + CYAN, f"  ═══ PROMANAGER · TEMPORADA {data['season']} ═══"))
    print(_c(YELLOW, f"  Manager : {m['name']:<20}  Prestigio: {_prestige_label(m['prestige'])}"))
    print(_c(YELLOW, f"  Equipo  : {data['team_name']:<20}  Jornada  : {matchday}/{total_md}"))
    print(_c(CYAN,   f"  Objetivo: {data['objective']}"))
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
            print(_c(RED, "       ─── zona de descenso ──"))
        s   = standings[pos]
        mgr = s.team.slot_id == mgr_slot
        if pos == 0:             color = GREEN
        elif pos >= total-n_rel: color = RED
        elif mgr:                color = YELLOW
        else:                    color = RESET
        print(color + f"  {pos+1:>3}  {s.team.name[:23]:<24} {s.played:>3} {s.points:>4}" +
              (" ◄" if mgr else "") + RESET)
        prev = pos
    print()


def _show_md_results(md_res: list[dict], tbs: dict[int, Team], mgr_slot: int):
    print(_c(YELLOW, "  ─── RESULTADOS ─────────────────────"))
    for r in md_res:
        ht = tbs.get(r["h"]); at = tbs.get(r["a"])
        if not ht or not at:
            continue
        hg, ag  = r["hg"], r["ag"]
        is_mgr  = r["h"] == mgr_slot or r["a"] == mgr_slot
        row     = f"  {'▶' if is_mgr else ' '} {ht.name[:21]:<22} {hg:>2} - {ag:<2} {at.name}"
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
        extra = " Sigue en zona de descenso." if drop else f" El equipo es {pos}º."
        return f"Empate {venue} ante {oname} ({mg}-{mg}).{extra}"
    else:
        extra = " La junta convoca urgencia." if drop else f" Posición {pos}."
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
            candidates.append(f"La junta esta satisfecha con tu rendimiento (pos {mgr_pos}ª).")
        elif mgr_pos > total - n_rel:
            candidates.append(f"La junta exige reaccion inmediata (pos {mgr_pos}ª).")
        else:
            candidates.append(f"La junta mantiene la calma con tu rendimiento (pos {mgr_pos}ª).")

    rivals = [t for sid, t in tbs.items() if sid != mgr_slot]
    if rivals:
        rival = rng.choice(rivals)
        fichaje = _pick_player_name(rival, rng, prefer_attack=True)
        fee = rng.randint(1, 8)
        candidates.append(f"Rival {rival.name} acaba de fichar a {fichaje} por {fee}M€.")

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


def _season_end_screen(data: dict, standings: list[Standing], mgr_slot: int,
                        mgr_team: Team, is_l1: bool, n_rel: int) -> bool:
    comp_name = "Primera División" if is_l1 else "Segunda División"
    total     = len(standings)
    mgr_pos   = next((i+1 for i, s in enumerate(standings) if s.team.slot_id == mgr_slot), 0)
    met       = _check_objective(data["objective"], mgr_pos, total, data["competition"])
    mgr_st    = next((s for s in standings if s.team.slot_id == mgr_slot), None)

    print()
    print(_c(BOLD + YELLOW, "  ╔═══════════════════════════════════╗"))
    print(_c(BOLD + YELLOW, "  ║        FIN DE TEMPORADA           ║"))
    print(_c(BOLD + YELLOW, "  ╚═══════════════════════════════════╝"))
    print()
    print(_c(GREEN, f"  🏆 CAMPEÓN: {standings[0].team.name} ({standings[0].points} pts)"))
    print()
    print(_c(YELLOW, f"  TU RESULTADO — {mgr_team.name}"))
    if mgr_st:
        print(f"  Posición: {mgr_pos}º / {total}")
        print(f"  PJ:{mgr_st.played}  G-E-P: {mgr_st.won}-{mgr_st.drawn}-{mgr_st.lost}  "
              f"GF:{mgr_st.gf}  GC:{mgr_st.ga}  DG:{mgr_st.gd:+d}  PTS:{mgr_st.points}")
    print()
    print(f"  Objetivo: {data['objective']}")
    if met:
        print(_c(GREEN, "  ✓  OBJETIVO CUMPLIDO"))
    else:
        print(_c(RED,   "  ✗  OBJETIVO NO CUMPLIDO"))

    rel_teams = [standings[total - n_rel + i].team for i in range(n_rel)]
    if mgr_team in rel_teams:
        print(_c(RED,   f"\n  ⬇  {mgr_team.name} DESCIENDE de {comp_name}"))
    elif not is_l1 and mgr_pos <= 3:
        print(_c(GREEN, f"\n  ⬆  {mgr_team.name} ASCIENDE a Primera División"))

    old_p = data["manager"]["prestige"]
    new_p = min(10, old_p + 1) if met else max(1, old_p - 1)
    print()
    print(_c(CYAN, f"  Prestigio: {_prestige_label(old_p)}  →  {_prestige_label(new_p)}"))
    print()
    print_standings(standings, f"{comp_name} — {data['season']}", relegated_from=n_rel)

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
    pt = "  [PÉRD.TIEMPO]" if t.get("perdidaTiempo", 0) == 1 else ""
    return f"{tj} · {tm} · Presión {tp} · Toque {t.get('porcToque',50)}% · Contra {t.get('porcContra',30)}%{pt}"


def _tactic_menu(data: dict):
    t = data.setdefault("tactic", dict(DEFAULT_TACTIC))
    print(_c(BOLD + YELLOW, "\n  ═══ TÁCTICA ═══"))

    while True:
        print(_c(GRAY, f"\n  Ajuste táctico actual: {_tactic_adj(t):+.1f}"))
        print(f"  1. Tipo de juego    : {_c(CYAN, _TACTIC_LABELS['tipoJuego'][t['tipoJuego']])}")
        print(f"  2. Marcaje          : {_c(CYAN, _TACTIC_LABELS['tipoMarcaje'][t['tipoMarcaje']])}")
        print(f"  3. Presión          : {_c(CYAN, _TACTIC_LABELS['tipoPresion'][t['tipoPresion']])}")
        print(f"  4. Despejes         : {_c(CYAN, _TACTIC_LABELS['tipoDespejes'][t['tipoDespejes']])}")
        print(f"  5. Faltas           : {_c(CYAN, _TACTIC_LABELS['faltas'][t['faltas']])}")
        print(f"  6. % Toque          : {_c(CYAN, str(t['porcToque']))}%")
        print(f"  7. % Contragolpe    : {_c(CYAN, str(t['porcContra']))}%")
        pt_lbl = _c(YELLOW, "ACTIVA") if t.get("perdidaTiempo", 0) == 1 else _c(GRAY, "NO")
        print(f"  8. Pérdida de tiempo: {pt_lbl}  {_c(GRAY, '(más amarillas · +tiempo añadido)')}")
        print(_c(CYAN, "  0. Volver"))

        op = input_int("  Opción: ", 0, 8)
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


# ---------------------------------------------------------------------------
# Modo Entrenador — animated pitch view
# ---------------------------------------------------------------------------

_COMM_ATT = [
    "Disparo que sale desviado.",
    "Fuera de juego — el juego continúa.",
    "Córner. El portero atrapa el balón.",
    "Remate de cabeza al larguero.",
    "Gran parada del portero.",
    "Falta peligrosa desde la frontal.",
    "Balón en el área pero sin rematador.",
    "El delantero controla y dispara — fuera.",
]

_COMM_MID = [
    "El juego se detiene por una falta.",
    "Duelo físico en el centro del campo.",
    "La afición anima a su equipo.",
    "Balón dividido cerca del área central.",
    "El árbitro pita falta.",
    "Ritmo de partido alto.",
    "Intercambio de posiciones en el centro.",
    "El balón recorre el campo de lado a lado.",
]


def _squad_rows(players: list["Player"]):
    """Split squad into GK / DEF / MID / ATT for pitch display (≤4 each)."""
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
        phase   = "1ª" if minute <= 45 else "2ª"
        min_str = f"{minute:02d}'"
    else:
        phase   = "2ª" if str(minute).startswith("90") else "1ª"
        min_str = str(minute)
    score  = f"{hg}-{ag}" if is_home_mgr else f"{ag}-{hg}"

    os.system("cls" if os.name == "nt" else "clear")

    BALL = "⚽"

    def tokens(players, n=4):
        ts = [f"[{p.name.split()[-1][:4]:^4}]" for p in players[:n]]
        return "  ".join(ts) if ts else "[   —   ]"

    def prow(players, color, n=4, ball=False):
        bm = f" {BALL} " if ball else "   "
        print(f"   {bm}{_c(color, tokens(players, n))}")

    def gkrow(gk_list, color, ball=False):
        nm = gk_list[0].name.split()[-1][:8] if gk_list else "---"
        bm = f" {BALL} " if ball else "   "
        print(f"   {bm}{_c(color, f'[ GK · {nm} ]')}")

    print()
    print(_c(BOLD + CYAN, f"  {'═'*52}"))
    print(f"  {home.name[:18]:<18}  {phase} {min_str}  {_c(sc+BOLD, score)}  {away.name[:18]}")
    print(_c(BOLD + CYAN, f"  {'═'*52}"))
    print()

    # ── Opponent half (top) ──
    gkrow(opp_gk, GRAY,   ball=(ball_zone == 0))
    prow (opp_df, GRAY,   ball=(ball_zone == 1))
    prow (opp_md, GRAY,   ball=False)
    prow (opp_at, GRAY,   ball=(ball_zone == 2))
    print()

    # ── Centre line ──
    ml   = list("· " * 24)
    if ball_zone == 2:
        ml[len(ml)//2] = BALL
    print(_c(GRAY, f"   {''.join(ml)[:48]}"))
    print()

    # ── Manager half (bottom) ──
    prow (mgr_at, YELLOW, ball=(ball_zone == 2))
    prow (mgr_md, YELLOW, ball=False)
    prow (mgr_df, YELLOW, ball=(ball_zone == 3))
    gkrow(mgr_gk, YELLOW, ball=(ball_zone == 4))
    print()

    print(_c(BOLD + CYAN,   f"  {'═'*52}"))
    print(_c(YELLOW + BOLD, f"  ► {mgr.name}"))
    print(_c(BOLD + CYAN,   f"  {'═'*52}"))

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
    print(_c(YELLOW + BOLD, "\n  ── SUSTITUCIÓN ──"))
    print(_c(GRAY, f"  {'#':>3}  {'JUGADOR':<26} {'POSICIÓN':<18} {'ME':>3}"))
    print(_c(GRAY, "  " + "─" * 52))
    for i, p in enumerate(squad, 1):
        tag = _c(RED, " ·sale·") if p.name in subs_made else ""
        print(f"  {i:>3}  {p.name[:25]:<26} {p.position[:17]:<18} {p.me:>3}{tag}")
    print()
    out_i = input_int(f"  Sale  (1-{len(squad)}, 0=cancelar): ", 0, len(squad))
    if out_i == 0:
        return False
    in_i = input_int(f"  Entra (1-{len(squad)}, 0=cancelar): ", 0, len(squad))
    if in_i == 0 or in_i == out_i:
        return False
    subs_made.append(squad[out_i - 1].name)
    print(_c(GREEN, f"  ✓ Sale {squad[out_i-1].name}  →  Entra {squad[in_i-1].name}\n"))
    return True


def _match_entrenador(
    home: "Team", away: "Team", seed: int,
    mgr_slot: int, data: dict,
) -> tuple:
    """
    Modo entrenador: 90 min. animados con fichas, VAR, tiempo añadido,
    5 cambios / 3 ventanas en juego / ventana libre en el descanso,
    6º cambio de concusión si hay golpe en la cabeza.
    """
    tactic_ref = [data.get("tactic", dict(DEFAULT_TACTIC))]

    # ── Determine initial result from Poisson ─────────────────────────────────
    rng      = random.Random(seed)
    ht0      = tactic_ref[0] if home.slot_id == mgr_slot else None
    at0      = tactic_ref[0] if away.slot_id == mgr_slot else None
    home_str = (home.strength() + _tactic_adj(ht0)) * 1.05
    away_str =  away.strength() + _tactic_adj(at0)
    total    = max(home_str + away_str, 0.01)
    lh       = max(0.3, min(2.8 * (home_str / total), 4.5))
    la       = max(0.3, min(2.8 * (away_str / total), 4.5))
    base_hg  = _poisson_goals(lh, rng)
    base_ag  = _poisson_goals(la, rng)

    # ── Generate event schedule ───────────────────────────────────────────────
    pool    = list(range(1, 91))
    hg_mins = sorted(rng.sample(pool, min(base_hg, len(pool)))) if base_hg else []
    ag_mins = sorted(rng.sample(pool, min(base_ag, len(pool)))) if base_ag else []

    # Base yellow cards (more if time-wasting active)
    n_yellow = rng.randint(2, 5)
    if tactic_ref[0].get("perdidaTiempo", 0) == 1:
        n_yellow += rng.randint(1, 3)

    events: list = []
    for m in hg_mins: events.append((m, "goal",   home.slot_id))
    for m in ag_mins: events.append((m, "goal",   away.slot_id))
    for _ in range(n_yellow):
        ev_type = "yellow_tw" if (
            tactic_ref[0].get("perdidaTiempo", 0) == 1
            and rng.random() < 0.4
        ) else "yellow"
        events.append((rng.randint(5, 88), ev_type,
                       rng.choice([home.slot_id, away.slot_id])))
    # Head-injury event (30% chance)
    if rng.random() < 0.30:
        events.append((rng.randint(10, 85), "injury",
                       rng.choice([home.slot_id, away.slot_id])))
    events.sort()

    # ── Match state ───────────────────────────────────────────────────────────
    is_home_mgr  = home.slot_id == mgr_slot
    mgr_team_obj = home if is_home_mgr else away
    our_str      = home_str if is_home_mgr else away_str
    opp_str      = away_str if is_home_mgr else home_str

    # Mutable refs (lists so inner functions can write without nonlocal)
    cur       = [0, 0]       # [cur_hg, cur_ag]
    ball      = [2]          # ball_zone
    subs      = [5]          # remaining substitutions
    wins      = [3]          # remaining in-play windows
    concuss   = [True]       # concussion sub available (6th sub)
    smade     = []           # names substituted out
    halfdone  = [False]
    # For added-time calculation
    yellows   = [0, 0]       # per half
    var_rev   = [0, 0]
    goals_h   = [0, 0]
    subs_h    = [0, 0]
    TICK      = 0.13
    ev_idx    = [0]

    # ── Inner helpers ─────────────────────────────────────────────────────────
    def draw(minute, msg=""):
        _draw_frame(home, away, is_home_mgr, cur[0], cur[1], minute, ball[0], msg)

    def collect(minute):
        evs = []
        while ev_idx[0] < len(events) and events[ev_idx[0]][0] == minute:
            evs.append(events[ev_idx[0]])
            ev_idx[0] += 1
        return evs

    def sub_window(half_idx: int):
        """Let manager make multiple subs in one window; uses one window slot."""
        while subs[0] > 0:
            if not _entrenador_substitution(mgr_team_obj, smade):
                break
            subs[0] -= 1
            subs_h[half_idx] += 1
            if subs[0] == 0:
                break
            nx = input(_c(CYAN, f"  ¿Otro cambio? ({subs[0]} restantes) [S=Sí / Intro=No]: ")).strip().upper()
            if nx != 'S':
                break
        wins[0] -= 1

    def offer_stop(minute, msg: str):
        """Tactical pause: offer window / tactic change / continue."""
        draw(minute, msg)
        print()
        if wins[0] > 0 and subs[0] > 0:
            print(_c(CYAN,   f"  W. Ventana de cambios  ({wins[0]} ventanas · {subs[0]} cambios)"))
        print(_c(CYAN,       "  T. Cambiar táctica"))
        print(_c(CYAN,       "  0. Continuar"))
        ch = input("  > ").strip().upper()
        half_idx = 1 if halfdone[0] else 0
        if ch == "W" and wins[0] > 0 and subs[0] > 0:
            sub_window(half_idx)
        elif ch == "T":
            _tactic_menu(data)
            tactic_ref[0] = data.get("tactic", tactic_ref[0])

    def process_goal(ev_min, ev_team, minute):
        half_idx = 1 if halfdone[0] else 0
        is_mgr   = (ev_team == mgr_slot)
        tname    = home.name if ev_team == home.slot_id else away.name
        if ev_team == home.slot_id:
            ball[0] = 0 if is_home_mgr else 4
            cur[0] += 1
        else:
            ball[0] = 0 if not is_home_mgr else 4
            cur[1] += 1
        goals_h[half_idx] += 1
        gc  = GREEN if is_mgr else RED
        msg = _c(gc + BOLD, f"⚽  ¡GOL de {tname}!   {cur[0]} - {cur[1]}")
        draw(minute, msg)
        time.sleep(2.0)
        # VAR review (18% chance)
        var_disallowed = False
        if rng.random() < 0.18:
            var_rev[half_idx] += 1
            draw(minute, _c(BOLD + CYAN, "🎥  VAR revisando el gol..."))
            time.sleep(2.0)
            if rng.random() < 0.38:
                reason = rng.choice(["fuera de juego", "mano en el área",
                                     "penalti no señalado en el origen"])
                if ev_team == home.slot_id: cur[0] -= 1
                else:                       cur[1] -= 1
                goals_h[half_idx] -= 1
                dc = RED if is_mgr else GREEN
                draw(minute, _c(dc + BOLD, f"❌  GOL ANULADO — VAR: {reason}"))
                time.sleep(2.5)
                ball[0] = 2
                var_disallowed = True
            else:
                draw(minute, _c(GREEN + BOLD, "✅  GOL CONFIRMADO por VAR"))
                time.sleep(1.5)
        if not var_disallowed:
            offer_stop(minute, msg)
            ball[0] = 2

    def process_yellow(ev_min, ev_type, ev_team, minute):
        half_idx = 1 if halfdone[0] else 0
        is_mgr   = (ev_team == mgr_slot)
        tname    = home.name if ev_team == home.slot_id else away.name
        yellows[half_idx] += 1
        tw  = " — pérdida de tiempo" if ev_type == "yellow_tw" else ""
        yc  = YELLOW if is_mgr else GRAY
        draw(minute, _c(yc, f"🟡  Amarilla{tw}  —  {tname}  (min. {ev_min})"))
        time.sleep(1.0)

    def process_injury(ev_min, ev_team, minute):
        tname = home.name if ev_team == home.slot_id else away.name
        half_idx = 1 if halfdone[0] else 0
        if concuss[0] and ev_team == mgr_slot:
            draw(minute, _c(RED + BOLD,
                            f"🚑  ¡GOLPE EN LA CABEZA! — {tname} — CAMBIO DE CONCUSIÓN"))
            print(_c(YELLOW, "  6ª sustitución por traumatismo (no cuenta como ventana)"))
            if _entrenador_substitution(mgr_team_obj, smade):
                concuss[0]  = False
                subs_h[half_idx] += 1
        else:
            draw(minute, _c(GRAY, f"🚑  Golpe leve — {tname} continúa  (min. {ev_min})"))
            time.sleep(1.0)

    # ── Opening frame ─────────────────────────────────────────────────────────
    draw(1, _c(GRAY, "  [Intro] para comenzar el partido..."))
    input()

    # ── First half (1–45) ─────────────────────────────────────────────────────
    for minute in range(1, 46):
        for ev_min, ev_type, ev_team in collect(minute):
            if ev_type == "goal":
                process_goal(ev_min, ev_team, minute)
            elif ev_type in ("yellow", "yellow_tw"):
                process_yellow(ev_min, ev_type, ev_team, minute)
            elif ev_type == "injury":
                process_injury(ev_min, ev_team, minute)
        ball[0] = _ball_drift(ball[0], rng, our_str, opp_str)
        draw(minute)
        time.sleep(TICK)

    # ── Added time — 1st half ─────────────────────────────────────────────────
    at1 = max(1, min(6,
               1 + yellows[0] + var_rev[0] * 2 + goals_h[0] + rng.randint(0, 1)))
    draw(f"45+{at1}", _c(BOLD + YELLOW, f"⏱  TIEMPO AÑADIDO: +{at1} min."))
    time.sleep(1.5)
    for a in range(1, at1 + 1):
        draw(f"45+{a}")
        time.sleep(TICK * 0.6)

    # ── Halftime break (free window — no win slot used) ───────────────────────
    halfdone[0] = True
    draw("HT", _c(BOLD + YELLOW, "━━━━━━━━━━  DESCANSO  ━━━━━━━━━━"))
    print()
    while True:
        print(_c(CYAN, "  T. Cambiar táctica"))
        if subs[0] > 0:
            print(_c(CYAN, f"  S. Sustitución  ({subs[0]} cambios · ventana de descanso — GRATIS)"))
        print(_c(CYAN, "  0. Continuar con la 2ª parte"))
        ch = input("  Opción: ").strip().upper()
        if ch in ("0", ""):
            break
        elif ch == "T":
            _tactic_menu(data)
            tactic_ref[0] = data.get("tactic", tactic_ref[0])
        elif ch == "S" and subs[0] > 0:
            while subs[0] > 0:
                if not _entrenador_substitution(mgr_team_obj, smade):
                    break
                subs[0] -= 1; subs_h[1] += 1
                if subs[0] == 0:
                    break
                nx = input(_c(CYAN, f"  ¿Otro cambio? ({subs[0]} restantes) [S/N]: ")).strip().upper()
                if nx != 'S':
                    break
    ball[0] = 2

    # ── Second half (46–90) ───────────────────────────────────────────────────
    for minute in range(46, 91):
        for ev_min, ev_type, ev_team in collect(minute):
            if ev_type == "goal":
                process_goal(ev_min, ev_team, minute)
            elif ev_type in ("yellow", "yellow_tw"):
                process_yellow(ev_min, ev_type, ev_team, minute)
            elif ev_type == "injury":
                process_injury(ev_min, ev_team, minute)
        # In-play substitution windows at 60', 70', 80'
        if minute in (60, 70, 80) and wins[0] > 0 and subs[0] > 0:
            offer_stop(minute, _c(CYAN,
                       f"  min.{minute}' — Parada táctica  ({wins[0]} ventanas · {subs[0]} cambios)"))
        ball[0] = _ball_drift(ball[0], rng, our_str, opp_str)
        draw(minute)
        time.sleep(TICK)

    # ── Added time — 2nd half ─────────────────────────────────────────────────
    at2 = max(2, min(10,
               2 + yellows[1] + var_rev[1] * 2 + goals_h[1]
               + int(subs_h[1] * 0.5)
               + (2 if tactic_ref[0].get("perdidaTiempo", 0) == 1 else 0)
               + rng.randint(0, 3)))
    draw(90, _c(BOLD + YELLOW, f"⏱  TIEMPO AÑADIDO: +{at2} min."))
    time.sleep(2.0)
    for a in range(1, at2 + 1):
        draw(f"90+{a}")
        time.sleep(TICK * 0.5)

    # ── Full time ─────────────────────────────────────────────────────────────
    fhg, fag = cur[0], cur[1]
    won  = (is_home_mgr and fhg > fag) or (not is_home_mgr and fag > fhg)
    lost = (is_home_mgr and fhg < fag) or (not is_home_mgr and fag < fhg)
    rc   = GREEN if won else (RED if lost else CYAN)
    res  = "¡VICTORIA!" if won else ("DERROTA" if lost else "EMPATE")
    draw("FT", _c(rc + BOLD, f"━━  PITIDO FINAL  ━━   {res}   {fhg} - {fag}"))
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
    mgr_names = {p.name for p in mgr_team.players}

    avail: list[tuple[Team, Player]] = [
        (t, p)
        for t in liga1 + liga2
        if t.slot_id != mgr_team.slot_id
        for p in t.players
        if p.name not in mgr_names
    ]

    print(_c(CYAN, "\n  Filtrar por posición: 1.Todos  2.Porteros  3.Defensas  4.Medios  5.Delanteros"))
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

    print(_c(BOLD + YELLOW, f"\n  Jugadores disponibles (presupuesto: €{budget:,.0f})"))
    print(_c(GRAY, f"  {'#':>3}  {'JUGADOR':<24} {'EQUIPO':<22} {'ME':>3} {'VALOR':<12}"))
    print(_c(GRAY, "  " + "─" * 68))
    for i, (t, p) in enumerate(candidates, 1):
        val   = f"€{p.market_value/1e6:.1f}M" if p.market_value >= 1e6 else f"€{p.market_value//1000}K"
        color = RESET if p.market_value <= budget else GRAY
        print(color + f"  {i:>3}  {p.name[:23]:<24} {t.name[:21]:<22} {p.me:>3} {val}" + RESET)
    print()

    idx = input_int(f"  Fichar (1-{len(candidates)}, 0=cancelar): ", 0, len(candidates))
    if idx == 0:
        return
    src_team, player = candidates[idx - 1]
    fee = player.market_value
    if fee > budget:
        print(_c(RED, f"  Sin presupuesto (necesitas €{fee:,.0f}, tienes €{budget:,.0f}).\n"))
        return
    print(_c(YELLOW, f"\n  Fichar {player.name} de {src_team.name} por €{fee:,.0f}?"))
    if input_int("  1.Confirmar  0.Cancelar: ", 0, 1) == 0:
        return
    data["budget"] = budget - fee
    data.setdefault("bought", []).append({"player_name": player.name, "from_slot": src_team.slot_id, "paid": fee})
    src_team.players  = [p for p in src_team.players if p.name != player.name]
    mgr_team.players.append(player)
    _save_career(data)
    print(_c(GREEN, f"  ✓ {player.name} fichado. Presupuesto restante: €{data['budget']:,.0f}\n"))


def _market_sell(data: dict, mgr_team: Team):
    if not mgr_team.players:
        print(_c(GRAY, "  Plantilla vacía.\n"))
        return
    spl = sorted(mgr_team.players, key=lambda p: p.overall, reverse=True)
    print(_c(BOLD + YELLOW, "\n  Vender jugador:"))
    print(_c(GRAY, f"  {'#':>3}  {'JUGADOR':<26} {'POS':<18} {'ME':>3}  VENTA(60%)"))
    print(_c(GRAY, "  " + "─" * 64))
    for i, p in enumerate(spl, 1):
        sale = int(p.market_value * 0.6)
        val  = f"€{sale/1e6:.1f}M" if sale >= 1e6 else f"€{sale//1000}K"
        print(f"  {i:>3}  {p.name[:25]:<26} {p.position[:17]:<18} {p.me:>3}  {val}")
    print()
    idx = input_int(f"  Vender (1-{len(spl)}, 0=cancelar): ", 0, len(spl))
    if idx == 0:
        return
    player = spl[idx - 1]
    sale   = int(player.market_value * 0.6)
    print(_c(YELLOW, f"\n  Vender {player.name} por €{sale:,.0f}?"))
    if input_int("  1.Confirmar  0.Cancelar: ", 0, 1) == 0:
        return
    data["budget"] = data.get("budget", 0) + sale
    data.setdefault("sold", []).append(player.name)
    mgr_team.players = [p for p in mgr_team.players if p.name != player.name]
    _save_career(data)
    print(_c(GREEN, f"  ✓ {player.name} vendido. Presupuesto: €{data['budget']:,.0f}\n"))


def _market_menu(data: dict, mgr_team: Team, all_slots: dict[int, Team],
                 liga1: list[Team], liga2: list[Team]):
    cur_md   = data.get("current_matchday", 1)
    tot_md   = 38 if data["competition"] == "ES1" else 42
    window   = _is_window_open(cur_md, tot_md)
    budget   = data.get("budget", 0)
    status   = _c(GREEN, "ABIERTA") if window else _c(RED, "CERRADA")

    print(_c(BOLD + YELLOW, "\n  ═══ MERCADO DE FICHAJES ═══"))
    print(f"  {mgr_team.name}   |   Presupuesto: €{budget:,.0f}   |   Ventana: {status}")
    print()
    while True:
        print(_c(CYAN, "  1. Buscar jugadores"))
        print(_c(CYAN, "  2. Vender jugador"))
        print(_c(CYAN, "  3. Ver mi plantilla"))
        print(_c(CYAN, "  0. Volver"))
        op = input_int("  Opción: ", 0, 3)
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
    print(_c(BOLD + YELLOW, "\n  ═══ VENTANA DE MERCADO DE INVIERNO ═══"))
    ans = input_str("  ¿Quieres fichar algún jugador? (S/N): ").strip().upper()
    if not ans.startswith("S"):
        print(_c(GRAY, "  Continúa la temporada sin movimientos.\n"))
        return

    budget = int(data.get("budget", 0))
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

    print(_c(GRAY, f"\n  Presupuesto disponible: €{budget:,.0f}"))
    print(_c(GRAY, f"  {'#':>3}  {'JUGADOR':<24} {'EQUIPO':<22} {'ME':>3} {'VALOR':<10}"))
    print(_c(GRAY, "  " + "─" * 66))
    for i, (team, player) in enumerate(picks, 1):
        val = f"€{player.market_value/1e6:.1f}M" if player.market_value >= 1_000_000 else f"€{player.market_value//1000}K"
        color = RESET if player.market_value <= budget else GRAY
        print(color + f"  {i:>3}  {player.name[:23]:<24} {team.name[:21]:<22} {player.me:>3} {val:<10}" + RESET)
    print()

    idx = input_int(f"  Fichar (1-{len(picks)}, 0=cancelar): ", 0, len(picks))
    if idx == 0:
        print(_c(GRAY, "  Operación cancelada.\n"))
        return
    src_team, player = picks[idx - 1]
    fee = int(player.market_value)
    if fee > budget:
        print(_c(RED, f"  Sin presupuesto (necesitas €{fee:,.0f}, tienes €{budget:,.0f}).\n"))
        return

    if input_int("  1. Confirmar fichaje  0. Cancelar: ", 0, 1) == 0:
        print(_c(GRAY, "  Operación cancelada.\n"))
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
    print(_c(GREEN, f"  ✓ {player.name} fichado por €{fee:,.0f}. Presupuesto: €{data['budget']:,.0f}\n"))


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
        print(_c(BOLD + YELLOW, f"\n  ═══ COPA DEL REY · {label} ═══"))
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
        news.append(f"J{md}: ¡Tu equipo gana la Copa del Rey!")

    _save_career(data)
    return matches


# ---- Main season loop ------------------------------------------------------

def _season_loop(data: dict, liga1: list[Team], liga2: list[Team], liga_rfef: list[Team] = []):
    comp_key = data["competition"]
    is_l1 = comp_key == "ES1"
    is_l2 = comp_key == "ES2"
    if is_l1:
        comp_t = sorted(liga1, key=lambda t: t.slot_id)
        n_rel = 3
        tot_md = 38
    elif is_l2:
        comp_t = sorted(liga2, key=lambda t: t.slot_id)
        n_rel = 4
        tot_md = 42
    else:
        comp_t = sorted([t for t in liga_rfef if t.comp == comp_key], key=lambda t: t.slot_id)
        if not comp_t:
            comp_t = sorted(liga_rfef, key=lambda t: t.slot_id)
        n_rel = 4
        tot_md = 38
    if not comp_t:
        print(_c(RED, f"\n  No hay equipos cargados para la competicion {comp_key}."))
        return
    fpm       = len(comp_t) // 2
    mgr_slot  = data["team_slot"]
    mgr_team  = {t.slot_id: t for t in comp_t}[mgr_slot]
    mgr_name  = data["manager"]["name"]
    seed      = data["season_seed"]
    tbs       = {t.slot_id: t for t in comp_t}   # teams_by_slot
    all_slots = {t.slot_id: t for t in liga1 + liga2 + liga_rfef}

    # Apply saved squad changes (fichajes/ventas)
    _apply_squad_changes(mgr_team, all_slots, data)
    data.setdefault("players", _build_players_snapshot(liga1 + liga_rfef, liga2, data.get("season", "2025-26")))
    if not isinstance(data.get("copa"), dict) or data.get("copa", {}).get("season") != data.get("season"):
        data["copa"] = _init_copa_state(data, liga1, liga2)
        _save_career(data)

    # Build fixture map (deterministic, sorted by slot_id)
    all_fix    = generate_fixtures(comp_t)
    fix_by_md: dict[int, list[tuple[Team, Team]]] = {}
    for i, pair in enumerate(all_fix):
        fix_by_md.setdefault((i // fpm) + 1, []).append(pair)

    results = data.setdefault("results", [])
    news    = data.setdefault("news", [])
    cur_md  = data.get("current_matchday", 1)

    while cur_md <= tot_md:
        standings = _standings_from_results(results, tbs)
        _pm_header(data, cur_md, tot_md)
        _mini_standings(standings, mgr_slot, n_rel)

        my_fix = next(((h, a) for h, a in fix_by_md.get(cur_md, [])
                       if h.slot_id == mgr_slot or a.slot_id == mgr_slot), None)
        if my_fix:
            h, a = my_fix
            print(_c(CYAN, f"  PRÓXIMO PARTIDO — Jornada {cur_md}:"))
            print(_c(BOLD + YELLOW, f"  {h.name}  vs  {a.name}"))
            print()
        copa_label = _active_copa_round_label(data.get("copa", {}), cur_md)
        if copa_label:
            print(_c(BOLD + CYAN, f"  COPA DEL REY: {copa_label} (ronda activa)"))
            print()

        tactic  = data.setdefault("tactic", dict(DEFAULT_TACTIC))
        win_tag = _c(GREEN, "[ABIERTO]") if _is_window_open(cur_md, tot_md) else _c(RED, "[CERRADO]")
        print(_c(GRAY, f"  Táctica: {_tactic_summary(tactic)}"))
        print()
        print(_c(CYAN, f"  1. Simular jornada {cur_md}"))
        print(_c(CYAN,  "  2. Clasificación completa"))
        print(_c(CYAN,  "  3. Plantilla"))
        print(_c(CYAN,  "  4. Noticias"))
        print(_c(CYAN,  "  5. Simular resto de temporada"))
        print(_c(CYAN,  f"  6. Mercado de fichajes {win_tag}"))
        print(_c(CYAN,  "  7. Táctica"))
        print(_c(CYAN,  "  0. Guardar y salir"))

        op = input_int("  Opción: ", 0, 7)

        if op == 0:
            data["current_matchday"] = cur_md
            _save_career(data)
            print(_c(GREEN, "  Partida guardada.\n"))
            return

        elif op == 1:
            md_res = []
            for h, a in fix_by_md.get(cur_md, []):
                s            = seed ^ (cur_md * 7919 + h.slot_id * 31 + a.slot_id)
                is_mgr_match = h.slot_id == mgr_slot or a.slot_id == mgr_slot
                ht           = tactic if h.slot_id == mgr_slot else None
                at           = tactic if a.slot_id == mgr_slot else None
                if is_mgr_match:
                    print(_c(CYAN, "\n  Modo de partido:"))
                    print(_c(CYAN, "  1. Simular automáticamente"))
                    print(_c(CYAN, "  2. Modo Entrenador  (fichas animadas)"))
                    pm = input_int("  Opción (1-2): ", 1, 2)
                    if pm == 2:
                        hg, ag = _match_entrenador(h, a, s, mgr_slot, data)
                    else:
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
            if new_items:
                for ni in new_items:
                    print(_c(YELLOW, f"  📰 {ni}"))
                print()
            _play_copa_round(data, cur_md, all_slots, mgr_slot, show_output=True)
            cur_md += 1
            data["current_matchday"] = cur_md
            _save_career(data)
            _pause()

        elif op == 2:
            label = "PRIMERA" if is_l1 else "SEGUNDA"
            print_standings(standings, f"{label} DIVISIÓN {data['season']}", relegated_from=n_rel)

        elif op == 3:
            print_squad(mgr_team)

        elif op == 4:
            if not news:
                print(_c(GRAY, "  Sin noticias aún.\n"))
            else:
                print(_c(YELLOW, "\n  ─── NOTICIAS ───"))
                for ni in news[-5:]:
                    print(f"  • {ni}")
                print()

        elif op == 6:
            _market_menu(data, mgr_team, all_slots, liga1, liga2)

        elif op == 7:
            _tactic_menu(data)
            tactic = data.get("tactic", tactic)

        elif op == 5:
            print(_c(YELLOW, f"\n  Simulando jornadas {cur_md}–{tot_md}..."))
            winter_md = 21 if tot_md >= 42 else max(1, tot_md // 2)
            for md in range(cur_md, tot_md + 1):
                md_res = []
                for h, a in fix_by_md.get(md, []):
                    s  = seed ^ (md * 7919 + h.slot_id * 31 + a.slot_id)
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
                if md == winter_md and not data.get("winter_market_done", False):
                    data["winter_market_done"] = True
                    _save_career(data)
                    _winter_market_menu(data, mgr_team, comp_t, md)
                _play_copa_round(data, md, all_slots, mgr_slot, show_output=False)
            cur_md = tot_md + 1
            data["current_matchday"] = cur_md
            _save_career(data)
            print(_c(GREEN, "  ✓ Temporada completada.\n"))
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

    team = _show_offers(data, liga1, liga2, liga_rfef)
    next_season = _next_season_str(data.get("season", "2025-26"))
    _setup_season(data, team, liga1, liga2, next_season)
    print(_c(GREEN, f"\n  ¡{data['manager']['name']} ficha por {team.name}!"))
    print(_c(YELLOW, f"  Objetivo: {data['objective']}"))
    _pause()
    _season_loop(data, liga1, liga2, liga_rfef)


# ---- Helpers ---------------------------------------------------------------

def _next_season_str(season: str) -> str:
    try:
        ns = int(season.split("-")[0]) + 1
        return f"{ns}-{(ns % 100) + 1:02d}"
    except Exception:
        return "2026-27"


def _setup_season(data: dict, team: Team, liga1: list[Team], liga2: list[Team], season: str):
    m = data["manager"]
    data.update({
        "phase":            "SEASON",
        "season":           season,
        "team_slot":        team.slot_id,
        "team_name":        team.name,
        "competition":      team.comp,
        "objective":        _assign_objective(team, liga1, liga2),
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
    })
    data.setdefault("players", _build_players_snapshot(liga1, liga2, season))
    _save_career(data)


def _comp_name(comp: str) -> str:
    return {"ES1": "Primera División", "ES2": "Segunda División",
            "E3G1": "1ª RFEF Grupo 1", "E3G2": "1ª RFEF Grupo 2"}.get(comp, comp)


def _show_offers(data: dict, liga1: list[Team], liga2: list[Team], liga_rfef: list[Team] = []) -> Optional[Team]:
    m      = data["manager"]
    offers = _generate_offers(m["prestige"], liga1, liga2, liga_rfef, data=data)
    print(_c(BOLD + YELLOW, "\n  ═══ SELECCIÓN DE OFERTA ═══"))
    print(_c(GRAY,  f"  Prestigio: {_prestige_label(m['prestige'])}  |  Temporadas: {m['total_seasons']}"))
    print()
    for i, t in enumerate(offers, 1):
        cn  = _comp_name(t.comp)
        obj = _assign_objective(t, liga1, liga2)
        print(_c(CYAN, f"  {i}. {t.name}"))
        print(f"       {cn}  ·  Fuerza: {t.strength():.1f}")
        print(f"       Objetivo: {obj}")
        print()
    idx = input_int(f"  Elige oferta (1-{len(offers)}): ", 1, len(offers))
    return offers[idx - 1]


# ---- Entry point -----------------------------------------------------------

def menu_promanager(liga1: list[Team], liga2: list[Team], liga_rfef: list[Team] = []):
    print(_c(BOLD + CYAN, "\n  ╔══════════════════════════════════╗"))
    print(_c(BOLD + CYAN, "  ║       MODO PRO MANAGER           ║"))
    print(_c(BOLD + CYAN, "  ╚══════════════════════════════════╝"))

    data = _load_career()

    if data:
        m = data["manager"]
        print(_c(YELLOW, f"\n  Partida guardada — {m['name']}  {_prestige_label(m['prestige'])}"))
        print(f"  {data.get('season','?')}  ·  {data.get('team_name','?')}  ·  J{data.get('current_matchday',1)}")
        _print_career_summary(data)
        print()
        if data.get("phase") == "POSTSEASON":
            print(_c(CYAN, "  1. Nueva temporada (nuevas ofertas)"))
            print(_c(CYAN, "  2. Borrar y crear nuevo manager"))
            print(_c(CYAN, "  0. Volver"))
            op = input_int("  Opción: ", 0, 2)
            if op == 0:
                return
            elif op == 1:
                team = _show_offers(data, liga1, liga2, liga_rfef)
                _setup_season(data, team, liga1, liga2, _next_season_str(data["season"]))
                print(_c(GREEN,  f"\n  ¡{m['name']} ficha por {team.name}!"))
                print(_c(YELLOW, f"  Objetivo: {data['objective']}"))
                _pause()
                _season_loop(data, liga1, liga2, liga_rfef)
                return
            else:
                data = None
        else:
            print(_c(CYAN, "  1. Continuar partida"))
            print(_c(CYAN, "  2. Borrar y crear nuevo manager"))
            print(_c(CYAN, "  0. Volver"))
            op = input_int("  Opción: ", 0, 2)
            if op == 0:
                return
            elif op == 1:
                _season_loop(data, liga1, liga2, liga_rfef)
                return
            else:
                data = None

    # Crear nuevo manager
    print(_c(BOLD + YELLOW, "\n  ─── NUEVO MANAGER ───"))
    name = input_str("  Nombre del manager: ")
    if not name:
        return
    data = {"manager": {"name": name, "prestige": 1, "total_seasons": 0, "history": []}}

    team = _show_offers(data, liga1, liga2, liga_rfef)
    _setup_season(data, team, liga1, liga2, "2025-26")
    m = data["manager"]
    print(_c(GREEN,  f"\n  ¡Bienvenido a {team.name}, {m['name']}!"))
    print(_c(YELLOW, f"  Objetivo de la junta: {data['objective']}"))
    _pause()
    _season_loop(data, liga1, liga2, liga_rfef)


def menu_multiplayer():
    print(_c(BOLD + CYAN, "\n  ╔══════════════════════════════════╗"))
    print(_c(BOLD + CYAN, "  ║      MULTIJUGADOR POR TURNOS     ║"))
    print(_c(BOLD + CYAN, "  ╚══════════════════════════════════╝"))
    print(_c(YELLOW, "\n  Esta variante del CLI no incluye multijugador por turnos."))
    print(_c(GRAY,   "  Usa android/cli/pcfutbol_cli.py para jugar en modo multi.\n"))
    _pause()


# ===========================================================================
# MENÚ PRINCIPAL
# ===========================================================================

def main_menu():
    print(_c(BOLD + CYAN,  "\n  ╔══════════════════════════════════╗"))
    print(_c(BOLD + CYAN,  "  ║    PC FÚTBOL 5  ·  CLI  2025/26  ║"))
    print(_c(BOLD + CYAN,  "  ║      Temporada real — Python      ║"))
    print(_c(BOLD + CYAN,  "  ╚══════════════════════════════════╝"))

    print(_c(YELLOW, "\n  Cargando datos de temporada 2025/26..."))
    all_teams = load_teams()

    liga1 = sorted([t for t in all_teams.values() if t.comp == "ES1"], key=lambda t: t.name)
    liga2 = sorted([t for t in all_teams.values() if t.comp == "ES2"], key=lambda t: t.name)
    liga_rfef = sorted([t for t in all_teams.values() if t.comp in ("E3G1", "E3G2")], key=lambda t: t.name)

    print(_c(GREEN, f"  ✓ {len(liga1)} equipos en Primera División"))
    print(_c(GREEN, f"  ✓ {len(liga2)} equipos en Segunda División"))
    print(_c(GREEN, f"  ✓ {len(liga_rfef)} equipos en 1ª RFEF"))
    total_players = sum(len(t.players) for t in liga1 + liga2 + liga_rfef)
    print(_c(GREEN, f"  ✓ {total_players} jugadores cargados\n"))

    while True:
        print(_c(CYAN,   "  ┌──────────────────────────────────┐"))
        print(_c(CYAN,   "  │         MENÚ PRINCIPAL           │"))
        print(_c(CYAN,   "  ├──────────────────────────────────┤"))
        print(_c(CYAN,   "  │  1. Simular temporada completa   │"))
        print(_c(CYAN,   "  │  2. Ver plantilla de equipo      │"))
        print(_c(CYAN,   "  │  3. Partido rápido               │"))
        print(_c(CYAN,   "  │  4. Top jugadores                │"))
        print(_c(CYAN,   "  │  5. Ranking de fortaleza         │"))
        print(_c(BOLD + CYAN, "  │  6. PRO MANAGER (modo carrera)  │"))
        print(_c(BOLD + CYAN, "  │  7. MULTIJUGADOR por turnos     │"))
        print(_c(BOLD + CYAN, "  │  8. Actualidad futbolistica      │"))
        print(_c(CYAN,   "  │  0. Salir                        │"))
        print(_c(CYAN,   "  └──────────────────────────────────┘"))

        op = input_int("  Opción: ", 0, 8)
        if op == 0:
            print(_c(GRAY, "\n  ¡Hasta la próxima temporada!\n"))
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
            menu_promanager(liga1, liga2, liga_rfef)
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

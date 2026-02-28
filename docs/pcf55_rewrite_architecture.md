# PCF55 Rewrite Architecture (From RE Findings)

Date: 2026-02-26
Goal: Rebuild the game end-to-end with deterministic behavior and compatibility with extracted legacy data.

## 1) Rewrite principles

1. Keep legacy semantics, not legacy binaries.
2. Separate data model from simulation logic.
3. Make all subsystems testable headless.
4. Preserve import/export compatibility with original files during transition.

## 2) Proposed subsystem split

## 2.1 `core-data`

Responsibilities:

- Parse legacy sources:
  - PKF (`EQUIPOS.PKF`, assets)
  - DBC competition files
  - LE string tables needed for labels/contracts
- Convert to canonical JSON schema:
  - `Team`
  - `Player`
  - `Competition`
  - `Calendar`
  - `Staff`, `Finance`, `Stadium`

Must expose:

- stable IDs (`team_slot_id`, `player_internal_id`)
- normalization for CP1252 and legacy rotated text
- import/export mappers

## 2.2 `season-state`

Responsibilities:

- In-memory state equivalent to `ACTLIGA` + `TACTICS` domains.
- Round-by-round progression, event log, injuries, sanctions, finances.
- Snapshot persistence to modern format (`.json`/sqlite) plus optional legacy export bridge.

Must include:

- manager profile state
- board objective tracking
- career history tracking (for ProManager offers)

## 2.3 `competition-engine`

Responsibilities:

- League/cup structure execution using decoded DBC structures.
- Promotions/relegations and qualification slots.
- Spanish tier handling:
  - First
  - Second
  - `LIGA2B` internal semantic replacement (modern `1a RFEF` mapping)

Must expose:

- fixture generation/import path
- standings tiebreak rules
- qualification outcome API

## 2.4 `match-sim`

Responsibilities:

- Deterministic result mode and optional full simulation mode.
- Consume tactical/player keys discovered in legacy contract:
  - pace/stamina/aggression/skill
  - morale/fitness
  - role/demarcation
  - tactical parameters (`tipojuego`, `tipomarcaje`, etc.)

Must expose:

- `simulate_result(match_context, seed)` -> score + event summary
- reproducible seeded outputs for QA replay

## 2.5 `manager-economy`

Responsibilities:

- Contracts, wages, transfer offers, release clauses.
- Budget/cashflow and board evaluation.
- Employee effects (psychologist/fisio/scout) on morale/recovery/scouting quality.

Must expose:

- decision APIs that can be used by GUI and automated tests
- objective evaluation hooks

## 2.6 `promanager-career`

Responsibilities:

- Manager identity/password flow.
- Offer generation at start and end-of-season.
- Career prestige progression and eligibility filters.

Current blocker to decode exactly:

- empty-offer bug in modernized dataset indicates hidden pool/filter dependency.

Must expose:

- `generate_initial_offers(manager_profile, season_state)`
- `generate_postseason_offers(manager_profile, objectives_result, season_state)`

## 2.7 `ui-shell`

Responsibilities:

- UI implementation independent from engine details.
- Legacy skin/theme can be layered later.
- First milestone can be utilitarian desktop UI + CLI debug views.

## 3) Rewrite data schema draft

## 3.1 Team

- `team_slot_id` (1..260)
- `name_short`, `name_full`, `stadium_name`
- `country_id`, `competition_id`
- `finance` (budget, members, sponsor, supplier)
- `staff`
- `squad` (list of player IDs)

## 3.2 Player

- `player_id` (legacy PID if available)
- identity fields (`name_short`, `name_full`, birth date)
- categorical fields (position, role-set, citizenship, status)
- attributes:
  - core: `VE`, `RE`, `AG`, `CA`, `ME`
  - technical: `PORTERO`, `ENTRADA`, `REGATE`, `REMATE`, `PASE`, `TIRO`
- contract fields

## 3.3 Competition

- `competition_code` (legacy key + modern alias)
- `format_type` (league, knockout, supercup)
- participants, rounds, promotion/relegation rules

## 3.4 Season state

- fixture cursor, current round
- standings snapshots
- injuries/sanctions
- transfer/offer log
- manager career state

## 4) Compatibility strategy

## 4.1 Phase A (read-only parity)

- Parse all legacy files into canonical JSON.
- Validate counts/hashes and cross-file references.

## 4.2 Phase B (result-mode parity)

- Implement result simulation and season progression.
- Run deterministic regression suite over multiple seasons.

## 4.3 Phase C (manager/pro-manager parity)

- Reproduce manager decisions and offer flows.
- Resolve current offer-generation unknowns using targeted RE traces.

## 4.4 Phase D (full productization)

- New UI shell.
- Save migration from legacy format.
- Tooling for modern database updates.

## 5) Test strategy for rewrite

1. Data integrity tests:
   - all team slots valid
   - all players attached to valid teams
   - competition participants valid
2. Engine consistency tests:
   - strength/value correlation
   - season statistical sanity checks
3. Career tests:
   - manager creation
   - offer generation non-empty in expected conditions
   - objective-driven offer improvement
4. Regression tests:
   - seeded season replay snapshots
   - known edge cases from legacy QA blockers

## 6) Immediate implementation backlog

1. Build `core-data` parser package from current scripts into a typed library.
2. Formalize `ACTLIGA` and `TACTICS` state schema.
3. Decode and implement offer-pool filter logic from ProManager.
4. Stand up headless `season-state + competition-engine + match-sim` runner.
5. Add a minimal manager CLI to validate full season loops before GUI rewrite.


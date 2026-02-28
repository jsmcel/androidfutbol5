# PCF55 Manager Depth Reverse Spec

Date: 2026-02-28
Scope: Reverse engineering baseline for manager depth systems:
- `cantera` / youth intake
- player evolution and aging
- training system
- staff/employee effects

This spec consolidates evidence from local RE docs, gameplay docs, and current code.

## 1) Evidence Inventory

### 1.1 Confirmed sources
- [`inversa/README.md`](../inversa/README.md)
  - Confirms executable/data map and original feature surface (`TACTICS`, `ACTLIGA`, DBC/PKF).
- [`docs/pcf55_reverse_spec.md`](./pcf55_reverse_spec.md)
  - Confirms manager/pro-manager anchors and save namespaces.
- [`docs/pcf55_rewrite_architecture.md`](./pcf55_rewrite_architecture.md)
  - Explicitly names employee effects: psychologist/fisio/scout on morale/recovery/scouting quality.
- [`android/FEATURE_AUDIT.md`](../android/FEATURE_AUDIT.md)
  - Enumerates original manager features: training, employees, juveniles, scouting, contracts.
- [`android/CODEX_TASK_sprint7_evolucion_jugadores.md`](../android/CODEX_TASK_sprint7_evolucion_jugadores.md)
  - Captures target behavior aligned to legacy-like progression rules.

### 1.2 Current implementation evidence
- Android engine:
  - [`android/match-sim/src/main/kotlin/com/pcfutbol/matchsim/PlayerDevelopmentEngine.kt`](../android/match-sim/src/main/kotlin/com/pcfutbol/matchsim/PlayerDevelopmentEngine.kt)
  - [`android/manager-economy/src/main/kotlin/com/pcfutbol/economy/EndOfSeasonUseCase.kt`](../android/manager-economy/src/main/kotlin/com/pcfutbol/economy/EndOfSeasonUseCase.kt)
- CLI engine:
  - [`cli/pcfutbol_cli.py`](../cli/pcfutbol_cli.py)
  - [`cli/CLI_DOCUMENTATION.md`](../cli/CLI_DOCUMENTATION.md)

## 2) Reconstructed Legacy Mechanics

Conventions:
- `[CONFIRMED]` present in reverse/game docs and/or already validated by engine behavior.
- `[INFERRED]` high-confidence reconstruction from available contracts and era mechanics.
- `[UNKNOWN]` not enough direct binary-level evidence yet.

### 2.1 Season state domains

- `[CONFIRMED]` Original runtime split:
  - `ACTLIGA/*` for season progression, rounds, matchday state.
  - `TACTICS/*` for manager identity, tactical presets, pro-manager persistence.
- `[CONFIRMED]` Rewrite should preserve semantic split even if storage changes.

### 2.2 Player lifecycle (aging + evolution)

- `[CONFIRMED]` Age-driven progression bands already implemented and documented:
  - Young players improve.
  - Prime ages fluctuate slightly.
  - Veterans decline in physical attributes.
  - Retirement at high age/low speed.
- `[CONFIRMED]` Current rules in project (Android + CLI):
  - improve `<24`
  - degrade `>=31` (stronger from `>=34`)
  - retire at `>=37` or `>=35` with low VE
- `[INFERRED]` In original manager depth, progression was affected by training quality and club staff (at least indirectly via morale/recovery/scouting and youth intake quality).

### 2.3 Cantera / juveniles

- `[CONFIRMED]` Youth generation is part of expected manager loop (project docs and current implementation include it).
- `[CONFIRMED]` Current project behavior:
  - Android: youth generated for manager team at season transition.
  - CLI: youth generated at season end.
- `[INFERRED]` Legacy had stronger coupling to staff (`juveniles`/scout) than current deterministic baseline.

### 2.4 Staff system

- `[CONFIRMED]` Original employee roster (from feature audit / legacy UX docs):
  - Segundo entrenador
  - Fisio
  - Psicologo
  - Asistente
  - Secretario
  - Ojeador
  - Juveniles
  - Cuidador
- `[CONFIRMED]` Rewrite architecture explicitly maps:
  - psychologist -> morale
  - fisio -> recovery
  - scout -> scouting quality
- `[INFERRED]` Remaining staff likely influence:
  - training efficiency / tactical adaptation
  - injury prevention
  - youth quality and intake volume
  - transfer-market filtering/negotiation quality

### 2.5 Training system

- `[CONFIRMED]` Training feature exists in original manager feature set and is currently missing in Android parity map.
- `[INFERRED]` Minimum viable training contract compatible with existing engine:
  - intensity (low/medium/high)
  - focus (balanced/physical/defensive/creative/attacking)
  - effects applied per season tick and optionally per matchday on form/morale/risk.
- `[UNKNOWN]` Exact original multipliers and week-to-week internals remain unresolved from current binary trace coverage.

## 3) Canonical Data Contract for Implementation

This section defines a rewrite-ready domain contract that can be applied in Android/CLI without depending on binary internals.

### 3.1 Staff

```text
StaffProfile
- segundoEntrenador: 0..100
- fisio: 0..100
- psicologo: 0..100
- asistente: 0..100
- secretario: 0..100
- ojeador: 0..100
- juveniles: 0..100
- cuidador: 0..100
```

### 3.2 Training

```text
TrainingPlan
- intensity: LOW | MEDIUM | HIGH
- focus: BALANCED | PHYSICAL | DEFENSIVE | TECHNICAL | ATTACKING
```

### 3.3 Seasonal development context

```text
DevelopmentContext
- staff: StaffProfile
- training: TrainingPlan
- seasonSeed: Long
```

## 4) Behavior Spec (Bring-to-Code Rules)

### 4.1 Player evolution modifiers

Base age rules remain as currently implemented.

Additive modifiers:
- `fisio` reduces veteran physical decline severity.
- `psicologo` reduces morale penalties from poor outcomes (season layer).
- `segundoEntrenador` improves training efficiency in non-prime ages.
- `juveniles` and `ojeador` improve youth intake quality and role fit.
- `cuidador` decreases injury carryover risk into next season.

### 4.2 Youth intake modifiers

- Base intake: 2 players/season to manager team.
- Effective intake quality:
  - +technical floor with `juveniles`.
  - better position distribution with `ojeador`.
  - small variance control with `segundoEntrenador`.

### 4.3 Training focus mapping

- `PHYSICAL` -> VE/RE/AG bias (+) and higher fatigue risk.
- `DEFENSIVE` -> ENTRADA/RE/AG bias (+).
- `TECHNICAL` -> PASE/REGATE/CA bias (+).
- `ATTACKING` -> REMATE/TIRO/REGATE bias (+).
- `BALANCED` -> no bias.

## 5) Gap vs Current Code

### 5.1 Already present
- deterministic season evolution engine.
- retirement logic.
- youth generation.
- end-of-season application and tests.

### 5.2 Missing for manager-depth parity
- persistent staff profile per manager/team.
- training plan persistence and UI.
- direct coupling staff/training -> evolution, morale, recovery, scouting.
- tactical/training weekly loop integration.

## 6) Confidence and Risk

- High confidence:
  - state split (`ACTLIGA`/`TACTICS`), lifecycle pattern, staff categories, need for employee effects.
- Medium confidence:
  - exact mapping of each staff role to numeric modifiers.
- Low confidence / pending RE:
  - original fixed constants, hidden fallback rules, and exact UI-era formulas.

## 7) Next Reverse Engineering Milestones

1. Extract/trace additional manager strings and xrefs around employee/training flows in `MANDOS.DAT`.
2. Recover field-level meaning of related `ACTLIGA` payload segments for staff/training persistence.
3. Build `ManagerDepthSpec v2` with constants once trace-backed.
4. Implement `v1` inferred model now (deterministic + tested), then calibrate to `v2`.

## 8) Practical Outcome

This reverse spec is implementation-ready for a first parity pass:
- It preserves confirmed legacy contracts.
- It formalizes missing systems in deterministic modern terms.
- It marks uncertain parts explicitly for further binary-level RE.

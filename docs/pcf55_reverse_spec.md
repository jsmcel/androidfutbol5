# PCF55 Reverse Engineering Spec (Rewrite Baseline)

Date: 2026-02-26
Repo: `C:\Users\Jose-Firebat\proyectos\pcfutbol`
Scope: Build a rewrite-ready technical map from the original game binaries and data.

## 1) High-level architecture

The game is split between executable LE modules and large data containers:

- Core LE modules:
  - `MANDOS.DAT` (MZ/LE): main manager/pro-manager UI flow, many engine tables, simulator contract strings.
  - `DBASEDOS.DAT` (MZ/LE): database/menus/support logic and resources.
  - `INFODOS.EXE` (MZ/LE): INFOFUT/documentation browser executable logic.
  - `TESTSYS.DAT` (MZ/LE): small system/test module.
- Main game executable wrappers:
  - `FUTBOL5.EXE` (MZ), `DOS4GW.EXE`, `DISCO.EXE`.
- Data model and assets:
  - `DBDAT/EQUIPOS.PKF` (teams + players payload, 260 team slots).
  - `DBDAT/*.DBC` (competitions, calendars, cups, standings contexts).
  - `*.PKF` assets: shields, kits, photos, resources.
  - `INFOFUT/*.HTM` internal functional documentation.

## 2) LE module layout (confirmed)

### `MANDOS.DAT`

- LE offset: `11408`
- Page size: `4096`
- Pages: `1066`
- Objects: `2`
  - obj1: base `0x00010000`, vsize `3740220`, flags `0x00002045`, page idx `1`, page count `914`
  - obj2: base `0x003B0000`, vsize `1352064`, flags `0x00002043`, page idx `915`, page count `152`

### `DBASEDOS.DAT`

- LE offset: `11408`
- Page size: `4096`
- Pages: `353`
- Objects: `2`
  - obj1: base `0x00010000`, vsize `1240716`, flags `0x00002045`, page idx `1`, page count `303`
  - obj2: base `0x00140000`, vsize `997696`, flags `0x00002043`, page idx `304`, page count `50`

## 3) PKF format and text encoding

### 3.1 Pointer table behavior

`EQUIPOS.PKF` and other PKF files use a pointer table starting near offset `232`.

- pointer entry marker: `0x02`
- pointer entry size: `38` bytes
- periodic jump block marker: `0x04` + 4-byte absolute jump target
- table terminator: `0x05 00 00 00 00`

Observed in current data:

- `DBDAT/EQUIPOS.PKF`: `260` pointers, `10` jump blocks.
- Multiple image/resource PKFs follow same family pattern.

### 3.2 Text codec

Game text in PKF payloads uses the known DM bijective byte rotation (involutive mapping).

- Implemented consistently in:
  - `tools/pcf55-updater/extract_pcf55_teams.py`
  - `tools/pcf55-updater/apply_name_stadium_patch.py`
  - `tools/pcf55-updater/apply_players_patch.py`
  - `tools/pcx-utils/src/Data/PCx/Byte/Rotate.hs`

## 4) Team and player data contracts

## 4.1 Team slots

- Team container (`EQUIPOS.PKF`) has 260 slots.
- Slot IDs are used globally across:
  - `EQUIPOS.PKF` payload chunks
  - competition DBC files (`LIGA.DBC`, `SCESPANA.DBC`, cups, jornadas)
  - UI flows in `MANDOS.DAT`.

## 4.2 Player record structure (practical contract)

From parser + patcher behavior (`apply_players_patch.py` and legacy parsers):

- record prefix `0x01`
- core order:
  - `pid (u16)`, `number (u8)`, `name`, `fullname`,
  - `index (u8)`, `status (u8)`, `roles[6]`,
  - `citizenship`, `skin`, `hair`, `position`,
  - birthday (`day`, `month`, `year u16`), `height`, `weight`,
  - attributes block (10 bytes)
- attributes order used by patcher:
  - `VE`, `RE`, `AG`, `CA`, `REMATE`, `REGATE`, `PASE`, `TIRO`, `ENTRADA`, `PORTERO`

Extended variants may include additional DB text fields before attribute bytes.

### 4.3 Safety rule discovered

Not every team chunk supports a stable player-chain rewrite.

- Chain guard was required and is now integrated:
  - If patched chunk loses valid player chain -> automatic per-team revert.

## 5) Runtime persistence contract (critical for rewrite)

### 5.1 Manager/season files found in `MANDOS.DAT`

`ACTLIGA` templates:

- `%c:ACTLIGA\%s.act`
- `%c:ACTLIGA\listaemp.act`
- `%c:ACTLIGA\listajug.act`
- `%c:ACTLIGA\noticias.act`
- `%c:ACTLIGA\PRET%04u.CPT`
- `%c:ACTLIGA\LIGA1%03u.CPT`
- `%c:ACTLIGA\LIGA2%03u.CPT`
- `%c:ACTLIGA\LIG2B%03u.CPT`
- `%c:ACTLIGA\CREY_%03u.CPT`
- `%c:ACTLIGA\CEURO%03u.CPT`
- `%c:ACTLIGA\RECOP%03u.CPT`
- `%c:ACTLIGA\CUEFA%03u.CPT`
- `%c:ACTLIGA\INTER%03u.CPT`
- `%c:ACTLIGA\SCESP%03u.CPT`
- `%c:ACTLIGA\SCEUR%03u.CPT`

`TACTICS` templates:

- `TACTICS\PROMANAG`
- `TACTICS\MANAGER`
- `%c:TACTICS\%s`
- `%c:TACTICS\TACTIC.%03X`
- `%c:TACTICS\TACTIC.*`

Implication:

- Rewrite must preserve equivalent save-domain split:
  - season state (`ACTLIGA` namespace)
  - manager identity/history (`TACTICS` namespace)
  - tactical presets (`TACTICS\TACTIC.*`).

## 6) ProManager flow (confirmed anchors + behavior contract)

### 6.1 Binary/UI anchors in `MANDOS.DAT`

- `TACTICS\PROMANAG` @ `4526067`
- `TACTICS\MANAGER` @ `4526084`
- `SELECCION DE OFERTAS` @ `4526351`
- `MANAGERS` @ `4526516`, `4526556`, `4526584`, `4526594`, plus awards screens
- `CLAVE DE ACCESO` @ `4526745`, `4526776`
- `OFERTAS PARA ` @ `4527309`

### 6.2 Functional docs contract (`INFOFUT`)

`IF5PROMA.HTM`:

- New manager + password should show starting offers.
- New managers receive weak-team offers (relegation risk profile).

`IF5PROFE.HTM`:

- End-of-season offers improve only if objective was met.

### 6.3 Current observed blocker

- In QA, flow reaches `SELECCION DE OFERTAS`, manager row exists, but offer list is empty.
- So issue is not only navigation/input. It is a logic/data dependency mismatch.
- Input-pool sanity check (`analyze_promanager_offer_inputs.py`) shows:
  - active Spanish slots in `LIGA.DBC` + `SCESPANA.DBC`: `81`
  - active third-tier (`E3G1/E3G2`) slots: `39` (out of `40` mapped)
- Therefore, empty offers are unlikely to be explained only by "no weak teams exist".

## 7) Competition keys and risk areas

### 7.1 Internal keys in `MANDOS.DAT`

Tail-region key tables include:

- `LIGA1`
- `LIGA2`
- `LIGA2B`
- `RECOPA`
- `SCESP`
- `SCEUR`

`LIGA2B` key pair at:

- `5091284`
- `5091292`

Nearby table head includes compact control values:

- `[1, 1, 1, 0, 0, 0, 1, 2, 2, 0, 0, 0, ...]`

### 7.2 Patch hazard discovered

- Replacing short codes like `2OB` can modify non-text loader/code data and crash runtime.
- Replacing internal keys like `LIGA2B` in `MANDOS` can avoid crash but still break behavior.
- Safe policy: keep `MANDOS` internal keys unchanged; apply naming migration in safer text assets.

## 8) Simulator contract surface

`MANDOS.DAT` embeds `PCF_SIMULATOR_COMFILE` and many parameter keys, including:

- team/player attributes:
  - `@velocidad`, `@resistencia`, `@agresividad`, `@calidad`,
  - `@estadoforma`, `@moral`, `@media`,
  - `@portero`, `@entrada`, `@regate`, `@remate`, `@pase`, `@tiro`,
  - `@rol`, `@demarcacion`
- tactical controls:
  - `@tipojuego`, `@tipomarcaje`, `@tipodespejes`, `@tipopresion`, `@faltas`
  - `@porctoque`, `@porccontra`
  - `@marcajedefensas`, `@marcajemedios`
  - `@puntodefensa`, `@puntoataque`, `@area`

Implication:

- Rewrite can define a simulator API using this keyset as canonical compatibility schema.

## 9) Competition DBC footprint (for rewrite data layer)

Inventory highlights:

- `DBDAT/LIGA.DBC`: `11063` words, `3991` valid slot refs, `95` unique IDs.
- `DBDAT/SCESPANA.DBC`: `3504` words, `391` valid refs, `68` unique IDs.
- `DBDAT/UEFA.DBC`: `1450` words, `433` valid refs, `100` unique IDs.
- `DBDAT/RECOPA.DBC`: `1421` words, `431` valid refs, `66` unique IDs.
- `DBDAT/JORN1xx.DBC` and `JORN2xx.DBC` files encode round/jornada structures with slot references.

Implication:

- Competitions are data-driven via DBCs + internal key tables in LE modules.
- Rewrite should preserve this separation:
  - static competition layout files
  - runtime season state files in `ACTLIGA`.

## 10) Current rewrite-readiness level

What is already strong:

- File/module inventory complete.
- PKF and player/team practical schemas established.
- Save-path namespace and pro-manager anchors identified.
- Competition key risk map identified.
- Simulator parameter surface identified.

What is still missing for full deterministic reimplementation:

- Exact offer-generation algorithm (inputs, filters, scoring, fallback rules).
- Full decode of `LIGA2B`/promotion table semantics.
- Full LE fixup-level control-flow map around manager offers screen.
- Exact season progression state transitions (`ACTLIGA` CPT/ACT payload semantics).

## 11) Practical next RE milestones

1. Parse LE fixup tables and recover xrefs from manager/pro-manager strings to code blocks.
2. Instrument/trace pro-manager start in one stable environment and snapshot generated in-memory candidate offer set.
3. Decode `ACTLIGA\LIG2B*.CPT` semantic fields after one generated season.
4. Lock a formal spec:
   - `OfferEngineSpec v1`
   - `SeasonStateSpec v1`
   - `CompetitionSpec v1`
5. Start rewrite with compatibility adapters against extracted spec, not against original binary behavior guesses.

# PCF55 Updater (Transfermarkt 2025/2026)

Toolkit para preparar una actualizacion de `PCF55` usando Transfermarkt como fuente.

## Que hace ahora

- Extrae metadatos de equipos desde `DBDAT/EQUIPOS.PKF` (nombre, estadio, offsets).
- Descarga equipos/plantillas/valores (EUR) desde Transfermarkt para una temporada.
- Deriva atributos de jugador compatibles con motor PCF (VE/RE/AG/CA + tecnicos) desde plantilla+valor.
- Genera mapeo automatico `PCF55 -> Transfermarkt`.
- Genera mapeo completo usando placeholders obsoletos para cubrir todos los equipos nuevos.
- Aplica patch inicial seguro sobre `EQUIPOS.PKF` para nombres de equipo y estadio.
- Corrige `country_code` en `EQUIPOS.PKF` para equipos espanoles (ES1/ES2/E3G1/E3G2), evitando pools vacios en ofertas ProManager.
- Reescribe jugadores en `EQUIPOS.PKF` (nombre corto, nombre completo, atributos y ano de nacimiento derivado de edad).
- Genera reporte de coherencia de motor (ranking de fuerza y simulacion proxy).
- Parchea textos de competicion para migrar `Segunda B` a `Primera RFEF` (2 grupos activos + 2 desactivados).
  - Por compatibilidad, el patch por defecto se aplica a `DBASEDOS.DAT`.
  - `MANDOS.DAT` queda en modo opt-in (`--include-mandos`).
  - Las claves internas de competicion (`2OB`, `2AB`, `LIGA2B`, etc.) se bloquean por defecto en binarios para evitar romper arranque o logica ProManager.

## Que no hace aun

- No reescribe todos los textos biograficos de ficha (trayectoria larga, notas, etc.).
- No convierte todos los campos monetarios historicos del juego a EUR dentro del binario (los valores de referencia si se manejan en EUR en CSV/JSON).

## Uso rapido

Desde `C:\Users\Jose-Firebat\proyectos\pcfutbol\tools\pcf55-updater`:

```powershell
python .\extract_pcf55_teams.py
python .\fetch_transfermarkt.py --competitions ES1 ES2 --season 2025
python .\build_mapping.py --min-score 0.72
python .\build_full_placeholder_mapping.py --min-score 0.72
python .\apply_name_stadium_patch.py --mapping .\out\pcf55_full_mapping.csv --only-accepted --min-score 0
# Solo si quieres tocar tambien el campo "full_name" heuristico (riesgoso):
# python .\apply_name_stadium_patch.py --mapping .\out\pcf55_full_mapping.csv --only-accepted --min-score 0 --allow-heuristic-full-name
python .\apply_country_patch.py --mapping .\out\pcf55_full_mapping.csv --mode spanish_only --in-place
python .\derive_attributes_from_transfermarkt.py --mapping .\out\pcf55_full_mapping.csv --min-score 0 --out-players .\out\pcf55_derived_attributes_full.csv --out-teams .\out\pcf55_derived_team_strength_full.csv
python .\apply_players_patch.py --mapping .\out\pcf55_full_mapping.csv --players-csv .\out\pcf55_derived_attributes_full.csv --only-accepted --min-score 0 --attributes-mode source --in-place --report .\out\player_patch_report_full.json
python .\engine_consistency.py --mapping .\out\pcf55_full_mapping.csv --min-score 0 --out-json .\out\engine_consistency_report_full.json --out-csv .\out\engine_strength_reference_full.csv
python .\patch_competitions_rfef.py --report .\out\competition_patch_report_full.json
# Solo si quieres forzar tambien MANDOS.DAT:
# python .\patch_competitions_rfef.py --include-mandos --report .\out\competition_patch_report_full.json
# Forzar tambien codigos cortos en MANDOS (riesgoso):
# python .\patch_competitions_rfef.py --include-mandos --allow-unsafe-mandos-codes --report .\out\competition_patch_report_full.json
```

### Ejemplo fase global (260 slots)

```powershell
python .\fetch_transfermarkt.py --competitions ES1 ES2 E3G1 E3G2 GB1 IT1 FR1 L1 PO1 NL1 BE1 TR1 RU1 SC1 A1 DK1 PL1 --season 2025 --out .\out\transfermarkt_teams_global.json
python .\build_mapping.py --tm .\out\transfermarkt_teams_global.json --out-csv .\out\pcf55_transfermarkt_mapping_global.csv --out-json .\out\pcf55_transfermarkt_mapping_global.json --min-score 0.72
python .\build_full_placeholder_mapping.py --base-mapping .\out\pcf55_transfermarkt_mapping_global.csv --transfermarkt .\out\transfermarkt_teams_global.json --min-score 0.72 --target-counts ES1=20 ES2=22 E3G1=20 E3G2=20 GB1=20 IT1=20 FR1=18 L1=18 PO1=18 NL1=18 BE1=16 TR1=18 RU1=10 PL1=10 SC1=4 A1=4 DK1=4 --only-targeted --priority-competitions ES1 ES2 E3G1 E3G2 GB1 IT1 FR1 L1 PO1 NL1 BE1 TR1 RU1 PL1 SC1 A1 DK1 --out .\out\pcf55_full_mapping_global.csv
python .\rebalance_competition_slots.py --mapping .\out\pcf55_full_mapping_global.csv --no-chain-slots 19 104 --low-impact-comps A1 DK1 SC1 --out .\out\pcf55_full_mapping_global.csv
python .\apply_name_stadium_patch.py --mapping .\out\pcf55_full_mapping_global.csv --only-accepted --min-score 0 --skip-indices 19 104 108 160 --in-place
# Solo si quieres tocar tambien el campo "full_name" heuristico (riesgoso):
# python .\apply_name_stadium_patch.py --mapping .\out\pcf55_full_mapping_global.csv --only-accepted --min-score 0 --skip-indices 19 104 108 160 --allow-heuristic-full-name --in-place
python .\apply_country_patch.py --mapping .\out\pcf55_full_mapping_global.csv --mode spanish_only --in-place --report .\out\country_patch_report_global.json
python .\derive_attributes_from_transfermarkt.py --transfermarkt .\out\transfermarkt_teams_global.json --mapping .\out\pcf55_full_mapping_global.csv --min-score 0 --out-players .\out\pcf55_derived_attributes_global.csv --out-teams .\out\pcf55_derived_team_strength_global.csv
python .\apply_players_patch.py --mapping .\out\pcf55_full_mapping_global.csv --players-csv .\out\pcf55_derived_attributes_global.csv --only-accepted --min-score 0 --attributes-mode source --in-place --report .\out\player_patch_report_global.json
python .\engine_consistency.py --mapping .\out\pcf55_full_mapping_global.csv --min-score 0 --out-json .\out\engine_consistency_report_global.json --out-csv .\out\engine_strength_reference_global.csv
python .\patch_competitions_rfef.py --report .\out\competition_patch_report_global.json
# Solo si quieres forzar tambien MANDOS.DAT:
# python .\patch_competitions_rfef.py --include-mandos --report .\out\competition_patch_report_global.json
# Forzar tambien codigos cortos en MANDOS (riesgoso):
# python .\patch_competitions_rfef.py --include-mandos --allow-unsafe-mandos-codes --report .\out\competition_patch_report_global.json
python .\qa_global_update.py --out .\out\qa_global_report.json
```

Resultados en `.\out\`:

- `pcf55_teams_extracted.json/csv`
- `transfermarkt_teams.json`
- `pcf55_transfermarkt_mapping.json/csv`
- `pcf55_full_mapping.csv`
- `pcf55_derived_attributes.csv`
- `pcf55_derived_team_strength.csv`
- `player_patch_report_full.json`
- `engine_consistency_report.json`
- `engine_strength_reference.csv`

El patch genera:

- Backup del PKF original: `EQUIPOS.PKF.YYYYMMDD_HHMMSS.bak`
- PKF parcheado: `EQUIPOS.patched.PKF` (o in-place si usas `--in-place`)

## Navegacion DOSBox por ADB (robusta)

Para evitar prueba/error manual en Magic DOSBox, usa:

```powershell
python .\dosbox_nav.py configure --mouse-mode absolute
python .\dosbox_nav.py screenshot --name qa_state.png
python .\dosbox_nav.py act double_tap 760 640
python .\dosbox_nav.py act long_press 760 640 --duration-ms 700
```

Click por nombre de boton (mapa):

```powershell
python .\dosbox_nav.py click-btn --screen main_menu --button liga_manager
python .\dosbox_nav.py click-btn --screen ofertas_manager --button volver
```

Mapa editable en:

- `tools/pcf55-updater/dosbox_button_map.json`

Soporta secuencias JSON:

```powershell
python .\dosbox_nav.py run-seq --file .\out\dosbox_seq.json
```

Formato minimo de `dosbox_seq.json`:

```json
[
  { "action": "double_tap", "x": 760, "y": 640 },
  { "action": "sleep", "seconds": 1.0 },
  { "action": "screenshot", "name": "after_step.png" }
]
```

## Temporada

En Transfermarkt, `--season 2025` equivale a temporada `2025/2026`.

## Ingenieria inversa (base para reescritura)

Generar inventario completo de binarios/datos del juego:

```powershell
python .\reverse_full_game.py
```

Salida:

- `out/reverse_full_game.json`
- `out/reverse_full_game.md`

Extraer contrato machine-readable para reescritura (offsets, rutas de guardado, tokens simulador):

```powershell
python .\extract_rewrite_contract.py
```

Salida:

- `out/rewrite_contract.json`

Analisis LE y diff por secciones/paginas:

```powershell
python .\reverse_le.py --file ..\..\PCF55\FUTBOL5\FUTBOL5\MANDOS.DAT --find "SELECCION DE OFERTAS" "OFERTAS PARA" "LIGA2B" --out .\out\re_le_mandos_current.json
```

Nota: en estos LE concretos, `fixup_page_table_offset` y `fixup_record_table_offset` aparecen a `0` en cabecera, por lo que el parseo clasico de fixups no aporta xrefs directos.

Escaneo de xrefs candidatos por literales VA (ayuda para validar si hay referencias directas a anchors):

```powershell
python .\find_le_xrefs.py --out .\out\re_le_xrefs_mandos_20260227.json
```

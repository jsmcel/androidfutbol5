# ProManager QA Snapshot (2026-02-27)

## Scope

- Environment: Windows + DOSBox 0.74-3 automation.
- Build under test: `PCF55/FUTBOL5/FUTBOL5` (patched 2025/26 dataset).
- Control build: `legacy/PCF55/FUTBOL5/FUTBOL5`.
- Mode: ProManager (`SELECCION DE OFERTAS`), result-mode flow only.

## What was validated

- Navigation from main menu to `SELECCION DE OFERTAS` is now reproducible with flow:
  - `main_to_offer_coords_experiment`
  - Evidence run dirs:
    - `tools/pcf55-updater/out/dosbox_framework_runs/20260227_123309_main_to_offer_coords_experiment`
    - `tools/pcf55-updater/out/dosbox_framework_runs/20260227_124135_main_to_offer_coords_experiment` (legacy scenario)
- Keyboard input in offer screen is accepted (manager field updates), verified by probe:
  - `offer_key_a_probe`

## Main blocker reproduced

- Both patched and legacy builds can reach `SELECCION DE OFERTAS`, but flows remain in `PASSWORD` state without transitioning to loaded offer list.
- OCR traces show stable pattern:
  - start: `MANAGERS`
  - after manager name + enter: `PASSWORD` + typed manager
  - after password attempts and `Cargar Liga`: still `PASSWORD`
- Evidence:
  - Patched:
    - `tools/pcf55-updater/out/dosbox_framework_runs/20260227_123340_offer_login_prueba_clean_probe`
    - OCR report: `.../ocr_report.json`
  - Legacy:
    - `tools/pcf55-updater/out/dosbox_framework_runs/20260227_124204_offer_login_prueba_clean_probe`

## Conclusion

- This behavior is **not unique to the 2025/26 patch** (same lock pattern appears in legacy with the same automated input sequence).
- Therefore, current blocker is at least partly a **flow/interaction contract issue** (or hidden state dependency), not yet proven as a patch-induced data regression.

## Next RE target

- Reverse the exact transition contract from `SELECCION DE OFERTAS` + `PASSWORD` to loaded manager offers:
  - xrefs around anchors in `MANDOS.DAT`:
    - `SELECCION DE OFERTAS`
    - `MANAGERS`
    - `CLAVE DE ACCESO`
    - `OFERTAS PARA`
    - `TACTICS\\PROMANAG`
  - Identify persistence read/write and accepted credential path before offer list generation.


# QA Report — 2026-02-28

## Resultado de compilación
- Tests unitarios: **PASS (30/30)**
  - `:match-sim:test` = **PASS (18/18)**
  - `:competition-engine:test` = **PASS (12/12)** (debug+release)
- `assembleDebug`: **PASS**
- `installDebug`: **PASS** (instalado en dispositivo `Mi A2 - 11`)
- Errores de compilación encontrados y corregidos:
  - Referencia huérfana Quiniela en DI (`db.quinielaDao()` inexistente).
  - Error suspend function reference en `CompetitionRepository` (`forEach(::setupLeague)`).
  - Configuración faltante `android.useAndroidX=true`.
  - Tema Android inválido (`Theme.Material.NoTitleBar.Fullscreen`).
  - Recursos launcher inexistentes (`@mipmap/ic_launcher`).
  - Incompatibilidad Compose Compiler/Kotlin (`1.5.14` vs Kotlin `1.9.25`).
  - Imports faltantes en UI (`Stroke`, `launch`).
  - Crash de seed por parseo JSON de Transfermarkt (Double -> Long).

## Errores en runtime (logcat)
- **Corregido**: crash al arrancar seed:
  - `JsonDataException: Expected a long but was 4099999.9999999995 at path $[24].tm_team_market_value_eur`
- **Persistente (warning, no crash)**: filas CSV con edad vacía:
  - `NumberFormatException: For input string: ""` en `SeedLoader.parseCsvPlayerLine`.
  - El seed termina igualmente (`Seed completado: 456 equipos, 10880 jugadores`).
- Logs antiguos de `UiAutomationService ... already registered` aparecen en `AndroidRuntime`, no atribuibles a la app.

## Flujos probados
| Flujo | Resultado | Nota |
|---|---|---|
| Menú principal carga | ✅ | `ui_dump.xml` muestra `PC FÚTBOL 5`, botones `LIGA / MANAGER` y `PROMANAGER`. |
| Liga/Manager abre | ✅ | `ui_dump_liga.xml` con menú completo (`CLASIFICACION`, `JORNADA`, `MI PLANTILLA`, etc.). |
| Clasificación muestra datos | ✅ | `ui_clasificacion.xml` contiene tabla y equipos. |
| Jornada simula partido | ✅ | `ui_jornada_sim.xml`: marcador pasó a `1-0` y eventos (`GOL`, `Amarilla`). |
| Plantilla muestra jugadores | ❌ | `ui_plantilla.xml` muestra `JUGADORES (0)` y `Sin jugadores en esta posición.` |
| Mercado de fichajes | ✅ | Pantalla carga (`MERCADO`), sin agentes libres en este estado. |
| Copa del Rey | ✅ | Pantalla carga y permite `INICIAR COPA DEL REY`. |
| Selección Nacional | ✅ | Pantalla carga con convocados y acciones (`TACTICA`, `SIMULAR PARTIDO`). |
| ProManager crea manager | ✅ | Creación de manager + generación de ofertas + aceptación de oferta completadas. |
| Fin de temporada | ❌ | No cubierto en este ciclo (requiere simulación larga multi-jornada). |

## Screenshots tomados
- `qa_screenshots/qa_01_menu.png`
- `qa_screenshots/qa_02_liga.png`
- `qa_screenshots/qa_03_clasificacion.png`
- `qa_screenshots/qa_04_jornada.png`
- `qa_screenshots/qa_04b_jornada_sim.png`
- `qa_screenshots/qa_05_plantilla.png`
- `qa_screenshots/qa_06_mercado.png`
- `qa_screenshots/qa_07_copa.png`
- `qa_screenshots/qa_08_seleccion.png`
- `qa_screenshots/qa_09_menu_back.png`
- `qa_screenshots/qa_10_promanager.png`
- `qa_screenshots/qa_11_promanager_action.png`
- `qa_screenshots/qa_12_promanager_created.png`
- `qa_screenshots/qa_13_promanager_offer_selected.png`
- `qa_screenshots/qa_14_promanager_offer_accepted.png`

## Bugs encontrados
| # | Descripción | Severidad | Archivo sospechoso |
|---|---|---|---|
| 1 | `MI PLANTILLA` aparece vacía (`JUGADORES (0)`) tras flujo manager. | ALTO | `ui/src/main/kotlin/com/pcfutbol/ui/screens/TeamSquadScreen.kt`, VM de plantilla/manager team state |
| 2 | Parseo de CSV con `age` vacío genera warnings y pérdida de filas en seed (`NumberFormatException`). | MEDIO | `core/data/src/main/kotlin/com/pcfutbol/core/data/seed/SeedLoader.kt` |
| 3 | `connectedAndroidTest` global es inestable/lento al recorrer módulos (0 tests), se interrumpió en ejecución completa. | BAJO | Configuración de tareas instrumentadas por módulo |

## Fixes aplicados durante el QA
- `core/data/src/main/kotlin/com/pcfutbol/core/data/di/DataModule.kt`
  - Eliminada provisión huérfana `provideQuinielaDao`.
- `competition-engine/src/main/kotlin/com/pcfutbol/competition/CompetitionRepository.kt`
  - Reemplazado `forEach(::setupLeague)` por loop suspend seguro.
- `app/src/main/res/values/themes.xml`
  - Tema base cambiado a `android:Theme.Material.Light.NoActionBar`.
- `app/src/main/AndroidManifest.xml`
  - `icon` y `roundIcon` ajustados a `@android:drawable/sym_def_app_icon`.
- `gradle.properties`
  - Añadido `android.useAndroidX=true`.
  - Añadido `android.suppressUnsupportedCompileSdk=35`.
  - Aumentada memoria Gradle (`org.gradle.jvmargs=-Xmx2048m ...`).
- `app/build.gradle.kts`
  - `kotlinCompilerExtensionVersion` actualizado a `1.5.15`.
  - Añadidas dependencias instrumentadas (`androidx.test:runner`, `rules`, `ext:junit`).
- `ui/build.gradle.kts`
  - `kotlinCompilerExtensionVersion` actualizado a `1.5.15`.
- `ui/src/main/kotlin/com/pcfutbol/ui/screens/MainMenuScreen.kt`
  - Import de `Stroke`.
- `ui/src/main/kotlin/com/pcfutbol/ui/screens/Stubs.kt`
  - Import de `kotlinx.coroutines.launch`.
- `core/data/src/main/kotlin/com/pcfutbol/core/data/seed/SeedModels.kt`
  - Valores de mercado Transfermarkt cambiados a `Double`.
- `core/data/src/main/kotlin/com/pcfutbol/core/data/seed/SeedLoader.kt`
  - Conversión de `tmMarketValueEur` para presupuesto/prestigio sin crash.
- `local.properties`
  - Añadido `sdk.dir` para entorno local.

## Recomendaciones
1. Corregir parsing robusto en `SeedLoader` para campos CSV vacíos (edad, etc.) con defaults/control explícito.
2. Revisar pipeline de datos/estado que alimenta `MI PLANTILLA` (managerTeamId, filtro por equipo/posición).
3. Definir estrategia de instrumentación por módulos (evitar `connectedAndroidTest` global cuando no hay suites, usar task por módulo o solo `:app:connectedDebugAndroidTest`).
4. Mantener check de huérfanos en CI para evitar regresiones (`Quiniela/Proquinielas/onProquinielas`).

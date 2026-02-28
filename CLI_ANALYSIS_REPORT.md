# CLI_ANALYSIS_REPORT

## 0) Resumen Ejecutivo

Análisis exhaustivo de `cli/pcfutbol_cli.py` (1810 líneas) con comparación contra Android (`android/`) y contra la copia alternativa `android/cli/pcfutbol_cli.py`.

Hallazgos críticos:

1. **Bloqueo de arranque en el CLI raíz**: `cli/pcfutbol_cli.py` resuelve mal `ASSETS_DIR` y falla al abrir CSV.
2. **Deriva entre CLIs**: `cli/pcfutbol_cli.py` y `android/cli/pcfutbol_cli.py` ya no son equivalentes (2205 vs 1810 líneas; +14 funciones en Android CLI).
3. **Sin tests automáticos del CLI Python**: 0 cobertura unitaria/integración directa para funciones del CLI.
4. **Persistencia frágil de mercado**: transferencias por `player_name` (no por ID) con colisiones reales de nombres.
5. **Modo Entrenador no robusto para piping**: funciona con input completo, pero lanza `EOFError` cuando faltan entradas.

Estado de bugs “conocidos” del enunciado:

- Calibración de fuerza plana (47-52): **sigue presente**.
- Handler de táctica en season loop: **corregido** en `cli/pcfutbol_cli.py`.
- Validación mismo equipo local/visitante: **corregido** en `cli/pcfutbol_cli.py`.

---

## 1) Alcance, Evidencia y Ejecuciones

### 1.1 Archivos revisados

- `cli/pcfutbol_cli.py` (objetivo principal).
- `android/cli/pcfutbol_cli.py` (comparación de divergencia).
- `docs/pcf55_rewrite_architecture.md` (diseño objetivo).
- Módulos Android relevantes:
  - `android/competition-engine/...`
  - `android/match-sim/...`
  - `android/manager-economy/...`
  - `android/promanager/...`
  - `android/ui/viewmodels/...`

### 1.2 Ejecuciones CLI pedidas y resultados

Comandos equivalentes ejecutados:

- `python cli/pcfutbol_cli.py` con input `0`.
- `python cli/pcfutbol_cli.py` con input de simulación.

Resultado real:

- `cli/pcfutbol_cli.py` falla en carga de assets:
  - Error: `No se encuentra el CSV: C:\Users\Jose-Firebat\proyectos\android\core\data\src\main\assets\pcf55_players_2526.csv`
  - Causa: `ASSETS_DIR` en línea 33 usa `parent.parent.parent` (ruta incorrecta para el archivo ubicado en `/cli`).

Ejecuciones adicionales para validar comportamiento funcional:

- `python android/cli/pcfutbol_cli.py` con menú principal y pruebas de temporada/partido/pro-manager.
- Confirmado:
  - menú funcional;
  - validación local != visitante activa;
  - temporada simulable;
  - persistencia de carrera y carga posterior.

### 1.3 Diferencia `cli/` vs `android/cli/`

- `cli/pcfutbol_cli.py`: 1810 líneas, 50 funciones top-level.
- `android/cli/pcfutbol_cli.py`: 2205 líneas, 64 funciones top-level.
- Funciones extra en Android CLI (no presentes en raíz):
  - `_save_career_raw`, `_clean_runtime_keys`, `_default_career_payload`, `_normalize_career_payload`,
  - `_get_active_manager_data`, `_is_multiplayer_context`, `_advance_multiplayer_turn`,
  - `_season_year`, `_player_snapshot_from_team_player`, `_build_players_snapshot`,
  - `_apply_season_development`, `_new_manager_state`, `_remove_active_manager`,
  - `menu_multiplayer`.

Conclusión: el CLI de raíz está **desfasado** frente al de `android/cli`.

---

## 2) Inventario Completo de Funciones (`cli/pcfutbol_cli.py`)

Notas generales de cobertura:

- **Tests automáticos directos para este archivo**: **No** (ninguno detectado).
- “Cobertura indirecta” solo existe en módulos Kotlin Android equivalentes (match-sim, competition, promanager), no sobre el CLI Python.

### 2.1 Métodos de modelos

| Función | Línea | Propósito | Entrada / Salida | Bugs conocidos/sospechosos | Cobertura test |
|---|---:|---|---|---|---|
| `Player.overall` | 62 | Exponer `me` como overall | `self -> int` | Ninguno | No |
| `Player.is_goalkeeper` | 66 | Detectar portero por string de posición | `self -> bool` | Dependencia de literal exacto `"Goalkeeper"` | No |
| `Player.attack_strength` | 69 | Fuerza ofensiva individual | `self -> float` | Sin calibración por contexto/rol | No |
| `Player.defense_strength` | 73 | Fuerza defensiva individual | `self -> float` | Ídem | No |
| `Player.gk_strength` | 77 | Fuerza específica de portero | `self -> float` | Ídem | No |
| `Team.competition` | 89 | Mapear `ES1/ES2` a `LIGA1/LIGA2` | `self -> str` | Valor por defecto `LIGA2` para cualquier no-`ES1` | No |
| `Team.strength` | 92 | Calcular fuerza de equipo | `self -> float` | Rango de salida demasiado estrecho (calibración plana) | No |
| `Standing.points` | 118 | Puntos de clasificación | `self -> int` | Ninguno | No |
| `Standing.gd` | 122 | Diferencia de goles | `self -> int` | Ninguno | No |
| `Standing.sort_key` | 125 | Orden de tabla | `self -> tuple` | Falta tie-break avanzado (head-to-head, etc.) | No |
| `MatchResult.__str__` | 137 | Render de resultado textual | `self -> str` | Ninguno | No |

### 2.2 Carga de datos y simulación base

| Función | Línea | Propósito | Entrada / Salida | Bugs conocidos/sospechosos | Cobertura test |
|---|---:|---|---|---|---|
| `load_teams` | 147 | Cargar equipos/jugadores ES1/ES2 desde CSV | `-> dict[str, Team]` | Depende de `ASSETS_DIR` global roto en CLI raíz; parseo incompleto ante filas malformadas | No |
| `_poisson_goals` | 204 | Muestreo Poisson (Knuth) | `(lam, rng) -> int` | Sin cota superior explícita de goles | No |
| `_tactic_adj` | 216 | Ajuste de fuerza por táctica | `(dict|None) -> float` | Fórmula simplificada; no valida schema | No |
| `simulate_match` | 240 | Simulación determinista por seed | `(home, away, seed, ht, at) -> (hg, ag)` | No VAR ni eventos; ventaja local distinta a Android match-sim | No |
| `generate_fixtures` | 266 | Round-robin doble vuelta | `(teams) -> list[(home,away)]` | Variable `round_away` no usada | No |
| `simulate_season` | 304 | Simular liga completa | `(teams, competition, seed_base, silent) -> (standings, results)` | Type hint incorrecto (`list[Standing]`), params `competition/silent` no usados | No |

### 2.3 Helpers de impresión y entrada

| Función | Línea | Propósito | Entrada / Salida | Bugs conocidos/sospechosos | Cobertura test |
|---|---:|---|---|---|---|
| `_c` | 358 | Aplicar color ANSI | `(color,text)->str` | Ninguno | No |
| `print_standings` | 362 | Mostrar clasificación | `(standings,title,...) -> None` | Sin tie-break avanzado | No |
| `print_matchday` | 389 | Mostrar resultados por jornada | `(results,matchday,team_filter)->None` | Ninguno | No |
| `print_squad` | 411 | Mostrar plantilla | `(team)->None` | Ninguno | No |
| `input_int` | 429 | Input entero validado | `(prompt,min,max)->int` | `sys.exit` en EOF/interrupt (difícil de testear por unidad) | No |
| `input_str` | 442 | Input string | `(prompt)->str` | `sys.exit` en EOF/interrupt | No |
| `select_team` | 449 | Selección numerada de equipo | `(teams,prompt)->Team` | Ninguno | No |

### 2.4 Menús principales

| Función | Línea | Propósito | Entrada / Salida | Bugs conocidos/sospechosos | Cobertura test |
|---|---:|---|---|---|---|
| `menu_simulate_season` | 456 | Simular LIGA1+LIGA2 y explorar resultados | `(liga1,liga2)->None` | Uso de seed aleatoria no reproducible desde UI | No |
| `menu_view_squad` | 519 | Ver plantilla | `(liga1,liga2)->None` | Ninguno | No |
| `menu_quick_match` | 526 | Partido único | `(liga1,liga2)->None` | Bug previo local==visitante ya corregido | No |
| `menu_top_players` | 561 | Ranking de jugadores | `(liga1,liga2)->None` | Filtros de posición por substring (frágiles) | No |
| `menu_team_strength` | 601 | Ranking de fortaleza de equipos | `(liga1,liga2)->None` | Exposición de calibración plana | No |

### 2.5 Persistencia y ProManager

| Función | Línea | Propósito | Entrada / Salida | Bugs conocidos/sospechosos | Cobertura test |
|---|---:|---|---|---|---|
| `_prestige_label` | 628 | Render de estrellas de prestigio | `(p)->str` | Ninguno | No |
| `_pause` | 632 | Pausa en TTY | `()->None` | Salta en stdin no TTY | No |
| `_save_career` | 639 | Guardar save JSON | `(data)->None` | Sin versionado/schema | No |
| `_load_career` | 644 | Cargar save JSON | `()->dict|None` | Captura `Exception` genérica y silencia corrupción | No |
| `_standings_from_results` | 656 | Recalcular tabla desde resultados guardados | `(results,teams)->list[Standing]` | Duplicados de resultados inflan PJ/PTS | No |
| `_generate_offers` | 674 | Ofertas por prestigio | `(prestige,liga1,liga2)->list[Team]` | `random.shuffle` no determinista | No |
| `_assign_objective` | 688 | Objetivo de junta por ranking de fuerza | `(team,liga1,liga2)->str` | Basado en fortaleza plana | No |
| `_check_objective` | 704 | Evaluar cumplimiento de objetivo | `(objective,position,total,comp)->bool` | Reglas simplificadas | No |
| `_pm_header` | 717 | Cabecera ProManager | `(data,matchday,total)->None` | Ninguno | No |
| `_mini_standings` | 727 | Clasificación compacta | `(standings,mgr_slot,n_rel)->None` | Ninguno | No |
| `_show_md_results` | 755 | Resultados jornada con highlight manager | `(md_res,tbs,mgr_slot)->None` | Ninguno | No |
| `_news_item` | 773 | Texto de noticia post-partido | `(r,tbs,mgr_slot,mgr_name,pos,total,n_rel)->str` | Plantillas aleatorias no deterministas | No |
| `_season_end_screen` | 795 | Cierre temporada + prestigio/histórico | `(data,standings,mgr_slot,mgr_team,is_l1,n_rel)->None` | Sin evolución/retiro en CLI raíz | No |
| `_tactic_summary` | 872 | Resumen textual de táctica | `(t)->str` | Ninguno | No |
| `_tactic_menu` | 880 | Edición interactiva de táctica | `(data)->None` | Cambios guardados sin validación de compatibilidad histórica | No |

### 2.6 Modo Entrenador

| Función | Línea | Propósito | Entrada / Salida | Bugs conocidos/sospechosos | Cobertura test |
|---|---:|---|---|---|---|
| `_squad_rows` | 951 | Particionar plantilla por líneas | `(players)->(gk,df,md,at)` | Heurística textual de posiciones | No |
| `_draw_frame` | 965 | Render animado ASCII del campo | `(home,away,is_home_mgr,hg,ag,minute,ball_zone,message)->None` | Limpieza terminal por `os.system`; pesada para tests | No |
| `_ball_drift` | 1044 | Movimiento de balón con sesgo de fuerza | `(zone,rng,our,opp)->int` | Modelo muy simplificado | No |
| `_entrenador_substitution` | 1056 | Flujo interactivo de sustitución | `(mgr_team,subs_made)->bool` | Sustituciones cosméticas (no cambian fuerza ni once real) | No |
| `_match_entrenador` | 1080 | Partido minuto a minuto (VAR, subs, lesiones) | `(home,away,seed,mgr_slot,data)->tuple` | `input()` sin manejo de EOF; cambios tácticos no recalculan goles preprogramados | No |

### 2.7 Mercado y loop de temporada

| Función | Línea | Propósito | Entrada / Salida | Bugs conocidos/sospechosos | Cobertura test |
|---|---:|---|---|---|---|
| `_init_budget` | 1350 | Presupuesto inicial por ranking/liga | `(team,liga1,liga2)->int` | Escala fija/manual | No |
| `_is_window_open` | 1362 | Ventana de mercado abierta/cerrada | `(matchday,total)->bool` | Ninguno | No |
| `_apply_squad_changes` | 1368 | Reaplicar fichajes/ventas del save | `(mgr_team,all_slots,data)->None` | Usa `player_name` (colisiones); no IDs únicos | No |
| `_market_buy` | 1386 | Comprar jugador | `(data,mgr_team,all_slots,liga1,liga2)->None` | Persistencia por nombre; riesgo colisiones y restauración errónea | No |
| `_market_sell` | 1448 | Vender jugador | `(data,mgr_team)->None` | Permite vaciar plantilla sin restricciones | No |
| `_market_menu` | 1476 | Menú mercado | `(data,mgr_team,all_slots,liga1,liga2)->None` | Dependencia total de funciones anteriores | No |
| `_season_loop` | 1511 | Loop principal de temporada ProManager | `(data,liga1,liga2)->None` | Si save manipulado puede duplicar jornadas/resultados | No |
| `_next_season_str` | 1648 | Cálculo string temporada siguiente | `(season)->str` | Fallback fijo `2026-27` | No |
| `_setup_season` | 1656 | Inicializar nueva temporada de manager | `(data,team,liga1,liga2,season)->None` | Seed aleatoria no reproducible desde UI | No |
| `_show_offers` | 1676 | Mostrar y seleccionar ofertas | `(data,liga1,liga2)->Team?` | Ofertas no deterministas | No |
| `menu_promanager` | 1695 | Entry ProManager (crear/continuar/postseason) | `(liga1,liga2)->None` | Save único; sin slots múltiples en CLI raíz | No |
| `main_menu` | 1757 | Menú principal de la app CLI | `()->None` | Funciona, pero CLI raíz falla antes por rutas de assets | No |

---

## 3) Cobertura de Funcionalidades CLI vs Android

| Funcionalidad | CLI (`cli/pcfutbol_cli.py`) | Android (`android/`) | Alineado |
|---|---|---|---|
| Liga completa (38 jornadas) | **Sí** (`simulate_season`, `generate_fixtures`, `_season_loop`) | **Sí** (`FixtureGenerator`, `CompetitionRepository.setupLeague/advanceMatchday`) | **Parcial** |
| Copa del Rey | **No** | **Sí** (`CopaRepository`) | **No** |
| ProManager | **Sí** (single-save/single-manager) | **Sí** (`ProManagerRepository`, `OfferPoolGenerator`) | **Parcial** |
| Mercado fichajes | **Sí** (compra/venta básica) | **Parcial** (`TransferMarketRepository`, `generateAiOffers` con TODO) | **Parcial** |
| Táctica | **Sí** (menu + ajuste en `simulate_match`) | **Parcial** (UI/DAO de táctica; no integrada claramente en `buildTeamInput`) | **Parcial** |
| Evolución jugadores | **No** (CLI raíz) | **Parcial/En curso** (`EndOfSeasonUseCase` + `PlayerDevelopmentEngine` en working tree) | **No** |
| Multi-jugador | **No** (CLI raíz) | **No** (sin multiplayer competitivo real) | **Sí (ambos faltante)** |
| Selección nacional | **No** | **Sí** (`NationalTeamViewModel`, `NationalSquadDao`) | **No** |
| Modo Entrenador (minuto a minuto) | **Sí** (`_match_entrenador`) | **No** (simulación de resultado/eventos, sin control minuto-a-minuto interactivo) | **No** |
| Fin de temporada | **Sí** (`_season_end_screen`) | **Sí** (`EndOfSeasonUseCase`) | **Parcial** |

Notas de alineación:

- Android está más avanzado en competición/copa/nacional.
- CLI está más avanzado en experiencia interactiva “modo entrenador”.
- El “eg0” para agentes IA está fragmentado: `cli/` (estable pero limitado) vs `android/cli/` (más features, distinto contrato).

---

## 4) Análisis del Save System (`~/.pcfutbol_career.json`)

### 4.1 Estructura observada

Ruta: `CAREER_SAVE = Path.home() / ".pcfutbol_career.json"`.

Ejemplo real (sesión de prueba):

```json
{
  "manager": {"name":"Codex","prestige":1,"total_seasons":0,"history":[]},
  "phase":"SEASON",
  "season":"2025-26",
  "team_slot":133,
  "team_name":"CD Mirandés",
  "competition":"ES2",
  "objective":"EVITAR EL DESCENSO",
  "current_matchday":2,
  "season_seed":3911973729,
  "budget":2000000,
  "bought":[],
  "sold":[],
  "results":[{"md":1,"h":13,"a":22,"hg":0,"ag":0}, "..."],
  "news":["Inicio de temporada...", "J1: ..."],
  "tactic":{"tipoJuego":2,"tipoMarcaje":1,"tipoPresion":2,"tipoDespejes":1,"faltas":2,"porcToque":50,"porcContra":30,"perdidaTiempo":0}
}
```

### 4.2 Qué persiste vs qué se pierde

Persistido:

- Identidad manager, prestigio, historial.
- Equipo actual, competición, objetivo.
- Jornada actual, `season_seed`.
- Presupuesto.
- Transacciones (`bought`/`sold`) y resultados de jornadas (`results`).
- Noticias y táctica.

No persistido (se reconstruye o se pierde):

- Objetos `Team`/`Player` completos en memoria.
- Clasificación detallada (se recalcula desde `results`).
- Fixtures explícitos (se regeneran determinísticamente por seed + slot order).
- Estados finos por jugador (lesión/sanción/forma/moral) en CLI raíz.
- Cualquier metadata de schema/versionado.

### 4.3 Carga/inicialización de equipos y jugadores

1. `load_teams()` recarga CSV en cada arranque (solo ES1/ES2).
2. En ProManager, `_apply_squad_changes()` reaplica compras/ventas del save.
3. Clasificación se deriva de `results` vía `_standings_from_results()`.

### 4.4 ¿Soporta múltiples partidas/managers?

En `cli/pcfutbol_cli.py` (raíz): **No**.

- Un único archivo global.
- Un único bloque `manager`.
- No hay “slots” de carrera.

Comparativa:

- `android/cli/pcfutbol_cli.py` sí añade estructura multi-manager y menú de turnos (`menu_multiplayer`), pero esa lógica no está en el CLI raíz.

---

## 5) Bugs, Edge Cases e Inconsistencias

### 5.1 Estado de bugs conocidos del enunciado

| Bug | Estado |
|---|---|
| Calibración de fuerza plana (47-52) | **Activo** |
| Handler táctica en season loop | **Corregido** |
| Partido local vs visitante mismo equipo | **Corregido** |

### 5.2 Nuevos bugs/hallazgos

| # | Severidad | Hallazgo | Evidencia |
|---|---|---|---|
| 1 | **CRÍTICO** | `cli/pcfutbol_cli.py` no arranca por ruta de assets incorrecta | `ASSETS_DIR` en línea 33 + ejecución real fallida |
| 2 | **ALTO** | Deriva funcional entre `cli/` y `android/cli/` | 1810 vs 2205 líneas; +14 funciones solo en Android CLI |
| 3 | **ALTO** | 0 tests automáticos del CLI Python | No hay referencias en suite de tests |
| 4 | **ALTO** | Mercado persiste por nombre de jugador, no ID | `_apply_squad_changes`, `_market_buy`, `_market_sell`; 8 nombres duplicados en dataset |
| 5 | **MEDIO** | `_match_entrenador` rompe con `EOFError` si faltan inputs en modo no interactivo | Traza real al llegar al descanso |
| 6 | **MEDIO** | Cambiar táctica durante `_match_entrenador` no recalcula eventos/goles ya predefinidos | Eventos se programan al inicio con `base_hg/base_ag` |
| 7 | **MEDIO** | Sustituciones en modo entrenador son cosméticas (no alteran simulación) | `_entrenador_substitution` no modifica once/fuerza |
| 8 | **MEDIO** | `_load_career` silencia corrupción de save y retorna `None` | `except Exception: return None` |
| 9 | **MEDIO** | Save sin versionado/schema: migraciones difíciles y alto riesgo de incompatibilidad | `_save_career` JSON plano |
| 10 | **BAJO** | `simulate_season` retorna tupla, annotation indica lista | definición y `return sorted_standings, results` |
| 11 | **BAJO** | Parámetros no usados (`competition`, `silent`) y constante no usada (`TEAMS_JSON`) | deuda técnica |
| 12 | **BAJO** | Documentación/comandos desalineados (`README` sugiere CLI raíz que falla) | ejecución real y docs |

### 5.3 Validaciones pedidas (división por cero, overflow, input)

- División por cero: mitigada en puntos críticos con `max(..., 1.0/0.01)`.
- Overflow: no hallado (Python int/float en rangos razonables).
- Inputs:
  - Menús principales: validados por `input_int`.
  - Modo entrenador: múltiples `input()` directos sin guardas EOF.

---

## 6) Arquitectura del Simulador (Poisson) y Comparación con Android `match-sim`

### 6.1 Cómo funciona el simulador CLI

Pipeline:

1. `Team.strength()`:
   - Top-11 por `ME`.
   - Combina `gk_strength` (25%), `attack_strength` (40%), `defense_strength` (35%).
2. Ajuste táctico `_tactic_adj()` (tipo juego, presión, marcaje, faltas, contra, despejes, pérdida de tiempo).
3. `simulate_match()`:
   - `home_str = (strength + tactic_adj) * 1.05`
   - `away_str = strength + tactic_adj`
   - `lambda_home = clamp(2.8 * home_str / total, 0.3, 4.5)`
   - `lambda_away = clamp(2.8 * away_str / total, 0.3, 4.5)`
   - Goles por Poisson Knuth.

### 6.2 Calibración actual

Medición en datos reales 25/26:

- LIGA1: min **49.24**, max **52.16**, stdev **0.77**.
- LIGA2: min **47.24**, max **48.89**, stdev **0.41**.

Efecto:

- Diferencias entre equipos top y medios muy pequeñas.
- En 300 temporadas simuladas, campeones muy dispersos (Girona/Elche/Osasuna/Getafe etc. compiten de tú a tú con Madrid/Barça).

### 6.3 Reproducibilidad

- `simulate_match`: **sí**, determinista por seed (verificado).
- `simulate_season`: **sí** con `seed_base` fijo y mismo orden de equipos.
- Desde UI: no reproducible por defecto (`os.urandom`, `random.randint` para semillas).

### 6.4 Comparación con Android `match-sim`

| Aspecto | CLI Python | Android `match-sim` |
|---|---|---|
| Modelo base | Poisson simple | Poisson + disciplina + lesiones + VAR + added time |
| Ventaja local | +5% en `home_str` | +12% en `strengthToLambda` |
| Táctica | ajuste estático simple | `TacticParams` en `StrengthCalculator` |
| Estado jugador | no usa forma/moral en core base | sí usa forma/moral |
| Eventos | solo en modo entrenador | `MatchEvent` completo en simulación normal |
| Tests | no | sí (MatchSimulatorTest, StrengthCalculatorTest) |

Conclusión: el simulador Android es más completo y testeado; el CLI raíz es más simple e inestable para QA automatizada.

---

## 7) Modo Entrenador (`_match_entrenador`) — Análisis Completo

### 7.1 Flujo de 90 minutos

1. Inicializa seed y resultado base Poisson (`base_hg/base_ag`).
2. Genera agenda de eventos (goles, amarillas, lesión).
3. 1ª parte 1..45 + añadido 1ª parte.
4. Descanso con menú (`T`, `S`, `0`) y ventana gratis.
5. 2ª parte 46..90 + añadido 2ª parte.
6. Cierre FT y retorno de marcador.

### 7.2 Sistema VAR

- Revisión por gol: 18%.
- Anulación tras revisión: 38%.
- Si anulado: revierte marcador y muestra motivo.

### 7.3 Sustituciones

- Límite de cambios: 5.
- Ventanas en juego: 3 (`wins`).
- Ventana de descanso gratis (no consume `wins`).
- Cambio de concusión adicional (evento lesión cabeza) con probabilidad 30% de evento lesión.

### 7.4 Lesiones

- Evento `injury` se inserta con prob. 30% por partido (en este modo).
- Si afecta al manager y hay cupo de concusión, permite cambio extra.

### 7.5 ¿Testeable con stdin piped?

Respuesta corta: **parcialmente sí, pero frágil**.

- Sí:
  - Puede ejecutarse sin TTY.
  - Con secuencia completa de inputs predefinida, avanza.
- No robusto:
  - `input()` directos sin manejo EOF: si faltan entradas, termina en `EOFError`.
  - Cantidad de prompts es dinámica (eventos/pausas), difícil de scriptar por piping simple.
  - `time.sleep` ralentiza pruebas (12-20s o más por partido).

Recomendación técnica: modo “headless/test” con:

- inyección de proveedor de input,
- `sleep` parametrizable (0 en tests),
- y salida desacoplada del render terminal.

---

## 8) Recomendaciones Prioritarias (Top 10, impacto/esfuerzo)

| Prioridad | Mejora | Impacto | Esfuerzo |
|---|---|---|---|
| 1 | Corregir `ASSETS_DIR` en `cli/pcfutbol_cli.py` (resolver path robusto por fallback) | Muy alto | Bajo |
| 2 | Unificar `cli/` y `android/cli/` (single source of truth o sync automático) | Muy alto | Medio |
| 3 | Añadir suite de tests para CLI (smoke, season determinism, save load, market, entrenador) | Muy alto | Medio |
| 4 | Migrar persistencia de transferencias a IDs (`player_id`) en vez de nombres | Alto | Medio |
| 5 | Versionar schema de save (`save_version`) + migraciones | Alto | Medio |
| 6 | Recalibrar fuerza/lambda (percentiles o transformación no lineal) + test estadístico | Alto | Medio |
| 7 | Rehacer `_match_entrenador` para que táctica/sustituciones afecten probabilidad real | Alto | Alto |
| 8 | Añadir modo `--non-interactive` / `--seed` / `--fast` para QA automatizada | Alto | Medio |
| 9 | Implementar multi-save y/o multi-manager en CLI raíz (o portar del Android CLI) | Medio-Alto | Medio |
| 10 | Actualizar docs (`README`, `CLI_DOCUMENTATION`, comandos de test) y checks de CI | Medio | Bajo |

---

## 9) Conclusión

`cli/pcfutbol_cli.py` sigue siendo útil como base de simulación y ProManager, pero actualmente:

- está roto de arranque en la ubicación `cli/`,
- no tiene cobertura de tests,
- tiene deuda de persistencia y calibración,
- y se ha quedado atrás frente a `android/cli/pcfutbol_cli.py`.

Para que otro agente IA pueda iterar sin fricción, los pasos mínimos de estabilización son: **fix de rutas + unificación de CLI + tests + schema de save con IDs**.


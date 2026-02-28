# FEATURE_AUDIT — PC Fútbol 5 vs Android (2025/26)

Fecha de auditoría: 2026-02-28

## Fuentes revisadas
- `CODEX_TASK_feature_audit.md`
- `../docs/pcf55_rewrite_architecture.md`
- `../docs/pcf55_reverse_spec.md`
- `../docs/promanager_qa_2026-02-27.md`
- `../inversa/README.md`
- `../inversa/formato_PKF.md`
- `../tools/pcf55-updater/out/` (incluyendo `reverse_full_game.md`, `re_mandos_findings_20260226.md`, `IF5MANPT.HTM`, `IF5MAOFE.HTM`, `IF5MASEC.HTM`, `IF5MASEG.HTM`, `pcf55_teams_extracted.json`, `pcf55_transfermarkt_mapping.json`)
- Código Android actual de módulos `core:data`, `core:season-state`, `competition-engine`, `match-sim`, `manager-economy`, `promanager`, `ui`, `app`

## 1) Features del PC Fútbol 5 original (inventario por categoría)

| Categoría | Features originales identificadas |
|---|---|
| Modos de juego | Liga/Manager, Liga ProManager, Pro-Quinielas, Selecciones, Partido amistoso, Seguimiento liga, Seguimiento manual, Historia, Base de datos, Actualizaciones, Infofútbol, Importar notas |
| Gestión de equipo | Selección de equipo, pretemporada (5 partidos y presentación), plantilla, ficha jugador, alineación, tácticas, ver rival, entrenamiento, lesionados, fichajes/cesiones, contratos detallados (primas/cláusulas/renovaciones), empleados (2º entrenador/fisio/psicólogo/asistente/secretario/ojeador/juveniles/cuidador), secretario técnico, seguros, estadio/obras |
| Motor de competición | LIGA1/LIGA2/LIGA2B, Copa del Rey, Copa Europa, UEFA, Recopa, Supercopa España, Supercopa Europa, Intercontinental, jornadas, clasificación, resultados, promociones y descensos |
| Simulador de partidos | Simulación de resultado, eventos de partido, modo TV/manual con controles, opciones de partido (cámaras/sonido/tiempo), estadísticas de partido |
| Economía | Caja (ingresos/gastos), balance semanal, salarios, presupuestos, transferibles, primas, créditos, contratos y cláusulas, impacto de empleados |
| Carrera ProManager | Alta manager + contraseña, selección de ofertas, login PIN, info manager, ofertas de nueva temporada, prestigio/evolución por objetivos, historial |
| UI/UX | Menús DOS, muchas pantallas específicas de Manager, navegación INFOFUT/INFOFútbol, efectos audiovisuales clásicos |
| Datos | 260 equipos por slot, miles de jugadores, competiciones DBC, estadios, árbitros, entrenadores, fotos/escudos/camisetas, palmarés/historia |

## 2) Estado de implementación por feature (Android actual)

Leyenda: ✅ IMPLEMENTADO | 🔶 PARCIAL | ❌ FALTANTE | 🚫 DESCARTADO

### 2.1 Modos de juego

| Feature | Estado | Prioridad si falta | Evidencia Android | Nota |
|---|---|---|---|---|
| Liga/Manager | 🔶 PARCIAL | - | `MainMenuScreen`, `LigaSelectScreen`, `CompetitionRepository` | Existe flujo base de liga, pero faltan subsistemas clave de Manager original. |
| ProManager | 🔶 PARCIAL | - | `ProManagerOffersScreen`, `ProManagerViewModel`, `ProManagerRepository` | Crear/login/aceptar oferta funciona, carrera completa no. |
| Pro-Quinielas | ❌ FALTANTE | ALTA | Botón deshabilitado en `MainMenuScreen` | No hay rutas, motor ni UI. |
| Selecciones | ❌ FALTANTE | MEDIA | No hay pantallas ni repositorio de selecciones en `ui/` o motor | Sólo referencias históricas en docs RE. |
| Partido amistoso | ❌ FALTANTE | MEDIA | No existe screen/route dedicado | No implementado en Android. |
| Seguimiento liga | ❌ FALTANTE | MEDIA | No hay módulo equivalente a “SEGUIMIENTO 96/97” | El flujo actual es juego activo, no seguimiento histórico. |
| Seguimiento manual | ❌ FALTANTE | MEDIA | No hay pantalla/VM dedicada | Ausente. |
| Historia / Base de datos clásica | ❌ FALTANTE | BAJA | No hay pantallas `IF5HIS*` / `IF5DB*` | Fuera del loop actual. |
| Actualizaciones + Infofútbol (web interna) | 🚫 DESCARTADO | - | No existe navegador INFOFUT en app | Razonable para versión móvil actual. |
| Importar notas | ❌ FALTANTE | BAJA | Sin feature equivalente | No implementado. |

### 2.2 Gestión de equipo

| Feature | Estado | Prioridad si falta | Evidencia Android | Nota |
|---|---|---|---|---|
| Plantilla + ficha de jugador | 🔶 PARCIAL | - | `TeamSquadScreen`, `TeamSquadViewModel` | Lista y detalle sí; sin acciones completas de gestión. |
| Alineación titular/suplentes real | ❌ FALTANTE | ALTA | No hay persistencia de XI inicial real en flujo de partido | Simulador toma primeros 11 disponibles automáticamente. |
| Tácticas (guardar/cargar) | 🔶 PARCIAL | - | `TacticScreen`, `TacticViewModel`, `TacticPresetEntity` | Persistencia básica; parámetros incompletos y sin integración profunda con partido. |
| Marcajes y emparejamientos | ❌ FALTANTE | ALTA | No hay UI/lógica específica | Original tenía pantalla dedicada. |
| Ver rival | ❌ FALTANTE | MEDIA | No hay screen equivalente | Ausente. |
| Entrenamiento | ❌ FALTANTE | ALTA | Sin módulo/pantalla de entrenamiento | Falta impacto de progreso semanal. |
| Lesionados/sancionados | 🔶 PARCIAL | - | `SeasonStateRepository`, `PlayerFormUpdater`, `MatchSimulator` | Se modelan estados, pero no UI específica tipo “LESIONADOS”. |
| Fichajes/ofertas | 🔶 PARCIAL | - | `TransferMarketScreen`, `TransferMarketRepository` | Flujo simplificado, principalmente agentes libres en UI. |
| Contratos completos (primas, cláusulas dinámicas, cesiones, renovaciones) | ❌ FALTANTE | ALTA | Sólo campos básicos en `PlayerEntity` | No hay negociación contractual avanzada. |
| Secretario técnico (búsquedas avanzadas) | ❌ FALTANTE | MEDIA | No existe pantalla ni motor de scouting | No implementado. |
| Empleados del club | ❌ FALTANTE | MEDIA | No hay entidades/DAOs de empleados | Sistema ausente. |
| Juveniles + ojeador | ❌ FALTANTE | MEDIA | Sin cantera ni captación | Ausente. |
| Seguros | ❌ FALTANTE | BAJA | No hay modelo ni UI | Ausente. |
| Estadio/obras | ❌ FALTANTE | MEDIA | `TeamEntity` sólo tiene `stadiumName` | No hay gestión de infraestructuras. |
| Pretemporada (5 partidos + presentación) | ❌ FALTANTE | ALTA | No hay flujo pretemporada dedicado | Original lo usaba para moral/ingresos. |

### 2.3 Motor de competición

| Feature | Estado | Prioridad si falta | Evidencia Android | Nota |
|---|---|---|---|---|
| Ligas round-robin (jornadas) | ✅ IMPLEMENTADO | - | `FixtureGenerator.generateLeague`, `CompetitionRepository.setupLeague` | Motor funcional de ida/vuelta. |
| Clasificación | ✅ IMPLEMENTADO | - | `StandingsCalculator`, `StandingsScreen` | Funciona con persistencia Room. |
| Criterio desempate oficial completo (enfrentamiento directo) | 🔶 PARCIAL | - | Comentario en `StandingsCalculator`, orden actual por puntos/goal diff/gf | Falta desempate H2H real. |
| Copa del Rey | 🔶 PARCIAL | - | `CopaRepository`, `CopaScreen` | Implementada a partido único y formato simplificado. |
| Champions/UEFA/Recopa/Conference | ❌ FALTANTE | ALTA | Códigos existen en `CompetitionDefinitions`, sin engine operativo | No hay setup/simulación de torneos europeos. |
| Supercopas (España/Europa) | ❌ FALTANTE | MEDIA | Sin repositorios dedicados | Sólo definiciones estáticas. |
| Intercontinental | ❌ FALTANTE | MEDIA | Sin competencia equivalente | Ausente. |
| Ascensos/descensos | 🔶 PARCIAL | - | `EndOfSeasonUseCase` | LIGA1/LIGA2/RFEF sí, pero simplificado vs original. |
| Promoción Segunda B / playoffs legacy | ❌ FALTANTE | ALTA | No hay bracket específico de promoción | No replicado fielmente. |
| Persistencia estacional tipo ACTLIGA | 🔶 PARCIAL | - | `SeasonStateEntity` + DAOs | Equivalente conceptual, no compatibilidad completa con CPT/ACT original. |

### 2.4 Simulador de partidos

| Feature | Estado | Prioridad si falta | Evidencia Android | Nota |
|---|---|---|---|---|
| Simulación determinista de resultado | ✅ IMPLEMENTADO | - | `MatchSimulator`, `MatchSimulatorTest` | Basado en Poisson + seed reproducible. |
| Eventos (goles, amarillas, rojas, lesiones) | ✅ IMPLEMENTADO | - | `MatchSimulator` | Incluye eventos y persistencia de lesiones. |
| Penaltis eliminatorias | ✅ IMPLEMENTADO | - | `MatchOutcomeRules.simulatePenaltyShootout`, `CopaRepository` | Funciona en Copa. |
| Integración táctica profunda (todos parámetros del contrato RE) | 🔶 PARCIAL | - | `TacticParams` existe, uso real parcial en simulación | Sólo parte de variables influye realmente. |
| Modo TV/manual con control de jugadores | ❌ FALTANTE | ALTA | No hay engine de partido jugable | UI actual es marcador/simulación abstracta. |
| Cambios durante partido | ❌ FALTANTE | ALTA | Sin sistema de sustituciones in-match | No implementado. |
| Estadísticas detalladas post-partido | ❌ FALTANTE | MEDIA | `MatchResultScreen` muestra score básico | Falta panel rico de estadísticas. |
| Tiempo añadido | ✅ IMPLEMENTADO | - | `MatchResult` + cálculo en `MatchSimulator` | Feature moderna añadida. |

### 2.5 Economía

| Feature | Estado | Prioridad si falta | Evidencia Android | Nota |
|---|---|---|---|---|
| Objetivos de junta directiva | 🔶 PARCIAL | - | `BoardObjectives`, `EndOfSeasonUseCase` | Existe evaluación, pero sin sistema completo de decisiones de junta. |
| Presupuesto/caja ingresos-gastos | ❌ FALTANTE | ALTA | `TeamEntity.budgetK` no tiene ciclo financiero completo | No hay “Caja” semanal real. |
| Salarios y cláusulas (base) | 🔶 PARCIAL | - | `WageCalculator`, campos en `PlayerEntity` | Modelo simplificado. |
| Mercado con ventanas verano/invierno | ✅ IMPLEMENTADO | - | `CompetitionRepository` y `TransferMarketCalendar` | Apertura/cierre por jornada operativo. |
| Mercado completo club-a-club/transferibles/cesiones | ❌ FALTANTE | ALTA | `TransferMarketRepository` simplificado, sin cesiones ni lista transferibles | Falta negociación real. |
| Créditos/primas | ❌ FALTANTE | MEDIA | Sin modelo/DAO/UI | Ausente. |
| Impacto de empleados en economía y rendimiento | ❌ FALTANTE | MEDIA | No existe sistema de empleados | Ausente. |
| Ofertas IA de compra/venta | 🔶 PARCIAL | - | `generateAiOffers()` con TODO | No operativo end-to-end. |

### 2.6 Carrera ProManager

| Feature | Estado | Prioridad si falta | Evidencia Android | Nota |
|---|---|---|---|---|
| Crear manager + PIN | ✅ IMPLEMENTADO | - | `ProManagerOffersScreen`, `ProManagerRepository` | Flujo funcional. |
| Login manager | ✅ IMPLEMENTADO | - | `authenticate()` + UI | Funcional. |
| Selección de ofertas inicial | ✅ IMPLEMENTADO | - | `OfferPoolGenerator.generate` | Genera ofertas y permite aceptar. |
| Filtro “equipos débiles” fiel al original | 🔶 PARCIAL | - | Heurística en `OfferPoolGenerator` | Aproximado, no paridad exacta con lógica legacy RE. |
| Ofertas de fin de temporada por cumplimiento objetivo | 🔶 PARCIAL | - | `recordSeasonEnd()` ajusta prestigio | No hay motor completo de “ofertas para nueva temporada” como original. |
| Historial de carrera multi-temporada | ❌ FALTANTE | ALTA | Campo `careerHistoryJson` no se usa de verdad | Sin pantalla ni registro real de carrera. |
| Premios/reconocimientos manager | ❌ FALTANTE | MEDIA | Sin módulo/pantallas | Ausente. |

### 2.7 UI/UX

| Feature | Estado | Prioridad si falta | Evidencia Android | Nota |
|---|---|---|---|---|
| Tema DOS adaptado | ✅ IMPLEMENTADO | - | `PcfTheme.kt`, `PcfComponents.kt` | Base visual coherente. |
| Menú principal retro | ✅ IMPLEMENTADO | - | `MainMenuScreen` | Alto nivel visual, moderno. |
| Navegación principal Liga/ProManager | ✅ IMPLEMENTADO | - | `PcfNavHost` | Flujo base operativo. |
| Cobertura de pantallas Manager original | ❌ FALTANTE | ALTA | Muchas en `IF5MA*.HTM` sin equivalente | Falta gran parte del producto original. |
| Noticias (tablón) | 🔶 PARCIAL | - | `NewsScreen`, `NewsViewModel`, `NewsDao` | Funciona básico, falta profundidad editorial/eventual. |
| Sonidos/música DOS | 🚫 DESCARTADO | - | No assets ni audio engine dedicado | No crítico para MVP móvil (se puede añadir en polish). |
| Opciones gráficas hardware DOS (VESA/resolución/joystick) | 🚫 DESCARTADO | - | No aplica a Android moderno | Correcto descartarlo. |

### 2.8 Datos

| Feature | Estado | Prioridad si falta | Evidencia Android | Nota |
|---|---|---|---|---|
| Equipos base históricos (260 slots) | ✅ IMPLEMENTADO | - | Asset `pcf55_teams_extracted.json` (260) + `SeedLoader` | Preserva slot IDs. |
| Jugadores base (6978) | ✅ IMPLEMENTADO | - | Asset `pcf55_players_2526.csv` (6978) | Integrados en Room. |
| Mapping Transfermarkt | ✅ IMPLEMENTADO | - | Asset `pcf55_transfermarkt_mapping.json` | Enriquecimiento de competiciones/valor. |
| Datos de estadio (nombre) | 🔶 PARCIAL | - | `TeamEntity.stadiumName` | Sólo nombre, sin gestión de estadio. |
| Árbitros/entrenadores/fotos legacy | ❌ FALTANTE | MEDIA | Sin entidades ni import de DBC/PKF visuales | No migrado. |
| Compatibilidad import/export legacy PKF/DBC | ❌ FALTANTE | MEDIA | No hay bridge de compatibilidad en app | Queda en herramientas externas. |
| Ligas mundiales extra y plantillas sintéticas | ✅ IMPLEMENTADO | - | `SyntheticLeagues`, `CompetitionDefinitions` | Es ampliación no original. |

## 3) Features nuevas que NO estaban en el original

| Feature nueva | Estado | Impacto | ¿Puede “sobrar” si se busca réplica 1:1? |
|---|---|---|---|
| VAR (revisión + anulación de goles) | ✅ | Moderniza realismo | Sí, rompe fidelidad histórica pura. |
| Tiempo añadido modelado | ✅ | Más realismo de simulación | Sí, el original no lo modelaba así. |
| Ligas mundiales (ENG/ITA/FRA/DE/NL/PT/BE/TR/SC/RU/DK/PL/AUT/ARG/BRA/MEX/SAU) | ✅ | Mucho contenido nuevo | Sí, es expansión clara sobre PCF5 original. |
| Sistema RFEF moderno (LIGA2B2, RFEF1A/B, RFEF2 A-D) | ✅ | Actualiza pirámide española 2025/26 | Sí, no existía así en 1997. |
| Plantillas sintéticas con generación determinista | ✅ | Permite ampliar alcance | Sí, no es dato original. |
| UI Compose con animaciones CRT avanzadas | ✅ | Mejora UX moderna | No “sobra”, pero no es reproducción literal. |
| Arquitectura modular testeable (Room + repositorios + tests unitarios) | ✅ | Mejora ingeniería y mantenibilidad | No sobra; mejora técnica necesaria. |

## 4) Top 10 features críticas faltantes (prioridad ALTA)

1. Loop completo de Liga Manager (pretemporada real + ciclo jornada completo con decisiones de manager).
2. Alineación real y gestión de titulares/suplentes integrada de verdad con el simulador.
3. Sistema de tácticas completo (todos parámetros legacy, marcajes, roles, persistencia por partido).
4. Mercado de fichajes completo (club-a-club, transferibles, cesiones, negociación real).
5. Economía de club real (caja, ingresos/gastos semanales, créditos, primas, presupuestos vivos).
6. Contratos completos de jugador (renovaciones, cláusulas efectivas, bonus, condiciones).
7. Competiciones europeas y supercopas operativas (CEURO, RECOPA, CUEFA, SCESP, SCEUR).
8. ProManager de carrera real multi-temporada (historial, ofertas post-temporada por objetivos, progresión robusta).
9. Promoción/playoff de categorías españolas fiel al comportamiento original.
10. Modo de partido avanzado (al menos estadísticas completas y cambios in-match; idealmente modo TV/manual).

## 5) Estimación de completitud

Estimación actual: **~42% de paridad funcional** respecto a features jugables de PC Fútbol 5.

Criterio usado para estimar:
- Se excluyeron de la paridad los ítems marcados como 🚫 DESCARTADO por no aplicar a Android moderno.
- Se ponderó **✅=1**, **🔶=0.5**, **❌=0** sobre el conjunto auditado de features jugables.
- El proyecto está bien encaminado en base técnica (datos, simulación base, estructura modular), pero todavía está lejos de la cobertura funcional completa del original.

## Conclusión breve

La app Android ya tiene una **base sólida de motor y datos** y un **vertical slice jugable** (liga/pro-manager/copa en versión simplificada). La mayor brecha está en la **profundidad manager**: economía real, contratos, mercado completo, staff, entrenamiento y competiciones europeas.


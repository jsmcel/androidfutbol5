# Tarea para Codex — Ideas de modernización: PC Fútbol 5 que se siente 2025

## Contexto
El proyecto es un clon de PC Fútbol 5 (1997) actualizado a la temporada 2025/26.
El desafío es mantener la esencia retro del juego original pero incorporar mecánicas
modernas que el jugador actual espera encontrar.

## Tu tarea
Estudia el código actual en `android/` y propón/implementa estas mejoras.
Todo lo que existe en el CLI (`tools/cli/pcfutbol_cli.py`) debe llegar también a la app Android.

---

## 1. REGLAS MODERNAS DE PARTIDO (ya en CLI, trasladar a Android)

### 5 cambios / 3 ventanas / concussion sub
- `MatchState` (o similar) debe trackear: `subsLeft=5`, `windowsLeft=3`, `concussionSubAvailable=true`
- Ventana libre en el descanso (no cuenta como ventana de juego)
- 6ª sustitución si hay lesión por golpe en la cabeza (concussionSub)
- `MatchdayViewModel` expone estos estados al UI

### VAR (Video Assistant Referee)
- Después de cada gol: 18% probabilidad de revisión VAR
- Si hay revisión: 38% probabilidad de anulación (fuera de juego / mano / origen del penalty)
- Debe haber un estado `MatchPhase.VAR_REVIEW` en el simulador
- Eventos de tipo `MatchEvent.VarGoalDisallowed(reason)` y `MatchEvent.VarGoalConfirmed`
- En la pantalla: mostrar "VAR revisando..." con countdown antes del veredicto

### Tiempo añadido real
- Calcular tiempo añadido basado en: amarillas + sustituciones + revisiones VAR + goles
- Primera parte: base 1min + 0.5/amarilla + 2/VAR + 1/gol → max 6min
- Segunda parte: base 2min + 0.5/amarilla + 2/VAR + 1/gol + 0.5/sustitución → max 10min
- Si hay pérdida de tiempo activa: +2 minutos extra
- `MatchEvent.AddedTime(minutes: Int)` al final de cada parte

### Pérdida de tiempo
- Nuevo parámetro táctico: `perdidaTiempo: Boolean` en `TacticPresetEntity`
- Efecto: genera más amarillas en el partido, aumenta tiempo añadido
- Pequeña penalización en lambda de goles (-0.4 de strength)
- Lógica ya en el CLI — trasladar exactamente a `StrengthCalculator.kt`

---

## 2. IDEAS DE MODERNIZACIÓN (nuevas, implementar si el código lo permite)

### A. Tarjetas rojas y expulsiones
- Segunda amarilla → roja automática (trackear yellows por jugador/equipo)
- Roja directa (5% de probabilidad en faltas muy duras — si `faltas==3`)
- `MatchEvent.RedCard(team, player)`
- Al quedarse con 10, reducir lambda del equipo afectado un 20%

### B. Penaltis
- Al final del partido si está empatado en Copa (CREY): penaltis
- Serie de penaltis: 5 lanzamientos por equipo, desempate si empata
- Para ligas: sin penaltis, el empate cuenta como empate

### C. Moral y racha de resultados
- `PlayerEntity.moral` ya existe (0..99)
- Después de victoria: moral +3 a todos los jugadores
- Después de derrota: moral -2
- Después de empate: sin cambio
- Goleada (+3 goles): moral +5 ganador, -5 perdedor
- La moral afecta al `StrengthCalculator`: (moral - 50) / 100 * 2.0 de bonus/penalización

### D. Lesiones en partido
- Durante el partido: 8% probabilidad de lesión por jornada
- Si hay lesión: `PlayerEntity.injuryWeeksLeft = rng(2,8)`
- `PlayerEntity.status = 1` (lesionado)
- Jugadores lesionados no disponibles para siguiente partido
- En `processMatchdayEnd()` decrementar `injuryWeeksLeft` correctamente

### E. Estadísticas de jugadores
- Nuevo DAO/entidad `PlayerSeasonStats(playerId, teamId, season, goals, assists, yellowCards, redCards, matchesPlayed, minutesPlayed)`
- Actualizar después de cada partido
- Pantalla "Estadísticas" (`/stats/{competitionCode}`) con clasificación de goleadores, asistentes

### F. Mercado de fichajes — Sistema de pujas
- En vez de compra directa: hacer una oferta
- `TransferOffer(fromTeam, toTeam, playerId, amount, status: PENDING/ACCEPTED/REJECTED)`
- La IA puede rechazar ofertas si `amount < player.marketValue * 0.8`
- Las ofertas se resuelven al avanzar jornada (procesadas en `advanceMatchday()`)
- Noticia generada cuando una oferta es aceptada/rechazada

### G. Efectos de clima en partidos
- `WeatherCondition` enum: SOLEADO, NUBLADO, LLUVIOSO, NIEVE, CALOR_EXTREMO
- Clima generado aleatoriamente por jornada (seed-deterministic)
- LLUVIOSO: -10% velocidad media → reduce lambdas levemente
- NIEVE: -20% para ambos equipos, más varianza en resultados
- CALOR_EXTREMO: reduce resistencia → ligero penalty en segunda parte
- `MatchEvent.WeatherReport(condition)` al inicio del partido

### H. Rueda de prensa post-partido
- Después de cada partido del manager: 3 preguntas aleatorias de prensa
- Respuesta A/B con efecto en moral: "¿Satisfecho con el resultado?" A=Sí/B=No
- Afecta moral equipo (+/-2) y prestige de manager a largo plazo
- Pantalla simple con texto pregunta + dos botones de respuesta

### I. Clasificación histórica de temporadas
- `SeasonArchive(season, league, champion, relegated, managerTeam, managerFinalPosition)`
- Al `advanceToNextSeason()`: guardar el estado actual en SeasonArchive
- Pantalla "Historial" con tabla de temporadas anteriores

### J. Notificaciones de mercado IA
- La IA también hace fichajes en el período de transferencias
- Equipos top compran jugadores de equipos pequeños si tienen presupuesto
- Genera noticias ("FC Barcelona ficha a X por 45M€")
- Lógica simple: top-8 equipos de LIGA1 compran el mejor jugador disponible de LIGA2

---

## 3. TECH DEBT / STUBS pendientes

- `generateAiOffers()` en algún ViewModel está como stub — implementar
- `gradlew` wrapper puede estar ausente — verificar y añadir si falta
- Tests: añadir `MatchSimRegressionTest` — 100 partidos con seed fija, verificar reproducibilidad
- `CompetitionRepository.byRoundForCopa()` — verificar que usa el FixtureDao correcto

---

## Prioridad sugerida
1. **Alta**: Tarjetas rojas, Penaltis Copa, Moral, Lesiones (impactan el juego directamente)
2. **Media**: Estadísticas de jugadores, Mercado con pujas, Clima
3. **Baja**: Rueda de prensa, Historial, Noticias IA

## Reglas de implementación
- Sin commits automáticos
- Tests JUnit5 para lógica nueva en `match-sim` y `competition-engine`
- Todo en español (comentarios de código y strings UI)
- No crear archivos .md adicionales

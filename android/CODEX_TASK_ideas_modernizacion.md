# Tarea para Codex â€” Ideas de modernizaciÃ³n: PC FÃºtbol 5 que se siente 2025

## Contexto
El proyecto es un clon de PC FÃºtbol 5 (1997) actualizado a la temporada 2025/26.
El desafÃ­o es mantener la esencia retro del juego original pero incorporar mecÃ¡nicas
modernas que el jugador actual espera encontrar.

## Tu tarea
Estudia el cÃ³digo actual en `android/` y propÃ³n/implementa estas mejoras.
Todo lo que existe en el CLI (`cli/pcfutbol_cli.py`) debe llegar tambiÃ©n a la app Android.

---

## 1. REGLAS MODERNAS DE PARTIDO (ya en CLI, trasladar a Android)

### 5 cambios / 3 ventanas / concussion sub
- `MatchState` (o similar) debe trackear: `subsLeft=5`, `windowsLeft=3`, `concussionSubAvailable=true`
- Ventana libre en el descanso (no cuenta como ventana de juego)
- 6Âª sustituciÃ³n si hay lesiÃ³n por golpe en la cabeza (concussionSub)
- `MatchdayViewModel` expone estos estados al UI

### VAR (Video Assistant Referee)
- DespuÃ©s de cada gol: 18% probabilidad de revisiÃ³n VAR
- Si hay revisiÃ³n: 38% probabilidad de anulaciÃ³n (fuera de juego / mano / origen del penalty)
- Debe haber un estado `MatchPhase.VAR_REVIEW` en el simulador
- Eventos de tipo `MatchEvent.VarGoalDisallowed(reason)` y `MatchEvent.VarGoalConfirmed`
- En la pantalla: mostrar "VAR revisando..." con countdown antes del veredicto

### Tiempo aÃ±adido real
- Calcular tiempo aÃ±adido basado en: amarillas + sustituciones + revisiones VAR + goles
- Primera parte: base 1min + 0.5/amarilla + 2/VAR + 1/gol â†’ max 6min
- Segunda parte: base 2min + 0.5/amarilla + 2/VAR + 1/gol + 0.5/sustituciÃ³n â†’ max 10min
- Si hay pÃ©rdida de tiempo activa: +2 minutos extra
- `MatchEvent.AddedTime(minutes: Int)` al final de cada parte

### PÃ©rdida de tiempo
- Nuevo parÃ¡metro tÃ¡ctico: `perdidaTiempo: Boolean` en `TacticPresetEntity`
- Efecto: genera mÃ¡s amarillas en el partido, aumenta tiempo aÃ±adido
- PequeÃ±a penalizaciÃ³n en lambda de goles (-0.4 de strength)
- LÃ³gica ya en el CLI â€” trasladar exactamente a `StrengthCalculator.kt`

---

## 2. IDEAS DE MODERNIZACIÃ“N (nuevas, implementar si el cÃ³digo lo permite)

### A. Tarjetas rojas y expulsiones
- Segunda amarilla â†’ roja automÃ¡tica (trackear yellows por jugador/equipo)
- Roja directa (5% de probabilidad en faltas muy duras â€” si `faltas==3`)
- `MatchEvent.RedCard(team, player)`
- Al quedarse con 10, reducir lambda del equipo afectado un 20%

### B. Penaltis
- Al final del partido si estÃ¡ empatado en Copa (CREY): penaltis
- Serie de penaltis: 5 lanzamientos por equipo, desempate si empata
- Para ligas: sin penaltis, el empate cuenta como empate

### C. Moral y racha de resultados
- `PlayerEntity.moral` ya existe (0..99)
- DespuÃ©s de victoria: moral +3 a todos los jugadores
- DespuÃ©s de derrota: moral -2
- DespuÃ©s de empate: sin cambio
- Goleada (+3 goles): moral +5 ganador, -5 perdedor
- La moral afecta al `StrengthCalculator`: (moral - 50) / 100 * 2.0 de bonus/penalizaciÃ³n

### D. Lesiones en partido
- Durante el partido: 8% probabilidad de lesiÃ³n por jornada
- Si hay lesiÃ³n: `PlayerEntity.injuryWeeksLeft = rng(2,8)`
- `PlayerEntity.status = 1` (lesionado)
- Jugadores lesionados no disponibles para siguiente partido
- En `processMatchdayEnd()` decrementar `injuryWeeksLeft` correctamente

### E. EstadÃ­sticas de jugadores
- Nuevo DAO/entidad `PlayerSeasonStats(playerId, teamId, season, goals, assists, yellowCards, redCards, matchesPlayed, minutesPlayed)`
- Actualizar despuÃ©s de cada partido
- Pantalla "EstadÃ­sticas" (`/stats/{competitionCode}`) con clasificaciÃ³n de goleadores, asistentes

### F. Mercado de fichajes â€” Sistema de pujas
- En vez de compra directa: hacer una oferta
- `TransferOffer(fromTeam, toTeam, playerId, amount, status: PENDING/ACCEPTED/REJECTED)`
- La IA puede rechazar ofertas si `amount < player.marketValue * 0.8`
- Las ofertas se resuelven al avanzar jornada (procesadas en `advanceMatchday()`)
- Noticia generada cuando una oferta es aceptada/rechazada

### G. Efectos de clima en partidos
- `WeatherCondition` enum: SOLEADO, NUBLADO, LLUVIOSO, NIEVE, CALOR_EXTREMO
- Clima generado aleatoriamente por jornada (seed-deterministic)
- LLUVIOSO: -10% velocidad media â†’ reduce lambdas levemente
- NIEVE: -20% para ambos equipos, mÃ¡s varianza en resultados
- CALOR_EXTREMO: reduce resistencia â†’ ligero penalty en segunda parte
- `MatchEvent.WeatherReport(condition)` al inicio del partido

### H. Rueda de prensa post-partido
- DespuÃ©s de cada partido del manager: 3 preguntas aleatorias de prensa
- Respuesta A/B con efecto en moral: "Â¿Satisfecho con el resultado?" A=SÃ­/B=No
- Afecta moral equipo (+/-2) y prestige de manager a largo plazo
- Pantalla simple con texto pregunta + dos botones de respuesta

### I. ClasificaciÃ³n histÃ³rica de temporadas
- `SeasonArchive(season, league, champion, relegated, managerTeam, managerFinalPosition)`
- Al `advanceToNextSeason()`: guardar el estado actual en SeasonArchive
- Pantalla "Historial" con tabla de temporadas anteriores

### J. Notificaciones de mercado IA
- La IA tambiÃ©n hace fichajes en el perÃ­odo de transferencias
- Equipos top compran jugadores de equipos pequeÃ±os si tienen presupuesto
- Genera noticias ("FC Barcelona ficha a X por 45Mâ‚¬")
- LÃ³gica simple: top-8 equipos de LIGA1 compran el mejor jugador disponible de LIGA2

---

## 3. TECH DEBT / STUBS pendientes

- `generateAiOffers()` en algÃºn ViewModel estÃ¡ como stub â€” implementar
- `gradlew` wrapper puede estar ausente â€” verificar y aÃ±adir si falta
- Tests: aÃ±adir `MatchSimRegressionTest` â€” 100 partidos con seed fija, verificar reproducibilidad
- `CompetitionRepository.byRoundForCopa()` â€” verificar que usa el FixtureDao correcto

---

## Prioridad sugerida
1. **Alta**: Tarjetas rojas, Penaltis Copa, Moral, Lesiones (impactan el juego directamente)
2. **Media**: EstadÃ­sticas de jugadores, Mercado con pujas, Clima
3. **Baja**: Rueda de prensa, Historial, Noticias IA

## Reglas de implementaciÃ³n
- Sin commits automÃ¡ticos
- Tests JUnit5 para lÃ³gica nueva en `match-sim` y `competition-engine`
- Todo en espaÃ±ol (comentarios de cÃ³digo y strings UI)
- No crear archivos .md adicionales


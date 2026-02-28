# Tarea Codex: Mejoras de jugabilidad para primera prueba real

## Contexto
La app se va a probar por primera vez. El objetivo es que todo lo que el usuario
toca funcione bien y tenga feedback visual claro. Lee el FEATURE_AUDIT.md para
entender el estado actual y el código existente antes de tocar nada.

## Sin commits al finalizar.

---

## MEJORA 1: Fichajes IA — los equipos rivales también fichan

### Problema actual
Solo el manager puede fichar. Los equipos rivales no hacen nada en el mercado,
lo que hace que la liga sea poco realista.

### Implementar en `TransferMarketRepository.kt`

Añadir función `runAiTransferWindow(competitionCode: String)`:
```kotlin
suspend fun runAiTransferWindow(competitionCode: String) {
    // Para cada equipo de la competición (excepto managerTeamId):
    //   - Con 30% de probabilidad, intenta fichar 1 agente libre
    //   - Busca agente libre cuyo CA esté en rango ±10 del promedio del equipo
    //   - Si el equipo tiene presupuesto > 500K → ficha al jugador y descuenta del budget
    //   - Genera noticia "TRANSFER" con el movimiento
}
```

Llamar a `runAiTransferWindow()` desde `CompetitionRepository.advanceMatchday()`
cuando `ss.transferWindowOpen == true` y al cerrar la ventana.

---

## MEJORA 2: Pantalla de Bajas e Indisponibles

### Problema actual
No hay forma de ver rápidamente qué jugadores están lesionados o sancionados.
El manager tiene que scrollear toda la plantilla.

### Implementar en `TeamSquadScreen.kt`

Añadir un panel colapsable en la parte superior de la pantalla de plantilla:

```
┌─────────────────────────────────────────┐
│ ⚠ BAJAS (3)                      [▼]   │
│  🔴 Gavi      LESIONADO  3 sem restantes │
│  🟡 Alaba     SANCIONADO 1 partido       │
│  🔴 Pedri     LESIONADO  1 sem restante  │
└─────────────────────────────────────────┘
```

Mostrar solo jugadores con `status == 1` (lesión) o `status == 2` (sanción).
Si no hay bajas → no mostrar el panel.
Colores: lesionado = DosRed, sancionado = DosYellow.

---

## MEJORA 3: Resultados recientes del equipo en LigaSelectScreen

### Problema actual
En el menú principal de liga no hay feedback de los últimos resultados.
El jugador no sabe cómo va su equipo sin ir a clasificación.

### Implementar en `LigaSelectViewModel.kt`

Añadir campo `recentResults: List<String> = emptyList()` al `LigaSelectUiState`.
Formato de cada elemento: `"W"`, `"D"`, `"L"` (victoria, empate, derrota).
Calcular los últimos 5 partidos del equipo del manager en la liga activa.

En `LigaSelectScreen.kt`, mostrar debajo del nombre del equipo:
```
> REAL MADRID        💰 450M€
  Últimos: W W L D W     (verde/rojo/amarillo)
```

Añadir método en `FixtureDao`:
```kotlin
@Query("""
    SELECT * FROM fixtures
    WHERE competitionCode = :comp
    AND (homeTeamId = :teamId OR awayTeamId = :teamId)
    AND homeGoals >= 0
    ORDER BY matchday DESC
    LIMIT :limit
""")
suspend fun recentByTeam(comp: String, teamId: Int, limit: Int = 5): List<FixtureEntity>
```

---

## MEJORA 4: Historial de la temporada en NewsScreen

### Problema actual
Las noticias están bien pero no hay resumen de resultados de las últimas jornadas.

### Implementar en `NewsScreen.kt`

Añadir un chip/tab "RESULTADOS" además de los existentes (ALL, RESULT, TRANSFER, etc.).
Al seleccionar "RESULTADOS", filtrar solo noticias de categoría "RESULT" ordenadas
por jornada descendente.

El `NewsViewModel` ya tiene categorías — solo añadir el chip en la UI.

---

## MEJORA 5: Feedback visual al simular jornada

### Problema actual
Al pulsar "SIMULAR JORNADA" en MatchdayScreen, los resultados aparecen pero no hay
indicación clara de cuáles son los resultados del equipo del manager.

### Implementar en `Stubs.kt` (MatchdayScreen)

Tras simular, el partido del equipo manager debe resaltarse visualmente:
- Borde dorado/cyan en el partido del equipo del manager
- Texto "TU PARTIDO" en pequeño encima del resultado
- Color del resultado: verde=victoria, amarillo=empate, rojo=derrota

El `MatchdayViewModel` ya tiene `managerTeamId` — usarlo para identificar el partido.

---

## MEJORA 6: Pantalla de Finanzas del Club

### Problema actual
El presupuesto se muestra pero no hay desglose de ingresos/gastos.

### Crear `FinancesScreen.kt` básica

Layout:
```
┌────────────────────────────────────────┐
│ ← FINANZAS — REAL MADRID              │
├────────────────────────────────────────┤
│ PRESUPUESTO ACTUAL      450.000.000 €  │
│ ──────────────────────────────────────│
│ PLANTILLA (28 jugadores)               │
│  Salario semanal total     2.340K€/sem │
│  Coste temporada estimado   121M€      │
│                                        │
│ VALOR DE MERCADO PLANTILLA  892M€      │
│ JUGADOR MÁS VALIOSO                    │
│   Vinicius Jr.              180M€      │
│ JUGADOR MENOS VALIOSO                  │
│   Lunin                       8M€      │
└────────────────────────────────────────┘
```

Calcular datos desde `PlayerEntity` (wageK, marketValueEur).
Añadir ruta `/finances` en NavHost y botón "FINANZAS" en LigaSelectScreen.

---

## MEJORA 7: Modo "Selección Rápida" en la liga — ver clasificaciones de otras ligas

### Problema actual
El manager puede cambiar de liga activa, pero no puede ver la clasificación de otra
liga rápidamente desde el menú.

### En `LigaSelectScreen.kt`
Añadir botón "VER CLASIFICACION" junto a cada liga en el selector de ligas
(en `LeagueGroupsSection`), que navegue directamente a `StandingsScreen(comp=code)`.

---

## Notas técnicas
- Mantén el estilo visual: DosBlack, DosYellow, DosCyan, FontFamily.Monospace
- Sigue el patrón HiltViewModel + StateFlow + collectAsState()
- Prioriza las mejoras 1-4 sobre las 5-7 si el tiempo apremia
- Room version sigue en 3 — no toques la versión si no añades entidades nuevas
- Si añades entidades, sube a 4 con fallbackToDestructiveMigration()

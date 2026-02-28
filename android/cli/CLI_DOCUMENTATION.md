# CLI PC Fútbol 5 — Documentación y Resultados QA

**Archivo**: `tools/cli/pcfutbol_cli.py` (1803 líneas)
**Python**: 3.9+ (stdlib solo, sin dependencias externas)
**Datos**: `android/core/data/src/main/assets/` (CSV + JSON)
**Fecha QA**: 2026-02-28

---

## Menú principal

```
╔══════════════════════════════════╗
║    PC FÚTBOL 5  ·  CLI  2025/26  ║
║      Temporada real — Python      ║
╚══════════════════════════════════╝

✓ 20 equipos en Primera División
✓ 22 equipos en Segunda División
✓ 1092 jugadores cargados

  1. Simular temporada completa
  2. Ver plantilla de equipo
  3. Partido rápido
  4. Top jugadores
  5. Ranking de fortaleza
  6. PRO MANAGER (modo carrera)
  0. Salir
```

---

## Opciones del menú principal

### 0 — Salir
✅ Funciona. Muestra "¡Hasta la próxima temporada!".

---

### 1 — Simular temporada completa
✅ Funciona. Simula las 38 jornadas de Liga1 y 42 de Liga2.

**Flujo:**
- Selecciona competición (1=Primera, 2=Segunda)
- Simula todos los partidos con seed aleatoria
- Muestra clasificación final con campeón, relegados y ascendidos

**Resultado de test:**
```
🏆 CAMPEÓN PRIMERA DIVISIÓN: Real Oviedo (59 pts)
  Real Madrid:   10º (52 pts)
  FC Barcelona:  17º (45 pts)

🔻 DESCENSOS: Real Madrid B, Deportivo Alavés, UD Las Palmas
🔺 ASCENSOS: SD Eibar, Granada CF, CD Mirandés
```

**⚠️ BUG 1 — CALIBRACIÓN DE FUERZA DEMASIADO PLANA:**
Todos los equipos de Liga1 tienen una fuerza en el rango 48-52 puntos.
La diferencia entre el mejor (Barça/Madrid ~52) y el peor (~48) es de solo 4 puntos.
Esto genera resultados poco realistas donde equipos modestos (Real Oviedo, Leganés)
pueden ganar la liga fácilmente. La distribución Poisson no refleja la superioridad real
de los grandes equipos.

---

### 2 — Ver plantilla de equipo
✅ Funciona correctamente.

**Flujo:**
- Lista los 42 equipos (20 Liga1 + 22 Liga2) numerados
- Selecciona equipo por número
- Muestra tabla con: #, JUGADOR, POS, ED, ME, VE, RE, CA, VALOR

**Resultado de test (CD Mirandés):**
```
#   JUGADOR          POS                  ED  ME  VE  RE  CA VALOR
1   Thiago Helguera  Defensive Midfield   19  69  70  71  74 3M
2   Adrián Pica      Centre-Back          23  68  63  72  62 1M
...
27  Juanpa Palomares Goalkeeper           25  57  44  61  60 300K
```

---

### 3 — Partido rápido
✅ Funciona para equipos distintos.

**Flujo:**
- Muestra lista de 42 equipos
- Selecciona equipo local y visitante por número
- Simula el partido y muestra resultado

**Resultado de test:**
```
Real Madrid 2-0 FC Barcelona
```

**⚠️ BUG 2 — MISMO EQUIPO EN AMBOS ROLES:**
Si el usuario elige el mismo equipo (mismo número) para local y visitante:
```
AD Ceuta FC 3-3 AD Ceuta FC
```
No hay validación que impida `local == visitante`. El partido se simula igualmente,
dando lugar a resultados absurdos (un equipo contra sí mismo).

**Fix sugerido**: añadir validación `if away_idx == home_idx: print("Error: elige equipos distintos")` en `menu_quick_match`.

---

### 4 — Top jugadores
✅ Funciona. Permite filtrar por liga, posición y cantidad.

**Flujo:**
- Selecciona competición (1=Primera, 2=Segunda, 3=Ambas)
- Selecciona posición (1=Todas, 2=Porteros, 3=Defensas, 4=Medios, 5=Delanteros)
- Selecciona cuántos mostrar (5-50)
- Muestra ranking por media (ME)

**Resultado de test (top 20 de Primera, todas posiciones):**
```
1   Pedri                Central Midfield      26  93  ...
2   Lamine Yamal         Right Winger          17  91  ...
3   Fede Valverde        Central Midfield      26  91  ...
4   Jude Bellingham      Central Midfield      21  91  ...
5   Vinícius Júnior      Left Winger           24  90  ...
6   Kylian Mbappé        Centre-Forward        26  90  ...
...
```

---

### 5 — Ranking de fortaleza
✅ Funciona. Muestra los 42 equipos ordenados por fuerza compuesta.

**Muestra:** slot_id, nombre, comp, fortaleza, posición en ranking.

---

### 6 — PRO MANAGER (modo carrera)
Sistema de modo carrera con persistencia en `~/.pcfutbol_career.json`.

#### Sub-menú de temporada (dentro del ProManager)
```
1. Simular jornada {N}
2. Clasificación completa
3. Plantilla
4. Noticias
5. Simular resto de temporada
6. Mercado de fichajes [ABIERTO/CERRADO]
7. Táctica
0. Guardar y salir
```

**Funciona:**
- ✅ Crear nuevo manager con nombre
- ✅ Generar ofertas según prestigio (prestige=1 → bottom 12 de Liga2)
- ✅ Ver clasificación (opción 2)
- ✅ Ver plantilla (opción 3)
- ✅ Ver noticias (opción 4) — con item de inicio de temporada
- ✅ Simular jornada en modo automático (opción 1 → modo 1)
- ✅ Mercado de fichajes (opción 6):
  - ✅ Buscar y fichar (con filtro por posición y presupuesto)
  - ✅ Vender jugador (con 60% de valor de mercado)
  - ✅ Ver plantilla desde el mercado
- ✅ Guardar y salir
- ✅ Continuar partida guardada
- ✅ Fin de temporada con pantalla de resultados
- ✅ Nueva temporada con nuevas ofertas y prestigio actualizado
- ✅ Modo Entrenador (animación en tiempo real): presupuesto de partida, VAR, lesiones, sustituciones

**⚠️ BUG 3 — CRÍTICO: OPCIÓN 7 (TÁCTICA) NO IMPLEMENTADA EN EL SEASON LOOP:**
La opción 7 (Táctica) aparece en el menú del season loop pero el handler
`elif op == 7:` está AUSENTE en el código. Al seleccionar 7, el bucle continúa
sin hacer nada, como si se hubiera pulsado una tecla inválida.

Esto provoca que cambiar la táctica sea IMPOSIBLE desde el ProManager.
El `_tactic_menu(data)` sí existe y está implementado pero nunca se llama.

**Fix**: añadir en `_season_loop` (tras `elif op == 6:`):
```python
elif op == 7:
    _tactic_menu(data)
    tactic = data.get("tactic", tactic)  # refrescar referencia
```

**⚠️ BUG 4 — TÁCTICA NO SE REFLEJA EN EL RESUMEN DE LA PANTALLA:**
Aunque se corrigiera el bug 3, la línea `tactic = data.setdefault("tactic", ...)` se
ejecuta al inicio de cada iteración del while loop. Si `_tactic_menu` modifica
`data["tactic"]` en el mismo objeto (`t = data.setdefault(...)`), los cambios SÍ
persisten (mismo objeto por referencia). Sin embargo, la variable `tactic` en el
scope del loop apunta al mismo dict, por lo que debería actualizarse automáticamente.

---

## Modo Entrenador (_match_entrenador)
✅ Funciona correctamente. Características verificadas en código:

| Feature | Estado |
|---|---|
| Animación minuto a minuto (90') | ✅ |
| Tiempo añadido dinámico (1ª y 2ª parte) | ✅ |
| VAR (18% de los goles se revisan, 38% se anulan) | ✅ |
| Tarjetas amarillas (2-5 por partido) | ✅ |
| Pérdida de tiempo (+amarillas si táctica activa) | ✅ |
| Golpe en la cabeza / 6ª sustitución de concusión (30%) | ✅ |
| 5 cambios / 3 ventanas en juego | ✅ |
| Ventana de descanso (gratis, no consume ventana) | ✅ |
| Cambio de táctica en parada (T durante partido) | ✅ |
| Marcador actualizado con nombres abreviados | ✅ |
| Vista de portería con balón (zona 0-4) | ✅ |

**Nota**: el modo entrenador usa `time.sleep(0.13)` por minuto → ~12-20 segundos
por partido. En piped stdin, funciona pero los inputs deben suministrarse previamente.

---

## Flujo ProManager completo (test real)

**Manager**: Jose | **Equipo**: CD Mirandés | **Liga**: Segunda División

| Jornada | Resultado | Posición | Noticias |
|---|---|---|---|
| 1 | Burgos CF 2 - **3** CD Mirandés ✅ | 6º (3 pts) | Victoria a domicilio (3-2). Equipo en puesto 6. |
| 2 | **CD Mirandés 1 - 2** SD Eibar ❌ | 10º (3 pts) | Derrota en casa ante SD Eibar (1-2). Posición 10. |
| 3 | FC Andorra vs CD Mirandés | (Modo Entrenador lanzado) | — |

---

## Bugs críticos — Resumen

| # | Descripción | Severidad | Función afectada |
|---|---|---|---|
| 1 | Calibración plana: todos los equipos ~47-52 fuerza; Madrid/Barça pueden no ganar Liga | ALTO | `team_strength()` / simulador Poisson |
| 2 | Sin validación de equipo local==visitante en partido rápido | MEDIO | `menu_quick_match()` |
| 3 | Opción 7 (Táctica) en season loop no tiene handler — táctica imposible de cambiar | CRÍTICO | `_season_loop()` |
| 4 | La táctica definida en PreManager se aplica pero nunca se puede actualizar vía UI | ALTO | `_season_loop()`, `_tactic_menu()` |

---

## Arquitectura del simulador

```
Team.strength() = promedio ponderado de atributos de jugadores
  ├── attack_strength() = remate×0.35 + regate×0.25 + tiro×0.2 + pase×0.1 + ve×0.1
  ├── defense_strength() = entrada×0.4 + re×0.25 + ag×0.2 + ca×0.15
  └── gk_strength() = portero×0.6 + re×0.2 + ag×0.1 + ca×0.1

simulate_match(home, away, seed):
  ├── strengthToLambda(): convierte fuerza → parámetro Poisson
  ├── home_advantage: +12% λ
  └── poissonSample(): genera goles con distribución Poisson
```

**Problema de calibración**: la fuerza de todos los equipos está en el rango 47-52.
`strengthToLambda` produce λ muy similares para todos → partidos poco predecibles.
La solución es escalar la fuerza con una curva exponencial o percentiles.

---

## Cómo ejecutar el CLI

```bash
cd /path/to/pcfutbol
python tools/cli/pcfutbol_cli.py

# Test no interactivo (ejemplo):
printf "3\n29\n18\n0\n" | python tools/cli/pcfutbol_cli.py
#   ^ partido rápido: Real Madrid (29) vs FC Barcelona (18), luego salir
```

---

## Datos cargados

- **Equipos**: `pcf55_teams_extracted.json` — 260 slots (20 Liga1 + 22 Liga2 + resto)
- **Jugadores**: `pcf55_players_2526.csv` — 1092 jugadores de Liga1+Liga2
  - Rango de plantillas: 23-30 jugadores por equipo (media 26)
- **Save de carrera**: `~/.pcfutbol_career.json` (JSON en home del usuario)

---

## Ideas para futuras mejoras

1. **Calibración de fuerza**: usar percentiles o escala exponencial para separar Madrid/Barça del resto
2. **Validación local≠visitante**: check simple en `menu_quick_match`
3. **Implementar handler de táctica** en `_season_loop` (1 línea)
4. **Evolución de jugadores**: subida/bajada de atributos por temporada (jóvenes mejoran, veteranos bajan)
5. **Cantera / juveniles**: jugadores de 16-18 años con potencial oculto
6. **Retiradas automáticas**: jugadores >35 años con probabilidad de retiro al final de temporada
7. **Multi-manager**: guardar N managers simultáneos (estructura `{"managers": {name: {...}}}`)
8. **Integración tablerofutbolero.es**: fetch de clasificaciones reales para comparar con simuladas

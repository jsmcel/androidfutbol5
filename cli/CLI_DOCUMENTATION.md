# CLI PC FÃºtbol 5 â€” DocumentaciÃ³n y Resultados QA

**Archivo**: `cli/pcfutbol_cli.py` (1803 lÃ­neas)
**Python**: 3.9+ (stdlib solo, sin dependencias externas)
**Datos**: `android/core/data/src/main/assets/` (CSV + JSON)
**Fecha QA**: 2026-02-28

---

## MenÃº principal

```
â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—
â•‘    PC FÃšTBOL 5  Â·  CLI  2025/26  â•‘
â•‘      Temporada real â€” Python      â•‘
â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

âœ“ 20 equipos en Primera DivisiÃ³n
âœ“ 22 equipos en Segunda DivisiÃ³n
âœ“ 1092 jugadores cargados

  1. Simular temporada completa
  2. Ver plantilla de equipo
  3. Partido rÃ¡pido
  4. Top jugadores
  5. Ranking de fortaleza
  6. PRO MANAGER (modo carrera)
  0. Salir
```

---

## Opciones del menÃº principal

### 0 â€” Salir
âœ… Funciona. Muestra "Â¡Hasta la prÃ³xima temporada!".

---

### 1 â€” Simular temporada completa
âœ… Funciona. Simula las 38 jornadas de Liga1 y 42 de Liga2.

**Flujo:**
- Selecciona competiciÃ³n (1=Primera, 2=Segunda)
- Simula todos los partidos con seed aleatoria
- Muestra clasificaciÃ³n final con campeÃ³n, relegados y ascendidos

**Resultado de test:**
```
ðŸ† CAMPEÃ“N PRIMERA DIVISIÃ“N: Real Oviedo (59 pts)
  Real Madrid:   10Âº (52 pts)
  FC Barcelona:  17Âº (45 pts)

ðŸ”» DESCENSOS: Real Madrid B, Deportivo AlavÃ©s, UD Las Palmas
ðŸ”º ASCENSOS: SD Eibar, Granada CF, CD MirandÃ©s
```

**âš ï¸ BUG 1 â€” CALIBRACIÃ“N DE FUERZA DEMASIADO PLANA:**
Todos los equipos de Liga1 tienen una fuerza en el rango 48-52 puntos.
La diferencia entre el mejor (BarÃ§a/Madrid ~52) y el peor (~48) es de solo 4 puntos.
Esto genera resultados poco realistas donde equipos modestos (Real Oviedo, LeganÃ©s)
pueden ganar la liga fÃ¡cilmente. La distribuciÃ³n Poisson no refleja la superioridad real
de los grandes equipos.

---

### 2 â€” Ver plantilla de equipo
âœ… Funciona correctamente.

**Flujo:**
- Lista los 42 equipos (20 Liga1 + 22 Liga2) numerados
- Selecciona equipo por nÃºmero
- Muestra tabla con: #, JUGADOR, POS, ED, ME, VE, RE, CA, VALOR

**Resultado de test (CD MirandÃ©s):**
```
#   JUGADOR          POS                  ED  ME  VE  RE  CA VALOR
1   Thiago Helguera  Defensive Midfield   19  69  70  71  74 3M
2   AdriÃ¡n Pica      Centre-Back          23  68  63  72  62 1M
...
27  Juanpa Palomares Goalkeeper           25  57  44  61  60 300K
```

---

### 3 â€” Partido rÃ¡pido
âœ… Funciona para equipos distintos.

**Flujo:**
- Muestra lista de 42 equipos
- Selecciona equipo local y visitante por nÃºmero
- Simula el partido y muestra resultado

**Resultado de test:**
```
Real Madrid 2-0 FC Barcelona
```

**âš ï¸ BUG 2 â€” MISMO EQUIPO EN AMBOS ROLES:**
Si el usuario elige el mismo equipo (mismo nÃºmero) para local y visitante:
```
AD Ceuta FC 3-3 AD Ceuta FC
```
No hay validaciÃ³n que impida `local == visitante`. El partido se simula igualmente,
dando lugar a resultados absurdos (un equipo contra sÃ­ mismo).

**Fix sugerido**: aÃ±adir validaciÃ³n `if away_idx == home_idx: print("Error: elige equipos distintos")` en `menu_quick_match`.

---

### 4 â€” Top jugadores
âœ… Funciona. Permite filtrar por liga, posiciÃ³n y cantidad.

**Flujo:**
- Selecciona competiciÃ³n (1=Primera, 2=Segunda, 3=Ambas)
- Selecciona posiciÃ³n (1=Todas, 2=Porteros, 3=Defensas, 4=Medios, 5=Delanteros)
- Selecciona cuÃ¡ntos mostrar (5-50)
- Muestra ranking por media (ME)

**Resultado de test (top 20 de Primera, todas posiciones):**
```
1   Pedri                Central Midfield      26  93  ...
2   Lamine Yamal         Right Winger          17  91  ...
3   Fede Valverde        Central Midfield      26  91  ...
4   Jude Bellingham      Central Midfield      21  91  ...
5   VinÃ­cius JÃºnior      Left Winger           24  90  ...
6   Kylian MbappÃ©        Centre-Forward        26  90  ...
...
```

---

### 5 â€” Ranking de fortaleza
âœ… Funciona. Muestra los 42 equipos ordenados por fuerza compuesta.

**Muestra:** slot_id, nombre, comp, fortaleza, posiciÃ³n en ranking.

---

### 6 â€” PRO MANAGER (modo carrera)
Sistema de modo carrera con persistencia en `~/.pcfutbol_career.json`.

#### Sub-menÃº de temporada (dentro del ProManager)
```
1. Simular jornada {N}
2. ClasificaciÃ³n completa
3. Plantilla
4. Noticias
5. Simular resto de temporada
6. Mercado de fichajes [ABIERTO/CERRADO]
7. TÃ¡ctica
0. Guardar y salir
```

**Funciona:**
- âœ… Crear nuevo manager con nombre
- âœ… Generar ofertas segÃºn prestigio (prestige=1 â†’ bottom 12 de Liga2)
- âœ… Ver clasificaciÃ³n (opciÃ³n 2)
- âœ… Ver plantilla (opciÃ³n 3)
- âœ… Ver noticias (opciÃ³n 4) â€” con item de inicio de temporada
- âœ… Simular jornada en modo automÃ¡tico (opciÃ³n 1 â†’ modo 1)
- âœ… Mercado de fichajes (opciÃ³n 6):
  - âœ… Buscar y fichar (con filtro por posiciÃ³n y presupuesto)
  - âœ… Vender jugador (con 60% de valor de mercado)
  - âœ… Ver plantilla desde el mercado
- âœ… Guardar y salir
- âœ… Continuar partida guardada
- âœ… Fin de temporada con pantalla de resultados
- âœ… Nueva temporada con nuevas ofertas y prestigio actualizado
- âœ… Modo Entrenador (animaciÃ³n en tiempo real): presupuesto de partida, VAR, lesiones, sustituciones

**âš ï¸ BUG 3 â€” CRÃTICO: OPCIÃ“N 7 (TÃCTICA) NO IMPLEMENTADA EN EL SEASON LOOP:**
La opciÃ³n 7 (TÃ¡ctica) aparece en el menÃº del season loop pero el handler
`elif op == 7:` estÃ¡ AUSENTE en el cÃ³digo. Al seleccionar 7, el bucle continÃºa
sin hacer nada, como si se hubiera pulsado una tecla invÃ¡lida.

Esto provoca que cambiar la tÃ¡ctica sea IMPOSIBLE desde el ProManager.
El `_tactic_menu(data)` sÃ­ existe y estÃ¡ implementado pero nunca se llama.

**Fix**: aÃ±adir en `_season_loop` (tras `elif op == 6:`):
```python
elif op == 7:
    _tactic_menu(data)
    tactic = data.get("tactic", tactic)  # refrescar referencia
```

**âš ï¸ BUG 4 â€” TÃCTICA NO SE REFLEJA EN EL RESUMEN DE LA PANTALLA:**
Aunque se corrigiera el bug 3, la lÃ­nea `tactic = data.setdefault("tactic", ...)` se
ejecuta al inicio de cada iteraciÃ³n del while loop. Si `_tactic_menu` modifica
`data["tactic"]` en el mismo objeto (`t = data.setdefault(...)`), los cambios SÃ
persisten (mismo objeto por referencia). Sin embargo, la variable `tactic` en el
scope del loop apunta al mismo dict, por lo que deberÃ­a actualizarse automÃ¡ticamente.

---

## Modo Entrenador (_match_entrenador)
âœ… Funciona correctamente. CaracterÃ­sticas verificadas en cÃ³digo:

| Feature | Estado |
|---|---|
| AnimaciÃ³n minuto a minuto (90') | âœ… |
| Tiempo aÃ±adido dinÃ¡mico (1Âª y 2Âª parte) | âœ… |
| VAR (18% de los goles se revisan, 38% se anulan) | âœ… |
| Tarjetas amarillas (2-5 por partido) | âœ… |
| PÃ©rdida de tiempo (+amarillas si tÃ¡ctica activa) | âœ… |
| Golpe en la cabeza / 6Âª sustituciÃ³n de concusiÃ³n (30%) | âœ… |
| 5 cambios / 3 ventanas en juego | âœ… |
| Ventana de descanso (gratis, no consume ventana) | âœ… |
| Cambio de tÃ¡ctica en parada (T durante partido) | âœ… |
| Marcador actualizado con nombres abreviados | âœ… |
| Vista de porterÃ­a con balÃ³n (zona 0-4) | âœ… |

**Nota**: el modo entrenador usa `time.sleep(0.13)` por minuto â†’ ~12-20 segundos
por partido. En piped stdin, funciona pero los inputs deben suministrarse previamente.

---

## Flujo ProManager completo (test real)

**Manager**: Jose | **Equipo**: CD MirandÃ©s | **Liga**: Segunda DivisiÃ³n

| Jornada | Resultado | PosiciÃ³n | Noticias |
|---|---|---|---|
| 1 | Burgos CF 2 - **3** CD MirandÃ©s âœ… | 6Âº (3 pts) | Victoria a domicilio (3-2). Equipo en puesto 6. |
| 2 | **CD MirandÃ©s 1 - 2** SD Eibar âŒ | 10Âº (3 pts) | Derrota en casa ante SD Eibar (1-2). PosiciÃ³n 10. |
| 3 | FC Andorra vs CD MirandÃ©s | (Modo Entrenador lanzado) | â€” |

---

## Bugs crÃ­ticos â€” Resumen

| # | DescripciÃ³n | Severidad | FunciÃ³n afectada |
|---|---|---|---|
| 1 | CalibraciÃ³n plana: todos los equipos ~47-52 fuerza; Madrid/BarÃ§a pueden no ganar Liga | ALTO | `team_strength()` / simulador Poisson |
| 2 | Sin validaciÃ³n de equipo local==visitante en partido rÃ¡pido | MEDIO | `menu_quick_match()` |
| 3 | OpciÃ³n 7 (TÃ¡ctica) en season loop no tiene handler â€” tÃ¡ctica imposible de cambiar | CRÃTICO | `_season_loop()` |
| 4 | La tÃ¡ctica definida en PreManager se aplica pero nunca se puede actualizar vÃ­a UI | ALTO | `_season_loop()`, `_tactic_menu()` |

---

## Arquitectura del simulador

```
Team.strength() = promedio ponderado de atributos de jugadores
  â”œâ”€â”€ attack_strength() = remateÃ—0.35 + regateÃ—0.25 + tiroÃ—0.2 + paseÃ—0.1 + veÃ—0.1
  â”œâ”€â”€ defense_strength() = entradaÃ—0.4 + reÃ—0.25 + agÃ—0.2 + caÃ—0.15
  â””â”€â”€ gk_strength() = porteroÃ—0.6 + reÃ—0.2 + agÃ—0.1 + caÃ—0.1

simulate_match(home, away, seed):
  â”œâ”€â”€ strengthToLambda(): convierte fuerza â†’ parÃ¡metro Poisson
  â”œâ”€â”€ home_advantage: +12% Î»
  â””â”€â”€ poissonSample(): genera goles con distribuciÃ³n Poisson
```

**Problema de calibraciÃ³n**: la fuerza de todos los equipos estÃ¡ en el rango 47-52.
`strengthToLambda` produce Î» muy similares para todos â†’ partidos poco predecibles.
La soluciÃ³n es escalar la fuerza con una curva exponencial o percentiles.

---

## CÃ³mo ejecutar el CLI

```bash
cd /path/to/pcfutbol
python cli/pcfutbol_cli.py

# Test no interactivo (ejemplo):
printf "3\n29\n18\n0\n" | python cli/pcfutbol_cli.py
#   ^ partido rÃ¡pido: Real Madrid (29) vs FC Barcelona (18), luego salir
```

---

## Datos cargados

- **Equipos**: `pcf55_teams_extracted.json` â€” 260 slots (20 Liga1 + 22 Liga2 + resto)
- **Jugadores**: `pcf55_players_2526.csv` â€” 1092 jugadores de Liga1+Liga2
  - Rango de plantillas: 23-30 jugadores por equipo (media 26)
- **Save de carrera**: `~/.pcfutbol_career.json` (JSON en home del usuario)

---

## Ideas para futuras mejoras

1. **CalibraciÃ³n de fuerza**: usar percentiles o escala exponencial para separar Madrid/BarÃ§a del resto
2. **ValidaciÃ³n localâ‰ visitante**: check simple en `menu_quick_match`
3. **Implementar handler de tÃ¡ctica** en `_season_loop` (1 lÃ­nea)
4. **EvoluciÃ³n de jugadores**: subida/bajada de atributos por temporada (jÃ³venes mejoran, veteranos bajan)
5. **Cantera / juveniles**: jugadores de 16-18 aÃ±os con potencial oculto
6. **Retiradas automÃ¡ticas**: jugadores >35 aÃ±os con probabilidad de retiro al final de temporada
7. **Multi-manager**: guardar N managers simultÃ¡neos (estructura `{"managers": {name: {...}}}`)
8. **IntegraciÃ³n tablerofutbolero.es**: fetch de clasificaciones reales para comparar con simuladas


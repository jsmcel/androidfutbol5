# CODEX TASK — Mejoras para convertir el CLI en un juego top

## Contexto

Se hizo QA de una temporada completa ProManager en `cli/pcfutbol_cli.py`.
El resultado base es funcional: carga datos, simula 42 jornadas, fin de temporada correcto.

Bugs encontrados en el QA:
1. Menú principal: opciones 7 (MULTIJUGADOR) y 8 (Actualidad) aparecen fuera del recuadro ║ ║
2. Objectives mal calibrados: Burgos CF ganó la liga (1º/22) pero tenía objetivo "EVITAR DESCENSO"
3. ASSETS_DIR ya corregido (parent.parent en vez de parent.parent.parent)

## Mejoras a implementar (prioridad alta → baja)

---

### 1. FIX: Menú principal — opciones 7 y 8 fuera del recuadro [FÁCIL]

En `main_menu()`, las opciones 7 y 8 se imprimen sin el formato de caja.
Deben quedar igual que 1-6:

```python
print(_c(BOLD + CYAN, "  │  7. MULTIJUGADOR por turnos     │"))
print(_c(BOLD + CYAN, "  │  8. Actualidad futbolística      │"))
```

---

### 2. FIX: Calibración de objectives [FÁCIL]

En `_setup_season()`, el objetivo se asigna basado en la fuerza del equipo vs la liga.
Actualmente Burgos CF (fuerza 48.1 en una liga cuya media es ~50) recibe "EVITAR DESCENSO".
Burgos acabó 1º — el objetivo debería haber sido "CLASIFICACION TOP 10".

Fix: calcular el ranking de fuerza del equipo dentro de su competición:
- Top 3 equipos → "GANAR LA LIGA"
- Top 4-10 → "CLASIFICACION TOP 10"
- Top 11-16 → "EVITAR EL DESCENSO"
- Bottom 6 (Liga de 22) / Bottom 4 (Liga de 20) → "EVITAR EL DESCENSO DIRECTO"

---

### 3. MEJORA: Segunda temporada en ProManager — loop infinito [MEDIO]

Actualmente después del fin de temporada el juego vuelve al menú principal
sin ofrecer continuar la carrera. Implementar:

- Después de `FIN DE TEMPORADA`, preguntar: "1. Continuar carrera  2. Salir"
- Si continua: generar nuevas ofertas según el prestige actualizado
  - Si ascendió a Primera → equipos de Primera en las ofertas (prestige ≥ 2)
  - Si siguió en Segunda → equipos similares + alguno mejor
- Persistir la carrera en `~/.pcfutbol_career.json` y cargarla al volver al menú

---

### 4. MEJORA: Jornada a jornada real + mercado de invierno [MEDIO]

Ahora "Simular resto" simula las 42 jornadas sin interacción.
Para hacer el juego más interesante:

- En jornada 21 (mitad de temporada), parar automáticamente y mostrar:
  ```
  ═══ VENTANA DE MERCADO DE INVIERNO ═══
  ¿Quieres fichar algún jugador? (S/N)
  ```
- Si S: mostrar `menu_market()` básico (3 jugadores disponibles aleatorios de la liga)
- Continuar simulando jornadas 22-42

---

### 5. MEJORA: Noticias dinámicas entre jornadas [MEDIO]

Al simular cada jornada, generar 1-2 noticias automáticas:
- "Tu delantero X ha marcado 2 goles — forma al máximo"
- "Lesión de Y — baja 3 semanas"
- "La junta está satisfecha con tu rendimiento (pos 3ª)"
- "Rival Z acaba de fichar a W por 2M€"

Mostrar las noticias más recientes (últimas 5) cuando el usuario elige opción 4 "Noticias".

---

### 6. MEJORA: Copa del Rey en CLI [DIFÍCIL]

Añadir opción 8.5 o integrar en el loop de temporada:
- Copa del Rey: 16 equipos (8 Primera + 8 Segunda seleccionados por fuerza)
- Eliminatoria simple: R16 → QF → SF → F
- Se juega entre jornadas (MD 10, MD 20, MD 30, MD 38)
- Mostrar ronda activa cuando el usuario está en el loop de temporada

---

### 7. MEJORA: Desarrollo de jugadores visible [FÁCIL]

Al final de temporada (ya existe `_apply_season_development`), mostrar:
```
═══ EVOLUCIÓN DE TU PLANTILLA ═══
  MEJORAN:  García (+3 ME), López (+2 VE)
  BAJAN:    Rodríguez (-2 VE) — veterano 33 años
  RETIRAN:  Fernández — 37 años, se retira
  CANTERANOS: 2 jóvenes (17 años) suben al primer equipo
```

---

### 8. MEJORA: Stats acumuladas de carrera [FÁCIL]

En `history` del manager ya se guarda cada temporada.
Mostrar al entrar al juego (si hay carrera guardada):

```
═══ RESUMEN DE CARRERA ═══
Manager: CODEX_QA  |  Temporadas: 3  |  Prestigio: ★★★☆☆
  2025-26: Burgos CF   2ª  Pos 1  ✓ ascenso
  2026-27: Getafe CF   1ª  Pos 8  ✓ objetivo
  2027-28: Betis       1ª  Pos 4  ✓ objetivo
```

---

## Lo que NO tocar

- El simulador Poisson (funciona bien)
- La carga de datos CSV/JSON
- `menu_real_football()` (recién implementado)
- El sistema de guardado/carga de carrera

## Verificación

Después de cada cambio, ejecutar:
```bash
cd androidfutbol/cli
python qa_runner.py
```
Y verificar `season_completed=True` y sin errores.

También ejecutar manualmente una temporada completa para confirmar que el menú
visualmente se ve correcto y las nuevas features funcionan.

---

### 9. MEJORA CRÍTICA: Pirámide de ligas completa — 1ª RFEF como nivel inicial [MEDIO]

El usuario quiere que un manager nuevo (prestige 1) empiece recibiendo ofertas de 1ª RFEF,
no de Segunda División directamente. La pirámide sería:

```
Prestige 1  → ofertas de 1ª RFEF (LIGA2B / LIGA2B2)
Prestige 2  → ofertas de Segunda División (LIGA2)
Prestige 3+ → ofertas de Primera División (LIGA1)
```

Para implementar:
1. En `load_teams()`, también cargar equipos de LIGA2B si están en el CSV
   (buscar competition == "E3G1" o "E3G2" en el CSV, o usar los equipos sintéticos de SyntheticLeagues)
2. Si no hay datos reales de 1ª RFEF en el CSV, generar 20 equipos sintéticos con fuerza ~42-45
3. En `_show_offers()`, añadir lógica: `if prestige == 1 → ligas_rfef; elif prestige == 2 → liga2; else → liga1`
4. La 1ª RFEF también tiene 38 jornadas (grupo de 20 equipos, doble vuelta)
5. Al ascender de RFEF → Segunda División, el prestige sube y las siguientes ofertas son de LIGA2

Los equipos de 1ª RFEF en el juego son los definidos en SyntheticLeagues.RFEF1A y RFEF1B
(son grupos). Para el CLI, bastaría con un grupo (20 equipos, Liga normal sin grupos).

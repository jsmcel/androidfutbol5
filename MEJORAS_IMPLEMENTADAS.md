# MEJORAS IMPLEMENTADAS

Fecha: 2026-02-28

## Archivos modificados
- `cli/pcfutbol_cli.py`
- `android/cli/pcfutbol_cli.py`

## Mejoras FÁCILES
1. Menú principal (fix 1)
- Se corrigió el formato de caja para las opciones 7 y 8.
- `cli/pcfutbol_cli.py`: se añadió además una opción 7 funcional (mensaje de disponibilidad de multijugador en la variante Android CLI) para evitar opción muda.
- `android/cli/pcfutbol_cli.py`: opción 8 ahora también queda dentro del recuadro.

2. Calibración de objetivos (fix 2)
- `_assign_objective()` ahora usa ranking de fuerza real dentro de su competición.
- Reglas aplicadas:
- Top 3: `GANAR LA LIGA`
- Top 4-10: `CLASIFICACION TOP 10`
- Top 11-16: `EVITAR EL DESCENSO`
- Zona baja: `EVITAR EL DESCENSO DIRECTO` (bottom 6 en ligas de 22; bottom 4 en ligas de 20)
- `_check_objective()` actualizado para evaluar correctamente los nuevos objetivos.

7. Desarrollo visible de jugadores
- Se mostró al final de temporada el bloque `EVOLUCION DE TU PLANTILLA`.
- Se añadieron/ajustaron snapshots de jugadores para calcular cambios estacionales.
- Se informa de:
- Mejoras
- Bajadas (incluyendo nota de veterano cuando aplica)
- Retiros
- Subida de canteranos

8. Estadísticas acumuladas de carrera
- Se añadió `RESUMEN DE CARRERA` al entrar en ProManager si existe historial guardado.
- Muestra manager, temporadas, prestigio y resumen de temporadas recientes con resultado/objetivo.

## Mejoras MEDIAS
3. Segunda temporada con continuidad inmediata
- Tras `FIN DE TEMPORADA` se pregunta:
- `1. Continuar carrera`
- `2. Salir al menu principal`
- Si se continúa:
- Se generan nuevas ofertas según el estado de carrera (incluido caso de ascenso desde Segunda).
- Se configura automáticamente la siguiente temporada.
- Se preserva guardado en `~/.pcfutbol_career.json`.

4. Mercado de invierno en mitad de temporada
- En opción `Simular resto de temporada`, se introduce parada automática en mitad de temporada.
- Se muestra ventana de invierno y pregunta `S/N`.
- Si se acepta, se abre mini-mercado básico con 3 candidatos aleatorios de la liga, compra opcional y actualización de presupuesto/plantilla.

5. Noticias dinámicas entre jornadas
- Se añadieron 1-2 noticias por jornada (en simulación jornada a jornada y también en `Simular resto`).
- Se añadieron noticias de rendimiento, lesiones, junta y fichajes de rivales.
- Opción 4 `Noticias` ahora muestra las últimas 5 entradas.

## Mejora DIFÍCIL
6. Copa del Rey en CLI
- Implementación integrada en el loop de temporada.
- Formato: 16 equipos (8 Primera + 8 Segunda por fuerza), eliminación directa.
- Rondas: OCTAVOS, CUARTOS, SEMIFINAL, FINAL.
- Matchdays de Copa: 10, 20, 30, 38.
- Se muestra ronda activa durante la temporada.
- Se simulan partidos de Copa en jornada y en `Simular resto`.
- Se publican noticias de progreso/eliminación/campeón.

## Ajuste adicional aplicado durante QA
- En `cli/pcfutbol_cli.py`, el flujo ya incluía 1ª RFEF (`E3G1/E3G2`) y ofertas iniciales en RFEF.
- Se corrigió `_season_loop()` para soportar correctamente competiciones RFEF y evitar `KeyError` al iniciar temporada con equipos RFEF.

## Verificación
Se ejecutó `python cli/qa_runner.py` tras cada mejora solicitada.

Resultado final:
- `season_completed=True`
- `exit_code=0`
- Último log válido: `cli/qa_outputs/20260228_183242_cli_root.stdout.log`

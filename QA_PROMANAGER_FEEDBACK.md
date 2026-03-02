# QA ProManager Feedback

> NOTA DE VIGENCIA (2026-03-02): este QA recoge un estado anterior con dos rutas CLI. Actualmente solo existe cli/pcfutbol_cli.py.

Fecha de QA: 2026-02-28  
Runner usado: `cli/qa_runner.py`  
Logs analizados completos:
- `cli/qa_outputs/20260228_181702_cli_root.stdout.log`
- `cli/qa_outputs/20260228_181702_android_cli.stdout.log`
- `cli/qa_outputs/20260228_181702_android_cli.stderr.log` (vacio)

## 1) Â¿Arranco correctamente? Â¿Cargo datos sin errores?

- `cli/pcfutbol_cli.py` (ejecucion desde `cli/`) **falla** por ruta de assets:
  - Error exacto: `[ERROR] No se encuentra el CSV: C:\Users\Jose-Firebat\proyectos\android\core\data\src\main\assets\pcf55_players_2526.csv`
- `android/cli/pcfutbol_cli.py` **arranca bien**:
  - `âœ“ 20 equipos en Primera Division`
  - `âœ“ 22 equipos en Segunda Division`
  - `âœ“ 1092 jugadores cargados`

## 2) Â¿Se creo el manager? Â¿Aparecio la lista de ofertas?

- **Si**.
- Se creo `CODEX_QA`.
- Se mostro pantalla `SELECCION DE OFERTA`.

## 3) Â¿Cuantas ofertas habia? Â¿>=3 equipos disponibles?

- Habia **4 ofertas**:
  - FC Andorra
  - Cordoba CF
  - Real Zaragoza
  - Real Sociedad B
- Cumple `>=3`.

## 4) Â¿La simulacion de temporada funciono? Â¿Mostro clasificacion final?

- **Si**.
- Se ejecuto `Simular resto de temporada`.
- Aparecio `âœ“ Temporada completada.`
- Se mostro `FIN DE TEMPORADA` y la tabla final completa de Segunda Division (22 equipos).

## 5) Â¿Se calculo el fin de temporada? Â¿Prestige +/-1? Â¿Objetivo mostrado?

- **Si**.
- Objetivo mostrado: `EVITAR EL DESCENSO`.
- Resultado manager: `Posicion: 10Âº / 22` con objetivo cumplido.
- Cambio de prestigio aplicado: `â˜…â˜†â˜†â˜†â˜† -> â˜…â˜…â˜†â˜†â˜†` (**+1**).

## 6) Bugs encontrados

1. **Ruta de assets rota en `cli/pcfutbol_cli.py` (cuando se ejecuta desde `cli/`)**  
   Impacto: no se puede iniciar el juego en esa variante del CLI.  
   Evidencia exacta:
   - `[ERROR] No se encuentra el CSV: C:\Users\Jose-Firebat\proyectos\android\core\data\src\main\assets\pcf55_players_2526.csv`

2. **Problema de codificacion/mojibake en textos del CLI**  
   Impacto: interfaz degradada y lectura confusa en menu/salidas.  
   Evidencia visible en salida:
   - `PC FÃƒÅ¡TBOL`, `OpciÃƒÂ³n`, `SELECCIÃƒâ€œN`, caracteres de cajas corruptos.

## 7) UX issues

1. **Flujo de alta de manager confuso**  
   Tras crear nombre, se vuelve a imprimir `MODO PRO MANAGER` y aparece un estado intermedio `2025-26 Â· ? Â· J1` antes de elegir equipo.

2. **Inconsistencia visual del menu principal**  
   La opcion `8. Actualidad futbolistica` queda fuera del marco ASCII del menu.

3. **Simulacion larga sin feedback de progreso**  
   `Simular resto de temporada` pasa directo de inicio a fin sin avance intermedio por jornadas.

4. **Copy/UI inconsistente**  
   Mezcla de mayusculas, acentos perdidos y textos poco pulidos (`Division`, `futbolistica`, etc.).

## 8) Top 3 mejoras propuestas

1. **Resolver rutas de assets de forma robusta y unica**
   - Buscar assets relativo al repositorio real, no a una profundidad fija de `parent.parent.parent`.
   - Soportar ambas entradas (`cli/` y `android/cli/`) sin romper.

2. **Normalizar codificacion UTF-8 end-to-end**
   - Corregir cadenas fuente y salida de consola para eliminar mojibake.
   - Revisar archivos Python y terminal para que no se mezclen codificaciones.

3. **Mejorar onboarding + feedback de simulacion**
   - Flujo directo: crear manager -> elegir oferta (sin estado `?` intermedio).
   - Mostrar progreso por bloques (ej. jornada 1-10-20-30-42) durante simulacion total.
   - Cerrar temporada con CTA clara (`Nueva temporada`, `Cambiar equipo`, `Salir`).




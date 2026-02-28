# CODEX QA TASK — Jugar 1 temporada ProManager en CLI y reportar feedback

## Objetivo

Ejecutar el CLI (`cli/pcfutbol_cli.py`) en modo ProManager, jugar UNA temporada completa
(Liga completa, jornada a jornada o "simular resto"), y reportar feedback detallado.

## Cómo ejecutar

```bash
cd /c/Users/Jose-Firebat/proyectos/androidfutbol
python cli/pcfutbol_cli.py
```

O si falla la ruta de assets:
```bash
cd /c/Users/Jose-Firebat/proyectos/androidfutbol/android/cli
python pcfutbol_cli.py
```

## Pasos a seguir (modo no-TTY / automatizado)

Como estás en modo automático sin TTY interactivo, usa este script Python para simular inputs:

```python
# qa_promanager_runner.py — ejecutar en el directorio del CLI
import subprocess, sys

inputs = "\n".join([
    "6",          # menú principal → PRO MANAGER
    "1",          # Nuevo manager (o login si ya existe)
    "CODEX_QA",   # nombre del manager
    "1",          # Aceptar primera oferta (equipo más débil)
    "3",          # Simular resto de temporada (en el loop de jornadas)
    "0",          # Volver / confirmar fin
    "0",          # Salir
])

result = subprocess.run(
    [sys.executable, "pcfutbol_cli.py"],
    input=inputs,
    capture_output=True,
    text=True,
    timeout=120,
    cwd=".",
)
print("=== STDOUT ===")
print(result.stdout[-8000:])  # últimas 8000 chars
print("=== STDERR ===")
print(result.stderr[-2000:])
print("=== EXIT CODE ===", result.returncode)
```

Guarda ese script como `qa_runner.py` en `cli/`, ejecútalo, y analiza la salida.

## Qué reportar

Escribe el feedback en `QA_PROMANAGER_FEEDBACK.md` en la raíz del proyecto con:

1. **¿Arrancó correctamente?** — ¿Cargó datos sin errores?
2. **¿Se creó el manager?** — ¿Apareció la lista de ofertas?
3. **¿Cuántas ofertas había?** — ¿≥3 equipos disponibles?
4. **¿La simulación de temporada funcionó?** — ¿Mostró clasificación final?
5. **¿Se calculó el fin de temporada?** — ¿Prestige +/-1? ¿Objetivo mostrado?
6. **Bugs encontrados** — con texto exacto del error si los hay
7. **UX issues** — cosas confusas o que faltan
8. **Propuesta de mejoras** — top 3 cambios que mejorarían la experiencia

## Notas

- Si el script da `FileNotFoundError` para assets, prueba desde `android/cli/` en vez de `cli/`
- Si hay `input_int` que no acepta el valor, ajusta los inputs en el script
- El archivo de carrera se guarda en `~/.pcfutbol_career.json` — bórralo antes de empezar para empezar limpio
- NO modifiques el código del CLI durante este QA — solo observa y reporta

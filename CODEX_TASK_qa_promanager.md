# CODEX QA TASK â€” Jugar 1 temporada ProManager en CLI y reportar feedback

## Objetivo

Ejecutar el CLI (`cli/pcfutbol_cli.py`) en modo ProManager, jugar UNA temporada completa
(Liga completa, jornada a jornada o "simular resto"), y reportar feedback detallado.

## CÃ³mo ejecutar

```bash
cd /c/Users/Jose-Firebat/proyectos/androidfutbol
python cli/pcfutbol_cli.py
```

## Pasos a seguir (modo no-TTY / automatizado)

Como estÃ¡s en modo automÃ¡tico sin TTY interactivo, usa este script Python para simular inputs:

```python
# qa_promanager_runner.py â€” ejecutar en el directorio del CLI
import subprocess, sys

inputs = "\n".join([
    "6",          # menÃº principal â†’ PRO MANAGER
    "1",          # Nuevo manager (o login si ya existe)
    "CODEX_QA",   # nombre del manager
    "1",          # Aceptar primera oferta (equipo mÃ¡s dÃ©bil)
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
print(result.stdout[-8000:])  # Ãºltimas 8000 chars
print("=== STDERR ===")
print(result.stderr[-2000:])
print("=== EXIT CODE ===", result.returncode)
```

Guarda ese script como `qa_runner.py` en `cli/`, ejecÃºtalo, y analiza la salida.

## QuÃ© reportar

Escribe el feedback en `QA_PROMANAGER_FEEDBACK.md` en la raÃ­z del proyecto con:

1. **Â¿ArrancÃ³ correctamente?** â€” Â¿CargÃ³ datos sin errores?
2. **Â¿Se creÃ³ el manager?** â€” Â¿ApareciÃ³ la lista de ofertas?
3. **Â¿CuÃ¡ntas ofertas habÃ­a?** â€” Â¿â‰¥3 equipos disponibles?
4. **Â¿La simulaciÃ³n de temporada funcionÃ³?** â€” Â¿MostrÃ³ clasificaciÃ³n final?
5. **Â¿Se calculÃ³ el fin de temporada?** â€” Â¿Prestige +/-1? Â¿Objetivo mostrado?
6. **Bugs encontrados** â€” con texto exacto del error si los hay
7. **UX issues** â€” cosas confusas o que faltan
8. **Propuesta de mejoras** â€” top 3 cambios que mejorarÃ­an la experiencia

## Notas

- Si aparece `FileNotFoundError` de assets, hay regresión de rutas en `cli/pcfutbol_cli.py` y debe corregirse en ese único CLI.
- Si hay `input_int` que no acepta el valor, ajusta los inputs en el script
- El archivo de carrera se guarda en `~/.pcfutbol_career.json` â€” bÃ³rralo antes de empezar para empezar limpio
- NO modifiques el cÃ³digo del CLI durante este QA â€” solo observa y reporta


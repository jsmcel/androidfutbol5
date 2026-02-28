# Tarea Codex: QA completo con dispositivo real vía ADB

## Contexto
Hay un dispositivo Android conectado por ADB. Tu misión es compilar la app,
instalarla, ejecutar las pruebas automáticas y hacer QA exploratorio completo
reportando todos los errores que encuentres.

## PASO 0 — Verificar entorno

```bash
adb devices                          # verificar dispositivo conectado
cd android/                          # directorio del proyecto
./gradlew --version                  # verificar Gradle
```

Si `adb devices` no muestra dispositivo, para y reporta el error.

---

## PASO 1 — Compilar y ejecutar tests unitarios

```bash
./gradlew :match-sim:test            # tests del simulador (críticos)
./gradlew :competition-engine:test   # tests del motor de liga
./gradlew assembleDebug              # compilar APK debug
```

Si la compilación falla, **lee el error completo**, identifica el archivo y línea,
corrígelo, y vuelve a compilar. Repite hasta que compile sin errores.

Errores esperados a corregir:
- Imports de `QuinielaEntity`/`QuinielaDao` que puedan haber quedado en algún archivo
  (el usuario eliminó Proquinielas — busca referencias huérfanas con `grep -r "Quiniela" --include="*.kt"`)
- `onProquinielas` en algún ViewModel o archivo que no se limpió
- Cualquier otro error de compilación que encuentres

---

## PASO 2 — Instalar en dispositivo

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O usar Gradle directamente:
```bash
./gradlew installDebug
```

---

## PASO 3 — Limpiar logcat y lanzar la app

```bash
adb logcat -c                        # limpiar log anterior
adb shell am start -n com.pcfutbol.app/.MainActivity
adb logcat -s "AndroidRuntime" "pcfutbol" "SeedLoader" "CompetitionRepository" "FATAL" 2>&1 | head -200
```

Busca en el log:
- `FATAL EXCEPTION` — crash
- `Room` errors — migración de BD fallida
- `SeedLoader` — si el seed de datos funcionó correctamente
- `NullPointerException`, `IllegalStateException`

---

## PASO 4 — QA exploratorio automatizado con UI Automator

Ejecuta los siguientes flujos usando `adb shell input` para simular toques:

### Flujo 1: Menú principal
```bash
# Esperar que cargue
sleep 3
# Screenshot inicial
adb shell screencap -p /sdcard/qa_01_menu.png
adb pull /sdcard/qa_01_menu.png qa_screenshots/
```

### Flujo 2: Liga/Manager
```bash
# Tap en "LIGA / MANAGER" (aproximadamente centro pantalla, 60% altura)
# Nota: las coordenadas dependen de la resolución del dispositivo
# Primero obtener resolución:
adb shell wm size
# Luego calcular coordenadas y hacer tap
adb shell input tap [X] [Y]
sleep 2
adb shell screencap -p /sdcard/qa_02_liga.png
adb pull /sdcard/qa_02_liga.png qa_screenshots/
```

### Para obtener coordenadas exactas, usa UIAutomator dump:
```bash
adb shell uiautomator dump /sdcard/ui_dump.xml
adb pull /sdcard/ui_dump.xml
# Parsea el XML para encontrar los bounds de cada botón
grep -A2 "LIGA\|MANAGER\|PLANTILLA\|JORNADA" ui_dump.xml | head -40
```

---

## PASO 5 — Ejecutar tests instrumentados (si existen)

```bash
./gradlew connectedAndroidTest 2>&1 | tail -50
```

---

## PASO 6 — Informe de QA

Al finalizar, crea el archivo `QA_REPORT.md` en `android/` con:

```markdown
# QA Report — [fecha]

## Resultado de compilación
- Tests unitarios: PASS/FAIL (N/M)
- Errores de compilación encontrados y corregidos: [lista]

## Errores en runtime (logcat)
- [lista de crashes/errores encontrados]

## Flujos probados
| Flujo | Resultado | Nota |
|---|---|---|
| Menú principal carga | ✅/❌ | |
| Liga/Manager abre | ✅/❌ | |
| Clasificación muestra datos | ✅/❌ | |
| Jornada simula partido | ✅/❌ | |
| Plantilla muestra jugadores | ✅/❌ | |
| Mercado de fichajes | ✅/❌ | |
| Copa del Rey | ✅/❌ | |
| Selección Nacional | ✅/❌ | |
| ProManager crea manager | ✅/❌ | |
| Fin de temporada | ✅/❌ | |

## Screenshots tomados
[lista de archivos en qa_screenshots/]

## Bugs encontrados
| # | Descripción | Severidad | Archivo sospechoso |
|---|---|---|---|
| 1 | | CRÍTICO/ALTO/BAJO | |

## Fixes aplicados durante el QA
[lista de cambios que hiciste para que compilara/funcionara]

## Recomendaciones
[lo que más urge arreglar antes de la próxima prueba]
```

---

## Notas importantes
- El workdir del proyecto es `C:\Users\Jose-Firebat\proyectos\pcfutbol\android`
- La app se llama `com.pcfutbol.app`
- Si hay error de migración Room (la BD cambió de v2 a v3):
  `adb shell pm clear com.pcfutbol.app` para limpiar datos y relanzar
- Crea el directorio `qa_screenshots/` antes de hacer pull de screenshots
- Sin commits al finalizar

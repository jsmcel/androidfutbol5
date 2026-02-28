# Tarea para Kimi — GUI Visual Espectacular

## Objetivo
Transformar la UI actual (funcional pero plana) en algo visualmente impresionante manteniendo
la estética retro DOS. Tres entregas independientes — puedes hacerlas en el orden que prefieras.

---

## Colores y tema (ya definidos en PcfTheme.kt)
```
DosBlack  = #0A0A0F
DosNavy   = #0D1B2A
DosCyan   = #00E5FF
DosYellow = #FFD600
DosGreen  = #00E676
DosGray   = #424242
DosWhite  = #E0E0E0
```
FontFamily.Monospace en todo el texto.

---

## ENTREGA 1 — MainMenuScreen con efecto CRT retro

**Archivo**: `ui/src/main/kotlin/com/pcfutbol/ui/screens/MainMenuScreen.kt`
Reemplaza la pantalla actual con una versión con estos efectos visuales:

### 1.1 Fondo animado — campo de fútbol con niebla
- Fondo `DosNavy` con gradiente vertical oscuro
- Dibuja en Canvas un campo de fútbol simplificado (líneas blancas muy sutiles, opacity 0.08):
  - Círculo central
  - Línea de medio campo
  - Áreas de penalty (dos rectángulos)
- Encima, un overlay de gradiente radial negro desde las esquinas (viñeta) — simula pantalla CRT

### 1.2 Efecto scanlines CRT
Crea un composable `CrtScanlines` que dibuje en Canvas líneas horizontales semitransparentes:
```kotlin
@Composable
fun CrtScanlines(modifier: Modifier = Modifier) {
    // InfiniteTransition para animar el desplazamiento lento (offset Y 0..4px en 3 segundos loop)
    // Canvas: dibuja líneas horizontales cada 4px, color Black.copy(alpha=0.18f)
    // El offset Y crea el efecto de "barrido" suave
}
```
Aplica este composable como overlay en MainMenuScreen (y opcionalmente en otras pantallas).

### 1.3 Título con glow animado
El texto "PC FÚTBOL 5" debe tener:
- Color DosCyan
- `graphicsLayer { } ` con `BlurMaskFilter` o `shadowElevation` para glow
- Animación de parpadeo suave: `animateFloatAsState` que cicla el alpha entre 0.85f y 1.0f cada 1.5s
- Debajo: "TEMPORADA 2025/26" en DosYellow con letter-spacing

### 1.4 Pelota animada rodando
Dibuja una pelota de fútbol (círculo blanco con pentágonos negros) que rueda por la parte inferior
de la pantalla de izquierda a derecha, en bucle:
```kotlin
@Composable
fun RollingBall(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val x by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing=LinearEasing)))
    val rotation by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing=LinearEasing)))
    Canvas(modifier) {
        // Círculo blanco r=18dp
        // Encima: 5 pentágonos negros (aproximar con drawPath o pequeños círculos)
        // Posición X = x * size.width, Y fija
        // rotate(rotation, pivot=center del balón)
    }
}
```

### 1.5 Botones con animación de hover/press
Los DosButton deben tener:
- `animateColorAsState` para el border al presionar: DosCyan → DosWhite flash en 150ms
- Scale animation al presionar: `animateFloatAsState` 1.0f → 0.96f → 1.0f
- Texto con glow sutil (shadowElevation o similar)

### Resultado final de MainMenuScreen:
```
[fondo: campo DOS con niebla + viñeta oscura]
[scanlines animadas encima de todo]
                                          (arriba, centrado)
        ██ PC FÚTBOL 5 ██               <- título con glow pulsante
         TEMPORADA 2025/26              <- en amarillo
         ─────────────────

         [ LIGA / MANAGER ]             <- botones con border glow
         [   PROMANAGER   ]
         [ PROQUINIELAS   ]

     ⚽————————————————————→            <- pelota rodando (parte inferior)
     © 2026 PCF Android Rewrite
```

---

## ENTREGA 2 — TacticScreen con campo interactivo

**Archivo**: `ui/src/main/kotlin/com/pcfutbol/ui/screens/TacticScreen.kt`

### 2.1 Campo de fútbol en Canvas
Crea `FootballPitchCanvas` que dibuje:
```kotlin
@Composable
fun FootballPitchCanvas(
    formation: List<Pair<Float, Float>>,  // posiciones relativas (0..1, 0..1) de 11 jugadores
    onPositionTap: (playerIndex: Int) -> Unit,
    selectedIndex: Int = -1,
    modifier: Modifier = Modifier,
)
```
El campo dibuja en Canvas:
- Fondo: gradiente verde oscuro (#0a2e0a → #143d14)
- Líneas blancas (alpha 0.6): rectángulo exterior, línea de medio campo, círculo central (r=18% del ancho)
- Área grande local (inferior): rectángulo 62% ancho × 22% alto, centrado
- Punto de penalty inferior
- Área grande visitante (superior): igual
- Círculo de penalti en cada área

### 2.2 Fichas de jugadores
Sobre el campo, dibuja 11 círculos (uno por jugador):
- Círculo de radio 14dp, borde 2dp
- Color de relleno: `DosNavy`
- Borde: `DosCyan` si no seleccionado, `DosYellow` si seleccionado
- Texto dentro: número (1-11) o iniciales del apellido
- Posición según la formación: 4-3-3, 4-4-2, 4-5-1, 3-5-2, 5-3-2 (las más comunes)

Mapa de posiciones para cada formación (Y=0.0 es portería propia, Y=1.0 es portería rival):
```kotlin
val FORMATIONS: Map<String, List<Pair<Float,Float>>> = mapOf(
  "4-3-3" to listOf(
    0.5f to 0.05f,                                     // GK
    0.15f to 0.22f, 0.38f to 0.22f, 0.62f to 0.22f, 0.85f to 0.22f, // 4 DEF
    0.25f to 0.50f, 0.50f to 0.50f, 0.75f to 0.50f,   // 3 MID
    0.20f to 0.78f, 0.50f to 0.82f, 0.80f to 0.78f,   // 3 ATT
  ),
  "4-4-2" to listOf(
    0.5f to 0.05f,
    0.15f to 0.22f, 0.38f to 0.22f, 0.62f to 0.22f, 0.85f to 0.22f,
    0.12f to 0.52f, 0.38f to 0.52f, 0.62f to 0.52f, 0.88f to 0.52f,
    0.33f to 0.80f, 0.67f to 0.80f,
  ),
  "4-5-1" to listOf(
    0.5f to 0.05f,
    0.15f to 0.22f, 0.38f to 0.22f, 0.62f to 0.22f, 0.85f to 0.22f,
    0.10f to 0.52f, 0.30f to 0.52f, 0.50f to 0.52f, 0.70f to 0.52f, 0.90f to 0.52f,
    0.50f to 0.82f,
  ),
  "3-5-2" to listOf(
    0.5f to 0.05f,
    0.25f to 0.22f, 0.50f to 0.22f, 0.75f to 0.22f,
    0.10f to 0.52f, 0.30f to 0.52f, 0.50f to 0.52f, 0.70f to 0.52f, 0.90f to 0.52f,
    0.33f to 0.82f, 0.67f to 0.82f,
  ),
)
```

### 2.3 Selector de formación
Fila de chips en la parte superior del campo:
```
[ 4-3-3 ]  [ 4-4-2 ]  [ 4-5-1 ]  [ 3-5-2 ]
```
Al seleccionar una formación, las fichas se animan con `animateFloatAsState` a sus nuevas posiciones.

### 2.4 Animación de cambio de formación
Cuando el usuario cambia de formación, cada ficha se mueve suavemente a su nueva posición:
```kotlin
val animatedPositions = formation.mapIndexed { i, (tx, ty) ->
    val ax by animateFloatAsState(tx, animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f))
    val ay by animateFloatAsState(ty, animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f))
    ax to ay
}
```

### 2.5 Panel de parámetros tácticos (debajo del campo)
Sliders para los parámetros del TacticPresetEntity:
- **Tipo de juego**: barra con labels DEFENSIVO ←——→ OFENSIVO (valores 1,2,3)
- **Presión**: BAJA ←——→ ALTA
- **% Toque**: slider 0-100
- **% Contragolpe**: slider 0-100
- Botón GUARDAR (DosGreen) abajo del todo

---

## ENTREGA 3 — MatchdayScreen con campo animado

**Archivo**: `ui/src/main/kotlin/com/pcfutbol/ui/screens/Stubs.kt` (función `MatchdayScreen`)

**Nota**: El ViewModel `MatchdayViewModel` ya existe en
`ui/src/main/kotlin/com/pcfutbol/ui/viewmodels/MatchdayViewModel.kt`
y expone `uiState: StateFlow<MatchdayUiState>`.

### 3.1 Layout general
```
┌─────────────────────────────────────┐
│ ← [JORNADA 12]    [LIGA1]   [SIMULAR]│  <- TopBar
├─────────────────────────────────────┤
│                                     │
│   EQUIPO LOCAL    1 - 0    VISITANTE │  <- Marcador animado
│                                     │
│   [     CAMPO ANIMADO (Canvas)    ]  │  <- 60% de la altura
│                                     │
├─────────────────────────────────────┤
│  • min.23 ⚽ GOL — Real Madrid      │  <- EventLog (LazyColumn scroll)
│  • min.18 🟡 Amarilla — Barça       │
│  • min. 1 ⚫ INICIO                 │
└─────────────────────────────────────┘
```

### 3.2 Marcador con animación
Cuando cambia el marcador, anima con:
- `animatedContentTransform` slide-up para cada dígito
- Flash de color: gol local → flash DosGreen, gol visitante → flash DosYellow

### 3.3 Campo animado top-down
Composable `LivePitchView` que muestra:
- El mismo campo dibujado en Canvas (igual que ENTREGA 2 pero más pequeño)
- Una pelota (círculo amarillo ⌀10dp con glow) que se mueve suavemente entre zonas
- La pelota no se teletransporta — usa `animateOffsetAsState` con spring para moverse:
  ```kotlin
  val ballTarget = when (ballZone) {
      0 -> Offset(size.width * 0.5f, size.height * 0.1f)  // opp goal
      1 -> Offset(size.width * 0.5f, size.height * 0.3f)  // opp half
      2 -> Offset(size.width * 0.5f, size.height * 0.5f)  // center
      3 -> Offset(size.width * 0.5f, size.height * 0.7f)  // our half
      else -> Offset(size.width * 0.5f, size.height * 0.9f) // our goal
  }
  val ballPos by animateOffsetAsState(ballTarget, spring(dampingRatio=0.6f, stiffness=80f))
  ```
- El campo usa los mismos equipos (local/visitante): fichas cyan (local) y grises (visitante)
- Las fichas se distribuyen fijas según sus líneas (GK en fondo, DEF línea 2, etc.)
- La pelota tiene un glow: dibuja dos círculos concéntricos semitransparentes antes del círculo sólido

### 3.4 Event log con animaciones
Lista de eventos del partido en la parte inferior:
- Cada evento aparece con slide-in desde la izquierda (AnimatedVisibility + slideInFromLeft)
- Íconos: ⚽ (gol), 🟡 (amarilla), 🟥 (roja), ⭐ (sustitución)
- Color de fondo: si es gol del equipo del manager → DosGreen.copy(alpha=0.15f)

### 3.5 Botón SIMULAR con estado
El botón SIMULAR debe cambiar de estado visualmente:
- Estado IDLE: border DosCyan, texto "SIMULAR JORNADA"
- Estado LOADING: spinner + texto "SIMULANDO..." + borde animado (giro del borde con drawArc)
- Estado DONE: checkmark verde + texto "SIGUIENTE JORNADA"

---

## Notas técnicas para todas las entregas

- **Imports Compose**: usa `androidx.compose.animation.*`, `androidx.compose.ui.graphics.*`,
  `androidx.compose.animation.core.*` para las animaciones
- **Canvas**: `import androidx.compose.foundation.Canvas`
- **No romper ViewModels existentes** — solo modificar los @Composable de pantalla
- **Rendimiento**: las animaciones de Canvas deben usar `remember { }` para los objetos Paint
  (en Canvas de Compose se usa `drawContext.canvas.nativeCanvas` para BlurMaskFilter)
- **Sin emojis hardcodeados si hay problemas de font** — usar `Icons.*` de Material como alternativa
- **Compatibilidad**: Android API 26+ (minSdk ya está en 26)
- No crear archivos de documentación (.md)
- Todos los strings de UI en español

# Pausa ⏸️

App de Android que **mide el tiempo que pasas en cada app** y, cuando llegas al límite que tú elegiste, **te interrumpe con una alarma interactiva** a pantalla completa.

## Cómo funciona

1. Abres Pausa y ves todas tus apps ordenadas por tiempo de uso de hoy.
2. Tocas una app (ej. Instagram, TikTok) y eliges un límite diario: 5 min, 15 min, 1 h…
3. Pausa vigila en segundo plano. Te avisa con una notificación **2 minutos antes** del límite.
4. Al llegar al límite suena una **alarma** (sonido + vibración) encima de la app, con dos opciones:
   - **"Ya me voy 👋"** → te lleva a la pantalla de inicio y suma una victoria a tu contador.
   - **"Necesito 5 minutos más"** → no es gratis:
     1. **15 segundos de respiración** guiada ("¿De verdad lo necesitas?").
     2. **Reto matemático** con 4 opciones. Si fallas, vuelves a respirar.
     3. Cada vez que pides más tiempo el mismo día, los retos son **más difíciles y más numerosos** (hasta 4 seguidos).
5. El botón "atrás" no cierra la alarma. Si vuelves a abrir la app sin tiempo extra, la alarma vuelve.
6. Los contadores se reinician cada día a medianoche.

## Permisos que pide

| Permiso | Para qué |
|---|---|
| Acceso al uso de apps | Saber qué app usas y cuánto tiempo (`UsageStatsManager`) |
| Mostrar sobre otras apps | Dibujar la alarma encima de la app |
| Notificaciones | Avisos previos y la notificación del servicio de vigilancia |

Todo se queda en tu teléfono: la app no usa internet.

## Instalar

**Opción fácil (sin compilar):** cada push compila el APK en GitHub Actions.
Ve a la pestaña **Actions** del repositorio → el último *Android build* → descarga el artefacto **Pausa-debug-apk**, descomprímelo e instala `app-debug.apk` en tu teléfono (tendrás que permitir "instalar apps desconocidas").

**Con Android Studio:** abre la carpeta del proyecto y pulsa ▶️ Run.

**Desde terminal:**
```bash
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

Requiere Android 8.0 (API 26) o superior.

## Estructura

```
app/src/main/java/com/pausa/app/
├── MainActivity.kt     # Pantalla principal: permisos, lista de apps, límites
├── AppAdapter.kt       # Fila de cada app (uso de hoy + barra de progreso)
├── UsageTracker.kt     # Calcula el tiempo de uso de hoy desde los eventos del sistema
├── LimitStore.kt       # Guarda límites, minutos extra y estadísticas
├── MonitorService.kt   # Servicio en primer plano que vigila cada 4 s
├── AlarmOverlay.kt     # La alarma interactiva (alarma → respiración → reto)
├── AlarmSound.kt       # Sonido de alarma en bucle + vibración
├── Challenge.kt        # Generador de retos matemáticos por dificultad
├── Permissions.kt      # Comprobación de permisos
└── BootReceiver.kt     # Reactiva la vigilancia al reiniciar el teléfono
```

## Nota sobre iPhone

iOS no permite que una app normal lea el tiempo de uso de otras apps; eso requiere la API *Screen Time / FamilyControls* de Apple con un permiso especial. Por eso esta versión es para Android.

## Cambiar el estilo visual con Refero + Claude

Puedes darle a Pausa el "look" de una web conocida usando [Refero Styles](https://styles.refero.design) y la skill `refero-style` incluida en `.claude/skills/`. Guía paso a paso para principiantes: [docs/GUIA-REFERO.md](docs/GUIA-REFERO.md).

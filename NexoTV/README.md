# NexoTV — Reproductor IPTV para Android

App original para ver tus listas IPTV en **móvil, tablet y Android TV**. No incluye canales: usa las listas de tu proveedor.

## Funciones

- **Listas**: M3U/M3U8 por URL, archivo M3U local y cuentas **Xtream Codes** (servidor + usuario + contraseña). Varias listas a la vez, cada una con su interruptor para **activarla o desactivarla** (los canales de una lista desactivada se ocultan sin borrarse).
- **Varias guías EPG (XMLTV)** combinadas: `.xml` o `.xml.gz`, por URL o archivo.
  - Se añaden solas desde `url-tvg` de la lista M3U o desde el servidor Xtream.
  - Asociación automática por `tvg-id` y, si no coincide, por nombre del canal (ignora "HD", "FHD", prefijos "ES:", acentos…).
  - **Prioridad** ordenable: si un canal está en varias guías, gana la de arriba.
  - **Asignación manual** del canal de la guía desde el menú ⋮ de cada canal.
  - Ajuste horario (±3 h), días a cargar y actualización automática.
- **Guía en parrilla** (24 h) con línea de "ahora" y ficha de cada programa.
- **Reproducción 1080p** con Media3/ExoPlayer: HLS, MPEG-TS, DASH.
  - Resolución máxima configurable (480p / 720p / **1080p** / 4K / sin límite) y opción "Forzar máxima calidad".
  - Botón **Idioma** para cambiar entre las pistas de audio del canal (p. ej. Español / Inglés). El idioma elegido se recuerda y se aplica solo a los demás canales. Ajuste «Idioma de audio preferido» y tecla de idioma/audio del mando.
  - **Motor VLC de respaldo**: si un canal suena pero sale en negro (típico de algunos SD 720×480 en MPEG-2 o H.264 entrelazado) o trae un formato que el dispositivo no decodifica, la app cambia sola a VLC con decodificación por software. Botón «ExoPlayer/VLC» para cambiarlo a mano y ajuste «Motor de reproducción».
  - Selector de calidad y subtítulos; muestra la resolución real (p. ej. `FHD 1080p (1920×1080)`).
  - Zapping con ▲▼ / CH+ CH− del mando o deslizando el dedo; lista de canales lateral con ◄.
  - Reintento automático y detección de HLS sin extensión.
- **Favoritos**, **recientes**, **búsqueda** y grupos.
- User-Agent y Referer por canal (`#EXTVLCOPT` y formato `url|User-Agent=…`).

## Compilar

### Opción A: Android Studio
1. Abre la carpeta `NexoTV` con Android Studio (Ladybug o más reciente).
2. Espera a que sincronice Gradle y pulsa ▶ **Run**, o *Build → Build APK(s)*.

### Opción B: GitHub (sin instalar nada)
1. Sube esta carpeta a un repositorio de GitHub.
2. La acción **Compilar APK** (`.github/workflows/build.yml`) se ejecuta sola.
3. Descarga el APK desde la pestaña *Actions → último build → Artifacts*.

### Opción C: línea de comandos
```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```
El APK incluye VLC solo para ARM (armeabi-v7a y arm64-v8a), por eso pesa unos 35-45 MB. El APK release está firmado con la clave de depuración para instalarlo directamente. Para publicarlo en Google Play, configura tu propia firma.

## Uso en Android TV
- ◄ ► ▲ ▼ y OK para moverte. En el reproductor: **OK o ◄ abren la lista de canales**, ► o Info muestran la barra (con el foco en «Lista de canales»), ▲▼ cambian de canal, la tecla de audio/idioma cambia el idioma, Atrás oculta/cierra.
- Para instalar: copia el APK con un USB o con una app tipo "Downloader" y actívale "Fuentes desconocidas".

## Estructura
```
app/src/main/java/com/nexotv/app/
├── data/      Modelos, M3U, Xtream, XMLTV, combinación de EPG, almacenamiento
├── ui/        Pantallas Compose: Canales, Guía, Favoritos, Ajustes
└── player/    Reproductor a pantalla completa (Media3)
```

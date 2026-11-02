# MKVCast

**Transmití los videos de tu PC al Chromecast y controlalos desde el celular.**

MKVCast es un programa para Windows que convierte en vivo cualquier video de tu PC (MKV, MP4, AVI, HEVC/H.265, 4K, anime con subtítulos ASS, etc.) a un formato que un Chromecast de 2ª generación puede reproducir, y lo envía por la red local. Se maneja desde el celular, como un control remoto, ya sea desde el navegador o desde una app Android.

```
 ┌──────────┐   control (wifi)   ┌──────────────┐   video (wifi)   ┌─────────────┐
 │ Celular  │ ─────────────────► │  PC (MKVCast)│ ───────────────► │ Chromecast  │ ──► TV
 │ (remoto) │ ◄───────────────── │  + ffmpeg    │                  │             │
 └──────────┘      estado        └──────────────┘                  └─────────────┘
```

---

## Índice

1. [Características](#características)
2. [Cómo funciona](#cómo-funciona)
3. [Requisitos](#requisitos)
4. [Instalación](#instalación)
5. [Configuración](#configuración)
6. [Uso en la PC](#uso-en-la-pc)
7. [El celular como control remoto](#el-celular-como-control-remoto)
8. [Subtítulos](#subtítulos)
9. [Imagen y sonido](#imagen-y-sonido)
10. [Playlists, cola y "Continuar viendo"](#playlists-cola-y-continuar-viendo)
11. [Seguridad y red](#seguridad-y-red)
12. [Solución de problemas](#solución-de-problemas)
13. [API HTTP](#api-http)
14. [Limitaciones conocidas](#limitaciones-conocidas)
15. [Cómo se hizo](#cómo-se-hizo)
16. [Licencia](#licencia)

---

## Características

| Área | Qué ofrece |
|---|---|
| **Transmisión** | HEVC/H.265 y otros códecs convertidos en vivo a H.264 High 4.1; límite de 1080p y 30 fps (48 fps baja a 24, 60 a 30) |
| **Aceleración por GPU** | Detecta y usa NVENC (NVIDIA), QSV (Intel) o AMF (AMD); si no hay, usa la CPU |
| **Biblioteca** | Navegación por carpetas, miniaturas, búsqueda, varias carpetas raíz y carpetas agregadas arrastrando |
| **Subtítulos** | Embebidos y externos (.srt, .ass, .ssa, .vtt), selección automática, estilo personalizable, sincronía ±, ASS/SSA respetados |
| **Audio** | Selección de pista, modo "Diálogos más claros" y "Modo noche" |
| **HDR** | Conversión de HDR / Dolby Vision (base HDR10) a SDR para que los colores no se vean apagados |
| **Cola y playlists** | Cola de reproducción, playlists guardadas, mezclar, ordenar, renombrar |
| **Continuar viendo** | Retoma donde dejaste, marca videos vistos, fila de "Continuar viendo" |
| **Control remoto** | Web móvil y app Android con la misma funcionalidad |
| **Subir archivos** | Mandar un video desde el celular a la PC para reproducirlo |
| **Bandeja del sistema** | Sigue transmitiendo con la ventana cerrada |

---

## Cómo funciona

1. Elegís un video desde la PC o el celular.
2. MKVCast lo analiza con `ffprobe` (códecs, pistas, subtítulos, HDR, duración).
3. `ffmpeg` convierte el video en vivo a **MP4 fragmentado H.264** y lo sirve por HTTP en `/stream`.
4. El Chromecast descarga ese stream desde la PC y lo reproduce en la TV.
5. El celular solo envía órdenes (play, pausa, adelantar, volumen, subtítulos…) y consulta el estado cada pocos segundos. **El video nunca pasa por el celular.**

Los subtítulos se **queman en la imagen** durante la conversión, y por eso funcionan con cualquier Chromecast sin necesitar un receptor propio. Cambiar subtítulos, pista de audio o estilo reinicia la transmisión en el mismo punto.

---

## Requisitos

- Windows 10/11 (64 bits)
- Un Chromecast de 2ª generación (otros modelos también funcionan, pero el programa está optimizado para ese)
- PC, celular y Chromecast en la **misma red wifi**
- `ffmpeg.exe` y `ffprobe.exe` en la carpeta del programa
  - Para la corrección HDR, un ffmpeg con los filtros `zscale` y `tonemap` (las compilaciones *full* y *essentials* de Windows los incluyen)
- Para compilar desde el código: Node.js

---

## Instalación

### Opción A: programa portable (recomendada)

Carpeta que se puede llevar a otra PC:

```
MKVCast-2.0.0-portable.exe
ffmpeg.exe
ffprobe.exe
config.json
```

Junto al exe se crean solas las carpetas `uploads`, `subs`, `thumbs` y el archivo `state.json`.

La primera vez, el Firewall de Windows pregunta: permití el acceso en **redes privadas**.

### Opción B: compilar desde el código

```bash
# 1. Instalar dependencias (descarga Electron, ~100 MB)
npm install

# 2. Probar sin generar el exe
npm start

# 3. Generar el portable
npm run build
# -> dist/MKVCast-2.0.0-portable.exe
```

Antes de compilar, copiá `ffmpeg.exe`, `ffprobe.exe` y tu `config.json` a la carpeta del proyecto (si no tenés `config.json`, copiá `config.example.json` y editalo).

> Si tenés abierta una versión anterior de MKVCast, cerrala antes: usan el mismo puerto (8099).

---

## Configuración

Todo se puede cambiar desde la pantalla **Ajustes**, o editando `config.json`:

```json
{
  "folders": ["E:\\Peliculas y series"],
  "gpu": "auto",
  "maxHeight": 1080,
  "maxFps": 30,
  "port": 8099
}
```

| Clave | Descripción | Por defecto |
|---|---|---|
| `folders` | Carpetas con tus videos | — |
| `gpu` | `auto`, `nvenc`, `qsv`, `amf` o `cpu` | `auto` |
| `maxHeight` | Alto máximo de salida | `1080` |
| `maxFps` | Cuadros por segundo máximos | `30` |
| `port` | Puerto del servidor | `8099` |
| `pin` | PIN de acceso desde otros dispositivos (opcional) | sin PIN |

- Los cambios hechos en Ajustes se guardan en `config.json`.
- El **puerto** solo se cambia editando `config.json` y reiniciando.
- Si la dirección que muestra la app no es la de tu red (por VPN o máquinas virtuales), escribí la IP correcta en *Ajustes → IP de esta PC*.

---

## Uso en la PC

La ventana de escritorio tiene cuatro secciones y un reproductor fijo abajo:

- **Biblioteca**: carpetas, miniaturas, búsqueda, fila de *Continuar viendo*, botón ✓ para marcar visto/no visto y ♫ para agregar a una playlist.
- **Cola**: lo que se va a reproducir, reordenable.
- **Playlists**: crear, renombrar, ordenar, mezclar y reproducir.
- **Ajustes**: carpetas, dispositivo Chromecast, subtítulos, imagen y sonido, control desde el celular.

El **reproductor fijo** incluye reproducir/pausar, adelantar/retroceder, anterior/siguiente, barra de progreso, volumen, selector de audio y subtítulos, tamaño y sincronía de subtítulos.

Al cerrar la ventana, MKVCast queda en la **bandeja del sistema** (junto al reloj) y sigue transmitiendo. Para salir del todo: clic derecho en el ícono → *Salir*.

---

## El celular como control remoto

El celular no reproduce nada: es un **mando a distancia** del programa que corre en la PC. Hay dos formas de usarlo.

### Cómo se conecta

1. Abrí MKVCast en la PC.
2. Mirá la dirección en *Ajustes → Control desde el celular*, por ejemplo `http://192.168.0.50:8099`.
3. En el celular (conectado al mismo wifi) abrí esa dirección en el navegador, o escribila en la app Android la primera vez.
4. Si configuraste un PIN, el celular lo pide una vez.

### Opción 1: web móvil

No hay nada que instalar. Se abre en el navegador del celular y funciona en cualquier teléfono.

### Opción 2: app Android

App nativa con la misma funcionalidad y un diseño pensado para el celular (compatible con Android 7 en adelante). Recuerda la dirección del servidor y el PIN, y se actualiza sola mientras está abierta.

### Qué se puede hacer desde el celular

- **Elegir el Chromecast** al que transmitir
- **Explorar la biblioteca** de la PC con miniaturas y ver *Continuar viendo* (la búsqueda está en la app Android)
- **Reproducir** un video, eligiendo pista de audio y subtítulo antes de empezar
- **Controlar la reproducción**: pausa, ±30 segundos, anterior/siguiente, barra de progreso
- **Volumen** con barra propia (el programa arranca en 50 % para que no suene al máximo)
- **Subtítulos en vivo**: tamaño, estilo (color, fondo, borde, sombra, posición), y sincronía − / +
- **Gestionar la cola y las playlists**
- **Marcar** videos como vistos o no vistos
- **Subir un video del celular** a la PC: MKVCast revisa primero si ya existe en tu biblioteca; si no, lo sube a la carpeta `uploads` y lo reproduce
- **Ajustes de imagen y sonido**: HDR, diálogos más claros, modo noche, respeto de subtítulos ASS

### Qué NO hace

- No reproduce el video en el celular.
- No funciona fuera de tu red wifi (a menos que uses una VPN como WireGuard o Tailscale hacia tu PC).
- No cambia la resolución máxima: eso se define en `config.json` de la PC.

---

## Subtítulos

### Detección y selección

- **Embebidos** en el MKV y **externos** (`.srt`, `.ass`, `.ssa`, `.vtt`) con el mismo nombre del video:

  ```
  Pelicula.mkv
  Pelicula.es.srt
  Pelicula.en.srt
  ```

  También se buscan en subcarpetas `Subs`, `Subtitles`, `Sub` o `subs`.
- Los externos aparecen marcados como "archivo externo". El **modo automático** elige el español por el nombre (`.es`, `.spa`, `.esp`, `.spanish`, `.latino`, `.castellano`) o por la marca del archivo (predeterminado / forzado).
- El selector muestra el tipo, por ejemplo `spa (SRT)` o `spa (PGS · imagen)`.
- Los acentos mal codificados (ISO-8859-1 / Windows-1252) se corrigen solos.

### Sincronía

Botones **−** y **+**: cada clic mueve 0,5 s (en la PC, Mayús+clic mueve 0,1 s). Un clic sobre el valor vuelve a 0. Se recuerda **por video**.

### Estilo (SRT y subtítulos externos)

Color del texto, caja de fondo con opacidad, grosor del borde, sombra y posición (abajo, un poco más arriba, al medio, arriba). Hay vista previa en la PC y botón *Restablecer estilo*.

### Subtítulos ASS/SSA (anime)

Por defecto se **respetan tal cual vienen**: posición, tamaño, colores y fuentes, incluidas las fuentes incrustadas en el MKV. Así los carteles posicionados y los efectos se ven como el autor los hizo. Si preferís un estilo único para todo, usá *Ajustes → Subtítulos → Subtítulos con estilo (ASS) → Usar mi estilo*.

### Subtítulos de imagen (PGS, VobSub)

No se pueden mostrar. Aparecen deshabilitados en el selector y el modo automático no los elige.

---

## Imagen y sonido

- **Colores HDR**: los videos HDR10 / Dolby Vision con base HDR10 se convierten a SDR al transmitir para que no se vean grises. Usa más procesador: si un 4K HDR se corta, desactivalo en *Ajustes → Imagen y sonido*. El Dolby Vision perfil 5 no se puede corregir bien.
- **Diálogos más claros**: mezcla el 5.1 a estéreo subiendo las voces.
- **Modo noche**: además de lo anterior, parejiza el volumen (explosiones menos fuertes, susurros más claros).
- **Volumen**: la primera vez que se reproduce con un Chromecast se pone al 50 %; después se mantiene el último que ajustaste.

---

## Playlists, cola y "Continuar viendo"

- **Cola**: se arma agregando videos; se puede guardar como playlist.
- **Playlists**: crear, renombrar, ordenar, mezclar y reproducir, desde la PC o el celular.
- **Retomar**: MKVCast recuerda en qué punto quedó cada video.
- **Continuar viendo**: fila con lo que dejaste a medias, con barra de progreso.
- **Visto / no visto**: se marca solo al terminar un video, y se puede cambiar a mano con el botón ✓.

---

## Seguridad y red

- **PIN opcional**: con `pin` en `config.json`, los demás dispositivos deben iniciar sesión; la propia PC entra sin PIN.
- La configuración sensible (`/api/config`, `/api/addpath`) solo se acepta desde la propia PC.
- El stream usa una clave temporal para que no se pueda acceder al video sin autorización.
- **No expongas el puerto a internet** (no hagas redirección de puertos en el router). Para controlarlo desde afuera de casa, usá una VPN.
- El servidor funciona sobre HTTP sin cifrar, pensado para una red doméstica de confianza.

---

## Solución de problemas

| Problema | Qué probar |
|---|---|
| No aparece el Chromecast | Verificá que PC, celular y Chromecast estén en el mismo wifi; permití el Firewall en *redes privadas* |
| El celular no conecta | Revisá la IP en *Ajustes → IP de esta PC*, sobre todo si hay VPN o máquinas virtuales |
| Error "puerto en uso" | Cerrá otra copia de MKVCast, o cambiá `port` en `config.json` |
| Microcortes en 4K | Mirá la velocidad `speed=` de ffmpeg al ejecutar `npm start`; si es menor a `1.0x`, la PC no alcanza a convertir en tiempo real. Desactivá la corrección HDR o bajá `maxHeight` |
| Colores grises en HDR | Activá *Colores de videos HDR* y usá un ffmpeg con `zscale` y `tonemap` |
| Subtítulos de imagen | PGS y VobSub no se pueden mostrar; usá una versión con subtítulos de texto |

Para ver errores y la velocidad de ffmpeg, ejecutá `npm start` desde una terminal.

---

## API HTTP

Todo lo que hace el celular pasa por una API HTTP simple, útil si querés hacer tu propio cliente.

- Con PIN: `POST /api/login` con `{"pin": "..."}` devuelve un `token`; el resto de las llamadas lo envían en el header `x-token`.
- La miniatura se pide con `GET /api/thumb`.

| Método | Ruta | Para qué sirve |
|---|---|---|
| POST | `/api/login` | Iniciar sesión con PIN |
| GET | `/api/state` | Estado completo: reproducción, cola, dispositivos, ajustes, "continuar viendo" |
| GET | `/api/ls` | Listar una carpeta de la biblioteca |
| GET | `/api/thumb` | Miniatura de un video |
| GET | `/api/info` | Información del video (pistas de audio y subtítulos, duración, códecs) |
| GET | `/api/find` | Buscar un video por nombre y tamaño |
| PUT | `/api/upload` | Subir un video desde el celular |
| POST | `/api/device` | Elegir el Chromecast |
| POST | `/api/play` | Reproducir un video (audio, subtítulo, inicio) |
| POST | `/api/control` | Pausa, ir a un punto, volumen, etc. |
| POST | `/api/queue` | Operaciones sobre la cola |
| POST | `/api/playlist` | Operaciones sobre playlists |
| POST | `/api/subdelay` | Sincronía de subtítulos |
| POST | `/api/settings` | Subtítulos, imagen y sonido |
| POST | `/api/seen` | Marcar visto / no visto |
| POST | `/api/recent` | Quitar de "Continuar viendo" |

---

## Limitaciones conocidas

- Solo Windows (la versión portable); el servidor Node.js puede correr en otros sistemas, pero no está probado.
- La conversión en vivo depende de la potencia de la PC. Los 4K HDR con corrección de color son lo más exigente.
- Dolby Vision perfil 5 no se corrige bien.
- Los subtítulos de imagen (PGS / VobSub) no se pueden mostrar.
- Si cambiás el volumen con el control remoto de la TV, la barra de MKVCast no se entera.
- Solo funciona dentro de la misma red local (o con VPN).
- La resolución máxima solo se cambia desde `config.json`.

---

## Cómo se hizo

MKVCast fue desarrollado por bareinn, con ayuda de herramientas de
inteligencia artificial:

- **Claude** (Anthropic): servidor, interfaz de escritorio y web móvil,
  transcodificación con ffmpeg, subtítulos y documentación.
- **Gemini** (Google AI Studio): generación de la app Android a partir de
  una especificación escrita para ese fin.

Las decisiones de diseño, las pruebas en dispositivos reales y la revisión
del resultado las hice yo. Si encontrás errores o algo que mejorar, los
issues y pull requests son bienvenidos.

---

## Licencia

Distribuido bajo la licencia MIT. Ver el archivo [LICENSE](LICENSE) para más información.

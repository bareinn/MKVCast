<div align="center">

# 📺 MKVCast

**Transmití los videos de tu PC al Chromecast y controlalos desde el celular.**

Conversión en vivo de MKV, HEVC, 4K y anime con subtítulos ASS, sin preparar nada de antemano.

[![Última versión](https://img.shields.io/github/v/release/bareinn/MKVCast?label=versi%C3%B3n&color=6D8DFF)](https://github.com/bareinn/MKVCast/releases/latest)
[![Licencia MIT](https://img.shields.io/github/license/bareinn/MKVCast?color=green)](LICENSE)
![Windows](https://img.shields.io/badge/Windows-10%20%7C%2011-0078D6?logo=windows&logoColor=white)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Electron](https://img.shields.io/badge/Electron-33-47848F?logo=electron&logoColor=white)
![Node.js](https://img.shields.io/badge/Node.js-339933?logo=nodedotjs&logoColor=white)

[**⬇ Descargar**](https://github.com/bareinn/MKVCast/releases/latest) ·
[Instalación](#instalación) ·
[Celular como control remoto](#el-celular-como-control-remoto) ·
[Solución de problemas](#solución-de-problemas)

</div>

---

## ¿Qué es?

MKVCast es un programa para Windows que toma cualquier video de tu PC (MKV, MP4, AVI, HEVC/H.265, 4K…), lo convierte **en vivo** a un formato que un Chromecast de 2ª generación puede reproducir y lo envía por tu red local. Se maneja desde el celular como si fuera un control remoto, con la web móvil o con una app Android.

```
 ┌──────────┐   control (wifi)   ┌───────────────┐   video (wifi)   ┌─────────────┐
 │ Celular  │ ─────────────────► │ PC (MKVCast)  │ ───────────────► │ Chromecast  │ ──► TV
 │ (remoto) │ ◄───────────────── │   + ffmpeg    │                  │             │
 └──────────┘      estado        └───────────────┘                  └─────────────┘
```

**Ideal si:**

- Tenés una colección de películas y series en la PC y un Chromecast viejo que no entiende HEVC, 4K ni subtítulos ASS.
- Querés mirar anime con los carteles, colores y fuentes originales de los subtítulos.
- Preferís manejarlo todo desde el celular, sin levantarte del sillón.

---

## Inicio rápido

1. Descargá `MKVCast-2.2.0-portable.exe` y `MKVCast.apk` desde [**Releases**](https://github.com/bareinn/MKVCast/releases/latest).
2. Poné el `.exe` en una carpeta junto con `ffmpeg.exe`, `ffprobe.exe` y tu `config.json` ([cómo hacerlo](#instalación)).
3. Abrí el programa, elegí tu Chromecast y tus carpetas de videos en **Ajustes**.
4. Instalá el APK en el celular, escribí la dirección que muestra el programa (por ejemplo `192.168.1.20:8099`) y listo.

<!--
CAPTURAS: subí imágenes a la carpeta docs/screenshots/ del repositorio y descomentá este bloque.

## Capturas

| Programa de la PC | App del celular |
|---|---|
| ![PC](docs/screenshots/pc.png) | ![Celular](docs/screenshots/celular.png) |
-->

---

## Índice

- [Características](#características)
- [Cómo funciona](#cómo-funciona)
- [Requisitos](#requisitos)
- [Instalación](#instalación)
- [Configuración](#configuración)
- [Uso en la PC](#uso-en-la-pc)
- [El celular como control remoto](#el-celular-como-control-remoto)
- [Subtítulos](#subtítulos)
- [Imagen y sonido](#imagen-y-sonido)
- [Playlists, cola y "Continuar viendo"](#playlists-cola-y-continuar-viendo)
- [Seguridad y red](#seguridad-y-red)
- [Solución de problemas](#solución-de-problemas)
- [API HTTP](#api-http)
- [Limitaciones conocidas](#limitaciones-conocidas)
- [Cómo se hizo](#cómo-se-hizo)
- [Contribuir](#contribuir)
- [Licencia](#licencia)

---

## Características

| Área | Qué ofrece |
|---|---|
| 🎞 **Transmisión** | HEVC/H.265 y otros códecs convertidos en vivo a H.264 High 4.1. Límite de 1080p y 30 fps (48 fps baja a 24, 60 a 30) |
| ⚡ **Aceleración por GPU** | Usa NVENC (NVIDIA), QSV (Intel) o AMF (AMD) si los detecta; si no, la CPU |
| 📚 **Biblioteca** | Carpetas, miniaturas, búsqueda, varias carpetas raíz y carpetas agregadas arrastrando |
| 💬 **Subtítulos** | Embebidos y externos (`.srt`, `.ass`, `.ssa`, `.vtt`), PGS de Blu-ray y VobSub de DVD, selección automática, estilo personalizable, sincronía ± y ASS/SSA respetados |
| 🔊 **Audio** | Elección de pista, "Diálogos más claros" y "Modo noche" |
| 🌈 **HDR** | HDR / Dolby Vision (base HDR10) convertido a SDR para que los colores no se vean apagados |
| 📋 **Cola y playlists** | Cola, playlists guardadas, mezclar, ordenar y renombrar |
| ▶ **Continuar viendo** | Retoma donde dejaste, marca videos vistos y muestra lo que quedó a medias |
| 📱 **Control remoto** | Web móvil y app Android con la misma funcionalidad |
| 🔘 **Botones de volumen** | En la app Android, los botones físicos controlan el volumen del Chromecast |
| 📤 **Subir archivos** | Mandá un video del celular a la PC para reproducirlo |
| 🗂 **Bandeja del sistema** | Sigue transmitiendo con la ventana cerrada |

---

## Cómo funciona

1. Elegís un video desde la PC o el celular.
2. MKVCast lo analiza con `ffprobe` (códecs, pistas, subtítulos, HDR, duración).
3. `ffmpeg` lo convierte en vivo a **MP4 fragmentado H.264** y lo sirve por HTTP en `/stream`.
4. El Chromecast descarga ese stream desde la PC y lo reproduce en la TV.
5. El celular solo envía órdenes (play, pausa, adelantar, volumen, subtítulos…) y consulta el estado cada pocos segundos. **El video nunca pasa por el celular.**

Los subtítulos se **queman en la imagen** durante la conversión, por eso funcionan en cualquier Chromecast sin necesitar un receptor propio. Cambiar subtítulos, pista de audio o estilo reinicia la transmisión en el mismo punto.

---

## Requisitos

- Windows 10/11 (64 bits)
- Un Chromecast de 2ª generación (otros modelos también funcionan, pero el programa está pensado para ese)
- PC, celular y Chromecast en la **misma red wifi**
- `ffmpeg.exe` y `ffprobe.exe` junto al programa
  - Para la corrección HDR hace falta un ffmpeg con los filtros `zscale` y `tonemap` (las compilaciones *full* y *essentials* para Windows los incluyen, por ejemplo las de [gyan.dev](https://www.gyan.dev/ffmpeg/builds/))
- Para compilar desde el código: [Node.js](https://nodejs.org/)

---

## Instalación

### Opción A: programa portable (recomendada)

1. Descargá `MKVCast-2.2.0-portable.exe` desde [Releases](https://github.com/bareinn/MKVCast/releases/latest).
2. Creá una carpeta (por ejemplo `C:\MKVCast`) con estos archivos:

   ```
   MKVCast-2.2.0-portable.exe
   ffmpeg.exe
   ffprobe.exe
   config.json
   ```

   `ffmpeg.exe` y `ffprobe.exe` no vienen incluidos: descargalos aparte. Para el `config.json`, copiá [`config.example.json`](config.example.json) y editá tus carpetas.
3. Abrí el `.exe`. La primera vez, el Firewall de Windows pregunta: permití el acceso en **redes privadas**.

Junto al exe se crean solas las carpetas `uploads`, `subs`, `thumbs` y el archivo `state.json`.

### Opción B: compilar desde el código

```bash
# 1. Instalar dependencias (descarga Electron, ~100 MB)
npm install

# 2. Probar sin generar el exe
npm start

# 3. Generar el portable
npm run build
# -> dist/MKVCast-2.2.0-portable.exe
```

Antes de compilar, copiá `ffmpeg.exe`, `ffprobe.exe` y tu `config.json` a la carpeta del proyecto.

> Si tenés abierta una versión anterior de MKVCast, cerrala antes: usan el mismo puerto (8099).

---

## Configuración

Casi todo se cambia desde **Ajustes**, o editando `config.json`:

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
| `hwdecode` | Decodificar también con la GPU (`true` / `false`). Solo se usa si el codificador es una GPU; desactivala si algún video da cortes o errores | `true` |
| `maxHeight` | Alto máximo de salida | `1080` |
| `maxFps` | Cuadros por segundo máximos | `30` |
| `port` | Puerto del servidor | `8099` |
| `pin` | PIN de acceso desde otros dispositivos (opcional). También se cambia en *Ajustes → Control desde el celular* | sin PIN |

- Los cambios hechos en Ajustes se guardan en `config.json`.
- La **resolución máxima**, los **fps máximos** y la **GPU** también se cambian desde *Ajustes* en el programa de la PC.
- El **puerto** solo se cambia editando `config.json` y reiniciando.
- Si la dirección que muestra el programa no es la de tu red (por VPN o máquinas virtuales), escribí la IP correcta en *Ajustes → IP de esta PC*.

---

## Uso en la PC

La ventana tiene cuatro secciones y un reproductor fijo abajo:

- **Biblioteca**: carpetas, miniaturas, búsqueda, fila de *Continuar viendo*, botón ✓ para marcar visto/no visto y ♫ para agregar a una playlist.
- **Cola**: lo que se va a reproducir, reordenable.
- **Playlists**: crear, renombrar, ordenar, mezclar y reproducir.
- **Ajustes**: carpetas, Chromecast, subtítulos, imagen y sonido, control desde el celular.

El **reproductor fijo** incluye reproducir/pausar, adelantar/retroceder, anterior/siguiente, barra de progreso, volumen, selector de audio y subtítulos, y tamaño y sincronía de subtítulos.

Al cerrar la ventana, MKVCast queda en la **bandeja del sistema** (junto al reloj) y sigue transmitiendo. Para salir del todo: clic derecho en el ícono → *Salir*.

---

## El celular como control remoto

El celular no reproduce nada: es un **mando a distancia** del programa que corre en la PC.

### Cómo se conecta

1. Abrí MKVCast en la PC.
2. Mirá la dirección en *Ajustes → Control desde el celular*, por ejemplo `http://192.168.1.20:8099`.
3. En el celular (en el mismo wifi) abrila en el navegador, o escribila en la app Android la primera vez.
4. Si configuraste un PIN, el celular lo pide una vez.

### Opción 1: web móvil

No hay nada que instalar. Se abre en el navegador de cualquier teléfono.

### Opción 2: app Android

App nativa con la misma funcionalidad y un diseño pensado para el celular (Android 7 o superior). Recuerda la dirección del servidor y el PIN, y se actualiza sola mientras está abierta.

Descargá `MKVCast.apk` desde [Releases](https://github.com/bareinn/MKVCast/releases/latest), instalalo (hay que permitir orígenes desconocidos) y escribí la dirección del programa de la PC. El código fuente está en la carpeta [`android/`](android/).

### Qué se puede hacer desde el celular

- **Elegir el Chromecast** al que transmitir.
- **Explorar la biblioteca** de la PC con miniaturas y ver *Continuar viendo* (la búsqueda está en la app Android).
- **Reproducir** un video, eligiendo pista de audio y subtítulo antes de empezar.
- **Controlar la reproducción**: pausa, ±30 segundos, anterior/siguiente y barra de progreso.
- **Volumen** con barra propia. En la app Android también con los **botones físicos del celular**, mientras la app está abierta y hay algo reproduciéndose. El programa arranca al 50 % para que no suene al máximo.
- **Subtítulos en vivo**: tamaño, estilo (color, fondo, borde, sombra, posición) y sincronía − / +.
- **Gestionar la cola y las playlists**.
- **Marcar** videos como vistos o no vistos.
- **Subir un video del celular** a la PC: MKVCast revisa primero si ya existe en tu biblioteca; si no, lo sube a `uploads` y lo reproduce.
- **Ajustes de imagen y sonido**: HDR, diálogos más claros, modo noche y respeto de subtítulos ASS.

### Qué no hace

- No reproduce el video en el celular.
- No funciona fuera de tu red wifi, a menos que uses una VPN (WireGuard, Tailscale) hacia tu PC.
- No cambia la resolución máxima ni los fps: eso se ajusta desde *Ajustes* en el programa de la PC (o en `config.json`).
- Los botones físicos de volumen no controlan el Chromecast con la pantalla apagada o la app en segundo plano.

---

## Subtítulos

### Detección y selección

Se detectan los **embebidos** en el MKV y los **externos** (`.srt`, `.ass`, `.ssa`, `.vtt`) con el mismo nombre del video:

```
Pelicula.mkv
Pelicula.es.srt
Pelicula.en.srt
```

También se buscan en subcarpetas `Subs`, `Subtitles`, `Sub` o `subs`.

- Los externos aparecen marcados como "archivo externo".
- El **modo automático** elige el español por el nombre (`.es`, `.spa`, `.esp`, `.spanish`, `.latino`, `.castellano`) o por la marca del archivo (predeterminado / forzado).
- El selector muestra el tipo, por ejemplo `spa (SRT)` o `spa (PGS · imagen)`.
- Los acentos mal codificados (ISO-8859-1 / Windows-1252) se corrigen solos.

### Sincronía

Botones **−** y **+**: cada clic mueve 0,5 s (en la PC, Mayús+clic mueve 0,1 s). Un clic sobre el valor vuelve a 0. Se recuerda **por video**.

### Estilo

Color del texto, caja de fondo con opacidad, grosor del borde, sombra y posición (abajo, un poco más arriba, al medio, arriba). Hay vista previa en la PC y botón *Restablecer estilo*. Se aplica a SRT y subtítulos externos.

### ASS/SSA (anime)

Por defecto se **respetan tal cual vienen**: posición, tamaño, colores y fuentes, incluidas las fuentes incrustadas en el MKV. Así los carteles posicionados y los efectos se ven como los hizo el autor. Si preferís un estilo único para todo: *Ajustes → Subtítulos → Subtítulos con estilo (ASS) → Usar mi estilo*.

### Subtítulos de imagen (PGS, VobSub, DVB)

Los subtítulos de imagen de los Blu-ray (**PGS**) y de los DVD (**VobSub**) se dibujan sobre el video tal como vienen, con su posición y aspecto originales. En el selector aparecen como `spa (PGS · imagen)`.

- La **sincronía** (− / +) funciona igual que con los de texto.
- El **tamaño** (Pequeño / Normal / Grande / Muy grande) los agranda o achica: *Normal* es el tamaño original. El color, la caja de fondo, el borde y la sombra no se aplican, porque son imágenes y no texto.
- En modo **automático** se prefieren los de texto; los de imagen se usan solo si no hay otros.
- Si el video es 4K, el subtítulo se reduce junto con el video a 1080p.
- Usan algo más de procesador, aunque el costo es bajo frente al de convertir el video.
- XSUB, un formato poco común, no es compatible.

---

## Imagen y sonido

- **Colores HDR**: los videos HDR10 / Dolby Vision con base HDR10 se convierten a SDR al transmitir para que no se vean grises. Usa más procesador: si un 4K HDR se corta, desactivalo en *Ajustes → Imagen y sonido*. Dolby Vision perfil 5 no se puede corregir bien.
- **Diálogos más claros**: mezcla el 5.1 a estéreo subiendo las voces.
- **Modo noche**: además de lo anterior, parejiza el volumen (explosiones menos fuertes, susurros más claros).
- **Volumen**: la primera vez que se reproduce con un Chromecast se pone al 50 %; después se mantiene el último que ajustaste.

---

## Playlists, cola y "Continuar viendo"

- **Cola**: se arma agregando videos; se puede guardar como playlist.
- **Playlists**: crear, renombrar, ordenar, mezclar y reproducir, desde la PC o el celular.
- **Retomar**: MKVCast recuerda en qué punto quedó cada video.
- **Continuar viendo**: fila con lo que dejaste a medias y su barra de progreso.
- **Visto / no visto**: se marca solo al terminar un video, y se puede cambiar a mano con el botón ✓.

---

## Seguridad y red

- **PIN opcional**: se define en *Ajustes → Control desde el celular → PIN* (o con `pin` en `config.json`). Con PIN, los demás dispositivos deben iniciar sesión; la propia PC entra sin PIN.
- La configuración sensible (`/api/config`, `/api/addpath`) solo se acepta desde la propia PC.
- El stream usa una clave temporal para que no se pueda acceder al video sin autorización.
- **No expongas el puerto a internet** (no hagas redirección de puertos en el router). Para usarlo fuera de casa, usá una VPN.
- El servidor funciona sobre HTTP sin cifrar, pensado para una red doméstica de confianza.

---

## Solución de problemas

| Problema | Qué probar |
|---|---|
| No aparece el Chromecast | Verificá que PC, celular y Chromecast estén en el mismo wifi; permití el Firewall en *redes privadas* |
| El celular no conecta | Revisá la IP en *Ajustes → IP de esta PC*, sobre todo si hay VPN o máquinas virtuales |
| Error de puerto en uso | Cerrá otra copia de MKVCast, o cambiá `port` en `config.json` |
| Microcortes en 4K | Ejecutá `npm start` y mirá la velocidad `speed=` de ffmpeg; si es menor a `1.0x`, la PC no alcanza a convertir en tiempo real. Desactivá la corrección HDR o bajá `maxHeight` |
| Colores grises en HDR | Activá *Colores de videos HDR* y usá un ffmpeg con `zscale` y `tonemap` |
| Subtítulo PGS que no aparece | Elegilo en el selector (modo automático prefiere los de texto). Si no se ve, ejecutá `npm start` y revisá los errores de ffmpeg; probá con otra pista |
| Los botones de volumen mueven el volumen del celular | Solo controlan el Chromecast con la app abierta y un video reproduciéndose |

Para ver errores y la velocidad de ffmpeg, ejecutá `npm start` desde una terminal.

---

## API HTTP

Todo lo que hace el celular pasa por una API HTTP simple, útil si querés hacer tu propio cliente.

- Con PIN: `POST /api/login` con `{"pin": "..."}` devuelve un `token`; el resto de las llamadas lo envían en el header `x-token`.

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

- Solo Windows en la versión portable; el servidor Node.js podría correr en otros sistemas, pero no está probado.
- La conversión en vivo depende de la potencia de la PC. Los 4K HDR con corrección de color son lo más exigente.
- Dolby Vision perfil 5 no se corrige bien.
- En los subtítulos de imagen (PGS / VobSub) solo se puede cambiar el tamaño, no el color ni el estilo.
- Si cambiás el volumen con el control remoto de la TV, la barra de MKVCast no se entera.
- Solo funciona dentro de la misma red local (o con VPN).
- La resolución máxima, los fps y la GPU se cambian desde *Ajustes* en la PC, no desde el celular.

---

## Cómo se hizo

MKVCast fue desarrollado por [bareinn](https://github.com/bareinn), con ayuda de herramientas de inteligencia artificial:

- **Claude** (Anthropic): servidor, interfaz de escritorio y web móvil, transcodificación con ffmpeg, subtítulos y documentación.
- **Gemini** (Google AI Studio): generación de la app Android a partir de una especificación escrita para ese fin.

Las decisiones de diseño, las pruebas en dispositivos reales y la revisión del resultado las hice yo.

---

## Contribuir

Los *issues* y *pull requests* son bienvenidos. Si encontrás un error, contá en el *issue*:

- qué hiciste y qué esperabas que pasara,
- el formato del video (códec, resolución, tipo de subtítulos),
- lo que muestra la terminal al ejecutar `npm start`, si es un problema de reproducción.

---

## Licencia

Distribuido bajo la licencia MIT. Ver el archivo [LICENSE](LICENSE) para más información.

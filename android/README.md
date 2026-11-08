# MKVCast Remote (app Android)

Control remoto nativo para [MKVCast](../README.md). No reproduce video: se conecta por wifi al programa de la PC
y le envía las órdenes (reproducir, pausa, volumen, subtítulos, cola, playlists, etc.).

- Android 7.0 o superior (minSdk 24)
- Kotlin + Jetpack Compose (Material 3), OkHttp y Coil; sesión multimedia con AndroidX Media (MediaSessionCompat)
- Hecha con ayuda de IA: generada a partir de una especificación con Gemini (Google AI Studio) y revisada con Claude

## Qué hace

- Biblioteca, cola, playlists y "Continuar viendo"
- Reproductor con pausa, ±10 s, anterior/siguiente, barra de progreso, audio y subtítulos
- Botones físicos de volumen para el volumen del Chromecast
- **Notificación multimedia** con la pantalla apagada o la app en segundo plano: pausa, ±10 s, capítulo anterior/siguiente,
  barra de progreso y volumen con las teclas físicas del celular

## Instalar el APK

Descargá `MKVCast.apk` desde la sección **Releases** del repositorio e instalalo en el celular
(hay que permitir "instalar apps de orígenes desconocidos").

Abrí la app, escribí la dirección que muestra MKVCast en *Ajustes → Control desde el celular*
(por ejemplo `192.168.1.20:8099`) y, si configuraste un PIN, ingresalo.

En Android 13 o superior, permití las notificaciones para ver los controles. Si el sistema cierra la notificación con la
pantalla apagada, desactivá la optimización de batería para MKVCast (hay un acceso directo en *Ajustes* de la app).

## Compilar desde el código

1. Instalá [Android Studio](https://developer.android.com/studio) y abrí esta carpeta (`android/`).
2. Dejá que sincronice Gradle y ejecutá en un emulador o celular, o generá el APK con `Build → Build APK(s)`.

Los APK *debug* se firman con la clave de depuración de Android Studio. Para una versión *release* firmada, creá tu propio
keystore con `Build → Generate Signed Bundle / APK` (los archivos `*.jks` y `*.keystore` están en el `.gitignore`).

## Estructura

```
app/src/main/java/com/example/
├── data/api/          Cliente HTTP de MKVCast (MKVCastApiClient)
├── data/model/        Modelos (estado, cola, subtítulos, ajustes)
├── data/local/        Dirección del servidor y PIN (SharedPreferences)
├── data/repository/   Estado compartido y sondeo del servidor (PlaybackRepository)
├── service/           Servicio y notificación multimedia
└── ui/                Pantallas (Biblioteca, Cola, Playlists, Ajustes) y reproductor
```

La API que usa la app está descrita en la sección *API HTTP* del [README principal](../README.md).

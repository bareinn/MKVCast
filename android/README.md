# MKVCast Remote (app Android)

Control remoto nativo para [MKVCast](../README.md). No reproduce video: se conecta por wifi al programa de la PC
y le envía las órdenes (reproducir, pausa, volumen, subtítulos, cola, playlists, etc.).

- Android 7.0 o superior (minSdk 24)
- Kotlin + Jetpack Compose (Material 3), OkHttp y Coil
- La app generada a partir de una especificación con Gemini (Google AI Studio) y revisada con Claude

## Instalar el APK

Descargá `MKVCast.apk` desde la sección **Releases** del repositorio e instalalo en el celular
(hay que permitir "instalar apps de orígenes desconocidos").

Abrí la app, escribí la dirección que muestra MKVCast en *Ajustes → Control desde el celular*
(por ejemplo `192.168.1.20:8099`) y, si configuraste un PIN, ingresalo.

## Compilar desde el código

1. Instalá [Android Studio](https://developer.android.com/studio) y abrí esta carpeta (`android/`).
2. El proyecto firma las compilaciones *debug* con un archivo `debug.keystore` que no está en el repositorio.
   Para compilar sin él, en `app/build.gradle.kts` quitá esta línea del bloque `debug`:

   ```kotlin
   signingConfig = signingConfigs.getByName("debugConfig")
   ```

3. Ejecutá en un emulador o celular, o generá el APK con `Build → Build APK(s)`.

## Estructura

```
app/src/main/java/com/example/
├── data/api/       Cliente HTTP de MKVCast (MKVCastApiClient)
├── data/model/     Modelos (estado, cola, subtítulos, ajustes)
├── data/local/     Dirección del servidor y PIN (SharedPreferences)
└── ui/             Pantallas (Biblioteca, Cola, Playlists, Ajustes) y reproductor
```

La API que usa la app está descrita en la sección *API HTTP* del [README principal](../README.md).

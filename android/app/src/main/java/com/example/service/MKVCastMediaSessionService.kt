package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media.VolumeProviderCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.MainActivity
import com.example.R
import com.example.data.model.ServerState
import com.example.data.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Servicio en primer plano con una MediaSession clásica (MediaSessionCompat):
 * - notificación multimedia con botones, carátula y barra de progreso arrastrable,
 * - teclas físicas de volumen enrutadas al Chromecast (reproducción remota) con la pantalla apagada.
 * No reproduce nada en el celular: solo refleja el estado del servidor y le manda órdenes.
 */
class MKVCastMediaSessionService : Service() {

    private lateinit var repository: PlaybackRepository
    private lateinit var session: MediaSessionCompat
    private lateinit var volumeProvider: VolumeProviderCompat

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateObservationJob: Job? = null
    private var artworkJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var pauseLockReleaseJob: Job? = null

    private var isForegroundNow = true

    // Carátula y metadatos ya publicados (para no repetir trabajo en cada sondeo)
    private var artwork: Bitmap? = null
    private var artworkKey: String? = null
    private var metadataKey: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> repository.setScreenOn(true)
                Intent.ACTION_SCREEN_OFF -> repository.setScreenOn(false)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        repository = PlaybackRepository.getInstance(applicationContext)

        createNotificationChannel()
        // Android exige llamar a startForeground() pocos segundos después de startForegroundService().
        startForegroundNow()

        createSession()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        initLocks()
        observeServerState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> { stopPlaybackAndService(); return START_NOT_STICKY }
            ACTION_STOP_CAST -> { repository.stopPlayback(); return START_NOT_STICKY }
            ACTION_TOGGLE -> { repository.togglePlayPause(); return START_NOT_STICKY }
            ACTION_PREV -> { repository.skipPrevious(); return START_NOT_STICKY }
            ACTION_NEXT -> { repository.skipNext(); return START_NOT_STICKY }
            ACTION_BACK -> { repository.seekRelative(-10.0); return START_NOT_STICKY }
            ACTION_FWD -> { repository.seekRelative(10.0); return START_NOT_STICKY }
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- Sesión multimedia

    private fun openAppPendingIntent(requestCode: Int): PendingIntent {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra(MainActivity.EXTRA_OPEN_EXPANDED_PLAYER, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, requestCode, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createSession() {
        session = MediaSessionCompat(this, "MKVCastSession").apply {
            setSessionActivity(openAppPendingIntent(0))
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    val st = repository.serverState.value?.status
                    if (st != null && st.isPaused) repository.togglePlayPause()
                }
                override fun onPause() {
                    val st = repository.serverState.value?.status
                    if (st != null && (st.isPlaying || st.isBuffering)) repository.togglePlayPause()
                }
                override fun onStop() { repository.stopPlayback() }
                override fun onSkipToNext() { repository.skipNext() }
                override fun onSkipToPrevious() { repository.skipPrevious() }
                override fun onSeekTo(pos: Long) { repository.seekTo(pos / 1000.0) }
                override fun onRewind() { repository.seekRelative(-10.0) }
                override fun onFastForward() { repository.seekRelative(10.0) }
                override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                    when (action) {
                        ACTION_BACK -> repository.seekRelative(-10.0)
                        ACTION_FWD -> repository.seekRelative(10.0)
                        ACTION_STOP_CAST -> repository.stopPlayback()
                    }
                }
            })
            isActive = true
        }

        // Reproducción REMOTA: Android manda las teclas de volumen a esta sesión (también con la pantalla apagada)
        val startVol = ((repository.serverState.value?.status?.volume ?: 0.5f) * 100).roundToInt().coerceIn(0, 100)
        volumeProvider = object : VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE, 100, startVol) {
            override fun onSetVolumeTo(volume: Int) {
                repository.setVolume((volume / 100f).coerceIn(0f, 1f))
                currentVolume = volume.coerceIn(0, 100)
            }
            override fun onAdjustVolume(direction: Int) {
                if (direction == 0) return
                repository.changeVolume(if (direction > 0) 0.05f else -0.05f)
                val v = (repository.uiVolume.value ?: repository.serverState.value?.status?.volume ?: 0.5f)
                currentVolume = (v * 100).roundToInt().coerceIn(0, 100)
            }
        }
        session.setPlaybackToRemote(volumeProvider)
    }

    private fun updateSession(state: ServerState?) {
        val now = state?.now ?: return
        val status = state.status

        val title = if (now.name.contains('.')) now.name.substringBeforeLast('.') else now.name
        val device = (state.device ?: "").ifBlank { "Chromecast" }
        val durationMs = (now.duration.coerceAtLeast(1.0) * 1000.0).toLong()

        // Metadatos: solo se republican si cambia algo
        val mKey = "${now.r}_${now.p}|$title|$device|$durationMs|${artwork?.hashCode()}"
        if (mKey != metadataKey) {
            metadataKey = mKey
            val md = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Chromecast: $device")
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "Chromecast: $device")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
            artwork?.let {
                md.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                md.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
                md.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
            }
            session.setMetadata(md.build())
        }

        val queue = state.queue
        val hasPrev = state.qi in queue.indices && state.qi > 0
        val hasNext = state.qi in queue.indices && state.qi < queue.size - 1

        var actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_FAST_FORWARD
        if (hasPrev) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        if (hasNext) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT

        val playing = status.isPlaying
        val stateInt = when {
            status.isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            status.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            status.isPaused -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_NONE
        }
        val posMs = (status.pos * 1000).toLong().coerceIn(0L, durationMs)
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .addCustomAction(ACTION_BACK, "-10 s", R.drawable.ic_replay_10)
                .addCustomAction(ACTION_FWD, "+10 s", R.drawable.ic_forward_10)
                .addCustomAction(ACTION_STOP_CAST, "Cortar transmisión", R.drawable.ic_stop_cast)
                .setState(stateInt, posMs, if (playing) 1f else 0f, SystemClock.elapsedRealtime())
                .build()
        )

        // El volumen que ve Android sigue al del servidor
        val vol = ((repository.uiVolume.value ?: status.volume ?: 0.5f) * 100).roundToInt().coerceIn(0, 100)
        if (volumeProvider.currentVolume != vol) volumeProvider.currentVolume = vol

        loadArtworkIfNeeded(state)
    }

    private fun loadArtworkIfNeeded(state: ServerState) {
        val now = state.now ?: return
        val key = "${now.r}_${now.p}"
        if (key == artworkKey) return
        artworkKey = key
        artwork = null
        artworkJob?.cancel()
        artworkJob = serviceScope.launch {
            try {
                val raw = repository.apiClient.fetchThumbnailBytes(now.r, now.p) ?: return@launch
                val bmp = withContext(Dispatchers.IO) {
                    val b = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@withContext null
                    val maxSide = 512
                    if (b.width > maxSide || b.height > maxSide) {
                        val k = maxSide.toFloat() / maxOf(b.width, b.height)
                        Bitmap.createScaledBitmap(
                            b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true
                        )
                    } else b
                }
                if (bmp != null && key == artworkKey) {
                    artwork = bmp
                    val st = repository.serverState.value
                    updateSession(st)
                    postNotification(st)
                }
            } catch (e: Exception) {
                // Sin carátula: se muestra el ícono por defecto
            }
        }
    }

    // ---------------------------------------------------------------- Notificación

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val i = Intent(this, MKVCastMediaSessionService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun postNotification(state: ServerState?) {
        val now = state?.now ?: return
        val status = state.status
        try {
            val playing = status.isPlaying || status.isBuffering
            val queue = state.queue
            val hasPrev = state.qi in queue.indices && state.qi > 0
            val hasNext = state.qi in queue.indices && state.qi < queue.size - 1

            val title = if (now.name.contains('.')) now.name.substringBeforeLast('.') else now.name
            val device = (state.device ?: "").ifBlank { "Chromecast" }

            val b = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(if (playing) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
                .setContentTitle(title)
                .setContentText("Chromecast: $device")
                .setContentIntent(openAppPendingIntent(0))
                .setDeleteIntent(actionIntent(ACTION_STOP_SERVICE, 9))
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            artwork?.let { b.setLargeIcon(it) }

            // Android muestra hasta 5 botones. Con capítulo anterior Y siguiente se omiten los de ±10 s
            // (para eso está la barra) y así siempre queda el botón de cortar la transmisión.
            val showSeekButtons = !(hasPrev && hasNext)
            var idx = 0
            val compact = ArrayList<Int>()
            if (hasPrev) { b.addAction(android.R.drawable.ic_media_previous, "Anterior", actionIntent(ACTION_PREV, 1)); compact.add(idx); idx++ }
            if (showSeekButtons) { b.addAction(R.drawable.ic_replay_10, "-10 s", actionIntent(ACTION_BACK, 2)); idx++ }
            b.addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pausa" else "Reproducir", actionIntent(ACTION_TOGGLE, 3)
            ); compact.add(idx); idx++
            if (showSeekButtons) { b.addAction(R.drawable.ic_forward_10, "+10 s", actionIntent(ACTION_FWD, 4)); idx++ }
            if (hasNext) { b.addAction(android.R.drawable.ic_media_next, "Siguiente", actionIntent(ACTION_NEXT, 5)); compact.add(idx); idx++ }
            b.addAction(R.drawable.ic_stop_cast, "Cortar transmisión", actionIntent(ACTION_STOP_CAST, 6)); idx++
            if (compact.size < 3 && idx > compact.size) {
                // completar la vista compacta con los botones restantes (máximo 3)
                for (i in 0 until idx) { if (compact.size >= 3) break; if (i !in compact) compact.add(i) }
                compact.sort()
            }

            b.setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(*compact.take(3).toIntArray())
            )
            val notification = b.build()

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else 0
            if (playing) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
                isForegroundNow = true
            } else {
                // En pausa se suelta el primer plano para poder descartar la notificación deslizándola
                if (isForegroundNow) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                    isForegroundNow = false
                }
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Si algo falla acá, el volumen y los controles de la sesión siguen funcionando
        }
    }

    private fun startForegroundNow() {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("MKVCast")
                .setContentText("Conectando con el Chromecast…")
                .setContentIntent(openAppPendingIntent(1))
                .setOngoing(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .build()
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            // Si el sistema no permite primer plano, el servicio se detiene solo más adelante
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = descriptionText
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // ---------------------------------------------------------------- Bloqueos (pantalla apagada)

    private fun initLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MKVCast:MediaPlaybackWakeLock"
            ).apply { setReferenceCounted(false) }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager.createWifiLock(wifiMode, "MKVCast:MediaPlaybackWifiLock").apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            // Ignorar errores de creación en equipos restrictivos
        }
    }

    private fun acquireLocks() {
        pauseLockReleaseJob?.cancel()
        pauseLockReleaseJob = null
        try {
            wakeLock?.let { if (!it.isHeld) it.acquire(60 * 60 * 1000L) }
            wifiLock?.let { if (!it.isHeld) it.acquire() }
        } catch (e: Exception) {
            // Ignorar
        }
    }

    private fun schedulePauseLockRelease() {
        if (pauseLockReleaseJob?.isActive == true) return
        pauseLockReleaseJob = serviceScope.launch {
            // Tras 10 minutos en pausa se sueltan los bloqueos para no gastar batería
            delay(10 * 60 * 1000L)
            releaseLocks()
        }
    }

    private fun releaseLocks() {
        pauseLockReleaseJob?.cancel()
        pauseLockReleaseJob = null
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (e: Exception) { }
        try { wifiLock?.let { if (it.isHeld) it.release() } } catch (e: Exception) { }
    }

    // ---------------------------------------------------------------- Estado del servidor

    private fun observeServerState() {
        stateObservationJob?.cancel()
        stateObservationJob = serviceScope.launch {
            repository.serverState.collect { state ->
                val now = state?.now
                val status = state?.status

                if (now == null || status == null || status.isIdle) {
                    removeNotificationAndStop()
                } else {
                    updateSession(state)
                    postNotification(state)
                    if (status.isPlaying || status.isBuffering) {
                        acquireLocks()
                    } else if (status.isPaused) {
                        schedulePauseLockRelease()
                    }
                }
            }
        }
    }

    /** Quita la notificación (aunque esté "suelta" por la pausa) y cierra el servicio. */
    private fun removeNotificationAndStop() {
        releaseLocks()
        try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (e: Exception) { }
        try { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID) } catch (e: Exception) { }
        isForegroundNow = false
        stopSelf()
    }

    private fun stopPlaybackAndService() {
        // Solo se cierra el servicio; NO se manda "stop" al servidor
        repository.blockServiceStart(8000L)
        removeNotificationAndStop()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val status = repository.serverState.value?.status
        if (status == null || status.isPaused || status.isIdle) {
            stopPlaybackAndService()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        stateObservationJob?.cancel()
        artworkJob?.cancel()
        releaseLocks()
        try { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID) } catch (e: Exception) { }
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) { }
        try {
            session.isActive = false
            session.release()
        } catch (e: Exception) { }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "mkvcast_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_SERVICE"
        const val ACTION_STOP_CAST = "com.example.service.ACTION_STOP_CAST"
        const val ACTION_TOGGLE = "com.example.service.ACTION_TOGGLE"
        const val ACTION_PREV = "com.example.service.ACTION_PREV"
        const val ACTION_NEXT = "com.example.service.ACTION_NEXT"
        const val ACTION_BACK = "com.example.service.ACTION_BACK"
        const val ACTION_FWD = "com.example.service.ACTION_FWD"

        /** true mientras el servicio existe; evita llamar a startForegroundService() en cada sondeo. */
        @Volatile
        var isRunning: Boolean = false
    }
}

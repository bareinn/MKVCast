package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.data.api.ApiException
import com.example.data.api.AuthException
import com.example.data.api.MKVCastApiClient
import com.example.data.local.ServerPreferences
import com.example.data.model.NowPlaying
import com.example.data.model.PlaybackStatus
import com.example.data.model.ServerSettings
import com.example.data.model.ServerState
import com.example.service.MKVCastMediaSessionService
import com.example.ui.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class PlaybackRepository private constructor(private val appContext: Context) {

    val preferences = ServerPreferences(appContext)
    val apiClient = MKVCastApiClient(preferences)

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _serverState = MutableStateFlow<ServerState?>(null)
    val serverState: StateFlow<ServerState?> = _serverState.asStateFlow()

    private val _isServerReachable = MutableStateFlow(true)
    val isServerReachable: StateFlow<Boolean> = _isServerReachable.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _uiVolume = MutableStateFlow<Float?>(null)
    val uiVolume: StateFlow<Float?> = _uiVolume.asStateFlow()
    private var volumeResetJob: Job? = null

    // Polling control
    private var pollingJob: Job? = null
    private var isAppForeground = true
    private var isScreenOn = true

    // Server unreachable tracking (threshold ~60s)
    private var firstUnreachableTime: Long? = null

    init {
        if (preferences.serverUrl.isNotBlank()) {
            connectServer(preferences.serverUrl, preferences.lastPin)
        }
    }

    fun setAppForeground(foreground: Boolean) {
        isAppForeground = foreground
        adjustPollingState()
    }

    fun setScreenOn(screenOn: Boolean) {
        isScreenOn = screenOn
    }

    private fun adjustPollingState() {
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            startPolling()
        }
    }

    fun connectServer(rawUrl: String, pin: String = "") {
        val normalized = ServerPreferences.normalizeUrl(rawUrl)
        if (normalized.isBlank()) {
            _errorMessage.value = "Por favor ingresá la dirección de la PC (ej: 192.168.1.20:8099)"
            _connectionStatus.value = ConnectionStatus.ERROR
            return
        }

        preferences.serverUrl = normalized
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _errorMessage.value = null

        applicationScope.launch {
            if (pin.isNotBlank()) {
                val loginRes = apiClient.login(pin)
                if (loginRes.isFailure) {
                    val ex = loginRes.exceptionOrNull()
                    if (ex is AuthException) {
                        _connectionStatus.value = ConnectionStatus.AUTH_REQUIRED
                        _errorMessage.value = "PIN incorrecto. Por favor ingresá el PIN configurado en MKVCast."
                        return@launch
                    }
                }
            }

            val stateRes = apiClient.getState()
            if (stateRes.isSuccess) {
                val state = stateRes.getOrNull()
                _serverState.value = state
                _connectionStatus.value = ConnectionStatus.CONNECTED
                _isServerReachable.value = true
                firstUnreachableTime = null
                startPolling()
                checkAndStartMediaService(state)
            } else {
                val ex = stateRes.exceptionOrNull()
                if (ex is AuthException) {
                    _connectionStatus.value = ConnectionStatus.AUTH_REQUIRED
                    _errorMessage.value = "Este servidor requiere PIN de acceso."
                } else {
                    _connectionStatus.value = ConnectionStatus.ERROR
                    _errorMessage.value = "No pude conectar con $normalized. Verificá que la PC esté encendida, que MKVCast esté abierto y que estén en la misma red wifi."
                }
            }
        }
    }

    fun submitPin(pin: String) {
        applicationScope.launch {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            val loginRes = apiClient.login(pin)
            if (loginRes.isSuccess) {
                val stateRes = apiClient.getState()
                if (stateRes.isSuccess) {
                    val state = stateRes.getOrNull()
                    _serverState.value = state
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    _isServerReachable.value = true
                    firstUnreachableTime = null
                    startPolling()
                    checkAndStartMediaService(state)
                } else {
                    _connectionStatus.value = ConnectionStatus.ERROR
                    _errorMessage.value = "Error al obtener estado del servidor."
                }
            } else {
                _connectionStatus.value = ConnectionStatus.AUTH_REQUIRED
                _errorMessage.value = "PIN incorrecto. Intentá nuevamente."
            }
        }
    }

    fun disconnect() {
        stopPolling()
        preferences.clearAuth()
        _serverState.value = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        firstUnreachableTime = null
        stopMediaService()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }

    fun showUserMessage(message: String) {
        _userMessage.value = message
    }

    fun showErrorMessage(message: String) {
        _errorMessage.value = message
    }

    private fun startPolling() {
        stopPolling()
        pollingJob = applicationScope.launch {
            while (true) {
                if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
                    try {
                        val stateRes = apiClient.getState()
                        if (stateRes.isSuccess) {
                            val state = stateRes.getOrNull()
                            _serverState.value = state
                            _isServerReachable.value = true
                            firstUnreachableTime = null
                            checkAndStartMediaService(state)
                        } else {
                            handleUnreachable(stateRes.exceptionOrNull())
                        }
                    } catch (e: Exception) {
                        handleUnreachable(e)
                    }
                }

                // Polling interval: 2s when foreground or screen on, 3s when background with screen off
                val pollDelay = if (isAppForeground || isScreenOn) 2000L else 3000L
                delay(pollDelay)
            }
        }
    }

    private fun handleUnreachable(ex: Throwable?) {
        if (ex is AuthException) {
            _connectionStatus.value = ConnectionStatus.AUTH_REQUIRED
            _errorMessage.value = "La sesión expiró o se requiere PIN."
            stopPolling()
            stopMediaService()
            return
        }

        _isServerReachable.value = false
        val now = SystemClock.elapsedRealtime()
        if (firstUnreachableTime == null) {
            firstUnreachableTime = now
        } else if (now - (firstUnreachableTime ?: now) > 60_000L) {
            // Unreachable for more than 60s continuously
            stopMediaService()
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // Evita que un sondeo "viejo" (en vuelo mientras se cortaba) vuelva a abrir la notificación
    @Volatile
    private var serviceStartBlockedUntil = 0L

    // Capítulo que el usuario cortó con la X: no se vuelve a mostrar su notificación hasta que
    // el servidor quede inactivo, cambie de capítulo o el usuario inicie algo desde la app.
    @Volatile
    private var dismissedKey: String? = null

    fun blockServiceStart(ms: Long) {
        serviceStartBlockedUntil = SystemClock.elapsedRealtime() + ms
    }

    private fun checkAndStartMediaService(state: ServerState?) {
        val now = state?.now
        val status = state?.status
        val curKey = now?.let { "${it.r}_${it.p}" }
        // Se olvida el "cortado" cuando el servidor queda inactivo o pasa a otro capítulo
        if (dismissedKey != null && (curKey == null || status?.isIdle == true || curKey != dismissedKey)) {
            dismissedKey = null
        }
        // Solo se arranca en primer plano cuando está reproduciendo (en pausa, Media3 no pasa a primer plano
        // y Android cerraría la app por no llamar a startForeground a tiempo). Si ya estaba corriendo, sigue en pausa.
        if (now != null && status != null && (status.isPlaying || status.isBuffering)) {
            if (MKVCastMediaSessionService.isRunning) return
            if (SystemClock.elapsedRealtime() < serviceStartBlockedUntil) return
            if (curKey != null && curKey == dismissedKey) return
            try {
                val intent = Intent(appContext, MKVCastMediaSessionService::class.java)
                ContextCompat.startForegroundService(appContext, intent)
            } catch (e: Exception) {
                // If system restricts starting foreground service in background, it will start on next foreground
            }
        } else if (now == null || status?.isIdle == true) {
            stopMediaService()
        }
    }

    fun stopMediaService() {
        try {
            appContext.stopService(Intent(appContext, MKVCastMediaSessionService::class.java))
        } catch (e: Exception) {
            // Service might already be stopped
        }
    }

    // Playback control operations

    fun playVideo(r: Int, p: String, audio: Int? = null, sub: Int? = null, start: Double? = null) {
        serviceStartBlockedUntil = 0L
        dismissedKey = null
        applicationScope.launch {
            val res = apiClient.play(r, p, audio, sub, start)
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al iniciar reproducción"
            } else {
                refreshStateImmediately()
            }
        }
    }

    fun resumeVideo(r: Int, p: String) {
        playVideo(r, p)
    }

    fun togglePlayPause() {
        val status = _serverState.value?.status ?: return
        val cmd = if (status.isPlaying) "pause" else "resume"
        serviceStartBlockedUntil = 0L
        dismissedKey = null
        applicationScope.launch {
            apiClient.control(cmd)
            refreshStateImmediately()
        }
    }

    fun stopPlayback() {
        blockServiceStart(8000L)
        _serverState.value?.now?.let { dismissedKey = "${it.r}_${it.p}" }
        applicationScope.launch {
            apiClient.control("stop")
            refreshStateImmediately()
            stopMediaService()
        }
    }

    fun skipPrevious() {
        applicationScope.launch {
            apiClient.control("prev")
            refreshStateImmediately()
        }
    }

    fun skipNext() {
        applicationScope.launch {
            apiClient.control("next")
            refreshStateImmediately()
        }
    }

    fun seekRelative(secondsDelta: Double) {
        val currentPos = _serverState.value?.status?.pos ?: 0.0
        val target = (currentPos + secondsDelta).coerceAtLeast(0.0)
        seekTo(target)
    }

    fun seekTo(seconds: Double) {
        applicationScope.launch {
            apiClient.control("seek", to = seconds)
            refreshStateImmediately()
        }
    }

    fun setVolume(volume: Float) {
        applicationScope.launch {
            apiClient.control("volume", value = volume.toDouble().coerceIn(0.0, 1.0))
        }
    }

    fun shouldHandleVolumeKeys(): Boolean =
        _serverState.value?.now != null &&
        _connectionStatus.value == ConnectionStatus.CONNECTED

    fun changeVolume(delta: Float) {
        val base = _uiVolume.value ?: _serverState.value?.status?.volume ?: 0.5f
        val next = (base + delta).coerceIn(0f, 1f)
        _uiVolume.value = next
        setVolume(next)
        volumeResetJob?.cancel()
        volumeResetJob = applicationScope.launch {
            delay(3000)
            _uiVolume.value = null
        }
    }

    fun refreshStateImmediately() {
        applicationScope.launch {
            try {
                val stateRes = apiClient.getState()
                if (stateRes.isSuccess) {
                    val state = stateRes.getOrNull()
                    _serverState.value = state
                    _isServerReachable.value = true
                    firstUnreachableTime = null
                    checkAndStartMediaService(state)
                }
            } catch (e: Exception) {
                // Ignore transient errors
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PlaybackRepository? = null

        fun getInstance(context: Context): PlaybackRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaybackRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

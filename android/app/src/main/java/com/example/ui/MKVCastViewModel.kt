package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ApiException
import com.example.data.api.AuthException
import com.example.data.api.MKVCastApiClient
import com.example.data.local.ServerPreferences
import com.example.data.model.FolderContent
import com.example.data.model.NowPlaying
import com.example.data.model.PlaylistItem
import com.example.data.model.QueueItem
import com.example.data.model.ServerSettings
import com.example.data.model.ServerState
import com.example.data.model.SubtitleStyle
import com.example.data.model.SubtitleTrack
import com.example.data.model.VideoFile
import com.example.data.model.VideoInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.InputStream
import java.util.Locale

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTH_REQUIRED,
    ERROR
}

data class Breadcrumb(
    val name: String,
    val path: String
)

class MKVCastViewModel(application: Application) : AndroidViewModel(application) {
    val preferences = ServerPreferences(application)
    val apiClient = MKVCastApiClient(preferences)

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

    // Library explorer state
    private val _currentFolderIndex = MutableStateFlow<Int?>(null)
    val currentFolderIndex: StateFlow<Int?> = _currentFolderIndex.asStateFlow()

    private val _currentFolderPath = MutableStateFlow("")
    val currentFolderPath: StateFlow<String> = _currentFolderPath.asStateFlow()

    private val _breadcrumbs = MutableStateFlow<List<Breadcrumb>>(emptyList())
    val breadcrumbs: StateFlow<List<Breadcrumb>> = _breadcrumbs.asStateFlow()

    private val _currentFolderContent = MutableStateFlow(FolderContent())
    val currentFolderContent: StateFlow<FolderContent> = _currentFolderContent.asStateFlow()

    private val _isFolderLoading = MutableStateFlow(false)
    val isFolderLoading: StateFlow<Boolean> = _isFolderLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Video Options Dialog State
    private val _optionsVideo = MutableStateFlow<Pair<Int, VideoFile>?>(null)
    val optionsVideo: StateFlow<Pair<Int, VideoFile>?> = _optionsVideo.asStateFlow()

    private val _optionsVideoInfo = MutableStateFlow<VideoInfo?>(null)
    val optionsVideoInfo: StateFlow<VideoInfo?> = _optionsVideoInfo.asStateFlow()

    private val _isLoadingInfo = MutableStateFlow(false)
    val isLoadingInfo: StateFlow<Boolean> = _isLoadingInfo.asStateFlow()

    // Playlist Dialog State
    private val _videoToAddToPlaylist = MutableStateFlow<Pair<Int, String>?>(null)
    val videoToAddToPlaylist: StateFlow<Pair<Int, String>?> = _videoToAddToPlaylist.asStateFlow()

    // Upload state
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    // Expanded Player State
    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    // Subtitle delay debounce
    private var subDelayJob: Job? = null
    private val _pendingSubDelay = MutableStateFlow<Double?>(null)
    val pendingSubDelay: StateFlow<Double?> = _pendingSubDelay.asStateFlow()

    private val _uiVolume = MutableStateFlow<Float?>(null)
    val uiVolume: StateFlow<Float?> = _uiVolume.asStateFlow()
    private var volumeResetJob: Job? = null

    // Polling job
    private var pollingJob: Job? = null
    private var isAppForeground = true

    init {
        // If serverUrl was previously saved, attempt auto-connection
        if (preferences.serverUrl.isNotBlank()) {
            connectServer(preferences.serverUrl, preferences.lastPin)
        }
    }

    fun setAppForeground(foreground: Boolean) {
        isAppForeground = foreground
        if (foreground) {
            startPolling()
        } else {
            stopPolling()
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

        viewModelScope.launch {
            // First check if a PIN login is needed
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
                _serverState.value = stateRes.getOrNull()
                _connectionStatus.value = ConnectionStatus.CONNECTED
                _isServerReachable.value = true
                startPolling()
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
        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            val loginRes = apiClient.login(pin)
            if (loginRes.isSuccess) {
                val stateRes = apiClient.getState()
                if (stateRes.isSuccess) {
                    _serverState.value = stateRes.getOrNull()
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    _isServerReachable.value = true
                    startPolling()
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
        _currentFolderIndex.value = null
        _currentFolderPath.value = ""
        _breadcrumbs.value = emptyList()
        _currentFolderContent.value = FolderContent()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    private fun startPolling() {
        stopPolling()
        pollingJob = viewModelScope.launch {
            while (true) {
                if (isAppForeground && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                    val stateRes = apiClient.getState()
                    if (stateRes.isSuccess) {
                        _serverState.value = stateRes.getOrNull()
                        _isServerReachable.value = true
                    } else {
                        val ex = stateRes.exceptionOrNull()
                        if (ex is AuthException) {
                            _connectionStatus.value = ConnectionStatus.AUTH_REQUIRED
                            _errorMessage.value = "La sesión expiró o se requiere PIN."
                            break
                        } else {
                            _isServerReachable.value = false
                        }
                    }
                }
                delay(2000)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // --- Library & Navigation ---

    fun openFolder(folderIndex: Int, path: String = "") {
        _currentFolderIndex.value = folderIndex
        _currentFolderPath.value = path
        updateBreadcrumbs(folderIndex, path)
        loadFolderContent(folderIndex, path)
    }

    fun navigateUp(): Boolean {
        val r = _currentFolderIndex.value ?: return false
        val p = _currentFolderPath.value
        if (p.isEmpty()) {
            _currentFolderIndex.value = null
            _currentFolderPath.value = ""
            _breadcrumbs.value = emptyList()
            _currentFolderContent.value = FolderContent()
            return true
        }

        val normalized = p.replace('\\', '/')
        val parent = if (normalized.contains('/')) normalized.substringBeforeLast('/') else ""
        openFolder(r, parent)
        return true
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun updateBreadcrumbs(r: Int, path: String) {
        val state = _serverState.value ?: return
        val rootName = if (r in state.folders.indices) {
            if (r == state.uploadsIdx) "Soltados" else state.folders[r].replace('\\', '/').substringAfterLast('/').ifBlank { state.folders[r] }
        } else "Carpeta $r"

        val list = mutableListOf(Breadcrumb(rootName, ""))
        if (path.isNotEmpty()) {
            val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
            var acc = ""
            for (part in parts) {
                acc = if (acc.isEmpty()) part else "$acc/$part"
                list.add(Breadcrumb(part, acc))
            }
        }
        _breadcrumbs.value = list
    }

    fun loadFolderContent(r: Int, p: String) {
        viewModelScope.launch {
            _isFolderLoading.value = true
            val res = apiClient.getLs(r, p)
            if (res.isSuccess) {
                _currentFolderContent.value = res.getOrNull() ?: FolderContent()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al cargar carpeta"
            }
            _isFolderLoading.value = false
        }
    }

    fun refreshCurrentFolder() {
        val r = _currentFolderIndex.value ?: return
        loadFolderContent(r, _currentFolderPath.value)
    }

    // --- Video Options Dialog ---

    fun openVideoOptions(r: Int, file: VideoFile) {
        _optionsVideo.value = Pair(r, file)
        _optionsVideoInfo.value = null
        _isLoadingInfo.value = true

        viewModelScope.launch {
            val res = apiClient.getInfo(r, file.p)
            if (res.isSuccess) {
                _optionsVideoInfo.value = res.getOrNull()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al obtener info del video"
            }
            _isLoadingInfo.value = false
        }
    }

    fun closeVideoOptions() {
        _optionsVideo.value = null
        _optionsVideoInfo.value = null
    }

    fun pickDefaultSubtitleIndex(info: VideoInfo?, settings: ServerSettings): Int {
        if (info == null || !settings.autoSub) return -1
        val compatibleSubs = info.subs.filter { !it.isImageSubtitle }
        if (compatibleSubs.isEmpty()) return -1

        // 1. Default
        val defSub = compatibleSubs.firstOrNull { it.def }
        if (defSub != null) return defSub.i

        // 2. Forced
        val forcedSub = compatibleSubs.firstOrNull { it.forced }
        if (forcedSub != null) return forcedSub.i

        // 3. Spanish (spa, es)
        val spaSub = compatibleSubs.firstOrNull {
            val l = it.lang.lowercase()
            l.startsWith("spa") || l.startsWith("es")
        }
        if (spaSub != null) return spaSub.i

        return -1
    }

    // --- Controls & Playback ---

    fun playVideo(r: Int, p: String, audio: Int? = null, sub: Int? = null, start: Double? = null) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            apiClient.control(cmd)
            refreshStateImmediately()
        }
    }

    fun stopPlayback() {
        viewModelScope.launch {
            apiClient.control("stop")
            _isPlayerExpanded.value = false
            refreshStateImmediately()
        }
    }

    fun skipPrevious() {
        viewModelScope.launch {
            apiClient.control("prev")
            refreshStateImmediately()
        }
    }

    fun skipNext() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            apiClient.control("seek", to = seconds)
            refreshStateImmediately()
        }
    }

    fun setVolume(volume: Float) {
        viewModelScope.launch {
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
        volumeResetJob = viewModelScope.launch {
            delay(3000)
            _uiVolume.value = null
        }
    }

    // --- Subtitle Sync with Debounce ---

    fun adjustSubDelay(delta: Double) {
        val current = _pendingSubDelay.value ?: _serverState.value?.now?.delay ?: 0.0
        val next = (current + delta).coerceIn(-60.0, 60.0)
        _pendingSubDelay.value = next

        subDelayJob?.cancel()
        subDelayJob = viewModelScope.launch {
            delay(700)
            apiClient.subDelay(value = next)
            _pendingSubDelay.value = null
            refreshStateImmediately()
        }
    }

    fun resetSubDelay() {
        subDelayJob?.cancel()
        _pendingSubDelay.value = null
        viewModelScope.launch {
            apiClient.subDelay(reset = true)
            refreshStateImmediately()
        }
    }

    // --- Queue ---

    fun addToQueue(r: Int, p: String) {
        viewModelScope.launch {
            val res = apiClient.queue("add", r = r, p = p)
            if (res.isSuccess) {
                _userMessage.value = "Agregado a la cola"
                refreshStateImmediately()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al agregar a la cola"
            }
        }
    }

    fun removeFromQueue(index: Int) {
        viewModelScope.launch {
            apiClient.queue("remove", i = index)
            refreshStateImmediately()
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            apiClient.queue("clear")
            refreshStateImmediately()
        }
    }

    fun saveQueueAsPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("action", "saveQueue")
                put("name", name.trim())
            }
            val res = apiClient.playlistAction(payload)
            if (res.isSuccess) {
                _userMessage.value = "Playlist guardada"
                refreshStateImmediately()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al guardar playlist"
            }
        }
    }

    // --- Playlists ---

    fun openAddToPlaylist(r: Int, p: String) {
        _videoToAddToPlaylist.value = Pair(r, p)
    }

    fun closeAddToPlaylist() {
        _videoToAddToPlaylist.value = null
    }

    fun createPlaylist(name: String, initialItem: Pair<Int, String>? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("action", "create")
                put("name", name.trim())
                if (initialItem != null) {
                    val itemsArr = org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("r", initialItem.first)
                            put("p", initialItem.second)
                        })
                    }
                    put("items", itemsArr)
                }
            }
            val res = apiClient.playlistAction(payload)
            if (res.isSuccess) {
                _userMessage.value = "Playlist creada"
                closeAddToPlaylist()
                refreshStateImmediately()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al crear playlist"
            }
        }
    }

    fun addVideoToPlaylist(playlistId: String, r: Int, p: String) {
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("action", "add")
                put("id", playlistId)
                put("r", r)
                put("p", p)
            }
            val res = apiClient.playlistAction(payload)
            if (res.isSuccess) {
                _userMessage.value = "Video agregado a la playlist"
                closeAddToPlaylist()
                refreshStateImmediately()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al agregar video a playlist"
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("action", "delete")
                put("id", playlistId)
            }
            apiClient.playlistAction(payload)
            refreshStateImmediately()
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("action", "rename")
                put("id", playlistId)
                put("name", newName.trim())
            }
            apiClient.playlistAction(payload)
            refreshStateImmediately()
        }
    }

    fun removePlaylistItem(playlistId: String, index: Int) {
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("action", "remove")
                put("id", playlistId)
                put("i", index)
            }
            apiClient.playlistAction(payload)
            refreshStateImmediately()
        }
    }

    fun movePlaylistItem(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("action", "move")
                put("id", playlistId)
                put("i", fromIndex)
                put("to", toIndex)
            }
            apiClient.playlistAction(payload)
            refreshStateImmediately()
        }
    }

    fun loadPlaylist(playlistId: String, mode: String = "replace", play: Boolean = true, shuffle: Boolean = false) {
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("action", "load")
                put("id", playlistId)
                put("mode", mode)
                put("play", play)
                if (shuffle) put("shuffle", true)
            }
            val res = apiClient.playlistAction(payload)
            if (res.isSuccess) {
                _userMessage.value = if (shuffle) "Reproduciendo playlist mezclada 🔀" else "Cargando playlist..."
                refreshStateImmediately()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al cargar playlist"
            }
        }
    }

    // --- Seen & Continue Watching ---

    fun toggleSeen(r: Int, p: String, currentSeen: Boolean) {
        viewModelScope.launch {
            val res = apiClient.setSeen(r, p, !currentSeen)
            if (res.isSuccess) {
                refreshCurrentFolder()
                refreshStateImmediately()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al cambiar estado visto"
            }
        }
    }

    fun removeFromRecent(r: Int, p: String) {
        viewModelScope.launch {
            val res = apiClient.removeRecent(r, p)
            if (res.isSuccess) {
                refreshStateImmediately()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al quitar de recientes"
            }
        }
    }

    // --- Settings & Devices ---

    fun selectChromecastDevice(deviceName: String) {
        viewModelScope.launch {
            val res = apiClient.setDevice(deviceName)
            if (res.isSuccess) {
                _userMessage.value = "Dispositivo seleccionado: $deviceName"
                refreshStateImmediately()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al cambiar de dispositivo"
            }
        }
    }

    fun updateSettings(newSettings: ServerSettings) {
        val currentPos = _serverState.value?.status?.pos ?: 0.0
        val isVideoPlaying = _serverState.value?.now != null

        viewModelScope.launch {
            val res = apiClient.updateSettings(newSettings)
            if (res.isSuccess) {
                // If a video is playing, send seek to status.pos as MKVCast web does
                if (isVideoPlaying && currentPos > 0) {
                    delay(300)
                    apiClient.control("seek", to = currentPos)
                }
                refreshStateImmediately()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Error al actualizar ajustes"
            }
        }
    }

    // --- Upload from phone ---

    fun uploadVideoFromUri(uri: Uri) {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val context = getApplication<Application>()
                var fileName = "video.mkv"
                var fileSize: Long = 0

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                // Check extension
                val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val allowedExtensions = listOf("mkv", "mp4", "avi", "mov", "m4v", "webm")
                if (ext !in allowedExtensions) {
                    _errorMessage.value = "Formato no compatible ($ext). Formatos permitidos: mkv, mp4, avi, mov, m4v, webm."
                    _isUploading.value = false
                    return@launch
                }

                // Pre-check with /api/find to avoid re-uploading
                if (fileSize > 0) {
                    val findRes = apiClient.find(fileName, fileSize)
                    val existing = findRes.getOrNull()
                    if (existing != null) {
                        _userMessage.value = "El video ya existía en la PC. Iniciando reproducción..."
                        playVideo(existing.r, existing.p)
                        _isUploading.value = false
                        return@launch
                    }
                }

                // Upload
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _errorMessage.value = "No se pudo leer el archivo seleccionado"
                    _isUploading.value = false
                    return@launch
                }

                _userMessage.value = "Subiendo $fileName a la PC..."
                val uploadRes = apiClient.upload(fileName, inputStream, fileSize)
                if (uploadRes.isSuccess) {
                    val uploaded = uploadRes.getOrNull()
                    _userMessage.value = "Video subido con éxito"
                    if (uploaded != null) {
                        playVideo(uploaded.r, uploaded.p)
                    }
                    refreshStateImmediately()
                } else {
                    _errorMessage.value = uploadRes.exceptionOrNull()?.message ?: "Error al subir video"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error al procesar archivo: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    private fun refreshStateImmediately() {
        viewModelScope.launch {
            val stateRes = apiClient.getState()
            if (stateRes.isSuccess) {
                _serverState.value = stateRes.getOrNull()
                _isServerReachable.value = true
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}

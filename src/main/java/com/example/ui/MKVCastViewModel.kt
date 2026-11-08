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
import com.example.data.model.pickDefaultSubtitle
import com.example.data.repository.PlaybackRepository
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
    val repository = PlaybackRepository.getInstance(application)

    val preferences: ServerPreferences get() = repository.preferences
    val apiClient: MKVCastApiClient get() = repository.apiClient

    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus
    val serverState: StateFlow<ServerState?> = repository.serverState
    val isServerReachable: StateFlow<Boolean> = repository.isServerReachable
    val errorMessage: StateFlow<String?> = repository.errorMessage
    val userMessage: StateFlow<String?> = repository.userMessage
    val uiVolume: StateFlow<Float?> = repository.uiVolume

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

    fun setAppForeground(foreground: Boolean) {
        repository.setAppForeground(foreground)
    }

    fun connectServer(rawUrl: String, pin: String = "") {
        repository.connectServer(rawUrl, pin)
    }

    fun submitPin(pin: String) {
        repository.submitPin(pin)
    }

    fun disconnect() {
        repository.disconnect()
        _currentFolderIndex.value = null
        _currentFolderPath.value = ""
        _breadcrumbs.value = emptyList()
        _currentFolderContent.value = FolderContent()
    }

    fun dismissError() {
        repository.dismissError()
    }

    fun dismissUserMessage() {
        repository.dismissUserMessage()
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
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
        val state = serverState.value ?: return
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
                repository.showErrorMessage(res.exceptionOrNull()?.message ?: "Error al cargar carpeta")
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
                repository.showErrorMessage(res.exceptionOrNull()?.message ?: "Error al obtener info del video")
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
        return info.subs.pickDefaultSubtitle()?.i ?: -1
    }

    // --- Controls & Playback ---

    fun playVideo(r: Int, p: String, audio: Int? = null, sub: Int? = null, start: Double? = null) {
        repository.playVideo(r, p, audio, sub, start)
    }

    fun resumeVideo(r: Int, p: String) {
        repository.resumeVideo(r, p)
    }

    fun togglePlayPause() {
        repository.togglePlayPause()
    }

    fun stopPlayback() {
        _isPlayerExpanded.value = false
        repository.stopPlayback()
    }

    fun skipPrevious() {
        repository.skipPrevious()
    }

    fun skipNext() {
        repository.skipNext()
    }

    fun seekRelative(secondsDelta: Double) {
        repository.seekRelative(secondsDelta)
    }

    fun seekTo(seconds: Double) {
        repository.seekTo(seconds)
    }

    fun setVolume(volume: Float) {
        repository.setVolume(volume)
    }

    fun shouldHandleVolumeKeys(): Boolean = repository.shouldHandleVolumeKeys()

    fun changeVolume(delta: Float) {
        repository.changeVolume(delta)
    }

    // --- Subtitle Sync with Debounce ---

    fun adjustSubDelay(delta: Double) {
        val current = _pendingSubDelay.value ?: serverState.value?.now?.delay ?: 0.0
        val next = (current + delta).coerceIn(-60.0, 60.0)
        _pendingSubDelay.value = next

        subDelayJob?.cancel()
        subDelayJob = viewModelScope.launch {
            delay(700)
            apiClient.subDelay(value = next)
            _pendingSubDelay.value = null
            repository.refreshStateImmediately()
        }
    }

    fun resetSubDelay() {
        subDelayJob?.cancel()
        _pendingSubDelay.value = null
        viewModelScope.launch {
            apiClient.subDelay(reset = true)
            repository.refreshStateImmediately()
        }
    }

    // --- Queue ---

    fun addToQueue(r: Int, p: String) {
        viewModelScope.launch {
            val res = apiClient.queue("add", r = r, p = p)
            if (res.isSuccess) {
                repository.showUserMessage("Agregado a la cola")
                repository.refreshStateImmediately()
            } else {
                repository.showErrorMessage(res.exceptionOrNull()?.message ?: "Error al agregar a la cola")
            }
        }
    }

    fun removeFromQueue(index: Int) {
        viewModelScope.launch {
            apiClient.queue("remove", i = index)
            repository.refreshStateImmediately()
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            apiClient.queue("clear")
            repository.refreshStateImmediately()
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
                repository.showUserMessage("Playlist guardada")
                repository.refreshStateImmediately()
            } else {
                repository.showErrorMessage(res.exceptionOrNull()?.message ?: "Error al guardar playlist")
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
                repository.showUserMessage("Playlist creada")
                closeAddToPlaylist()
                repository.refreshStateImmediately()
            } else {
                repository.showErrorMessage(res.exceptionOrNull()?.message ?: "Error al crear playlist")
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
                repository.showUserMessage("Video agregado a la playlist")
                closeAddToPlaylist()
                repository.refreshStateImmediately()
            } else {
                repository.showErrorMessage(res.exceptionOrNull()?.message ?: "Error al agregar video a playlist")
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
            repository.refreshStateImmediately()
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
            repository.refreshStateImmediately()
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
            repository.refreshStateImmediately()
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
            repository.refreshStateImmediately()
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
                repository.showUserMessage(if (shuffle) "Reproduciendo playlist mezclada 🔀" else "Cargando playlist...")
                repository.refreshStateImmediately()
            } else {
                repository.showErrorMessage(res.exceptionOrNull()?.message ?: "Error al cargar playlist")
            }
        }
    }

    // --- Seen & Continue Watching ---

    fun toggleSeen(r: Int, p: String, currentSeen: Boolean) {
        viewModelScope.launch {
            val res = apiClient.setSeen(r, p, !currentSeen)
            if (res.isSuccess) {
                refreshCurrentFolder()
                repository.refreshStateImmediately()
            } else {
                repository.showErrorMessage(res.exceptionOrNull()?.message ?: "Error al cambiar estado visto")
            }
        }
    }

    fun removeFromRecent(r: Int, p: String) {
        viewModelScope.launch {
            val res = apiClient.removeRecent(r, p)
            if (res.isSuccess) {
                repository.refreshStateImmediately()
            } else {
                repository.showErrorMessage(res.exceptionOrNull()?.message ?: "Error al quitar de recientes")
            }
        }
    }

    // --- Settings & Devices ---

    fun selectChromecastDevice(deviceName: String) {
        viewModelScope.launch {
            val res = apiClient.setDevice(deviceName)
            if (res.isSuccess) {
                repository.showUserMessage("Dispositivo seleccionado: $deviceName")
                repository.refreshStateImmediately()
            } else {
                repository.showErrorMessage(res.exceptionOrNull()?.message ?: "Error al cambiar de dispositivo")
            }
        }
    }

    fun updateSettings(newSettings: ServerSettings) {
        val currentPos = serverState.value?.status?.pos ?: 0.0
        val isVideoPlaying = serverState.value?.now != null

        viewModelScope.launch {
            val res = apiClient.updateSettings(newSettings)
            if (res.isSuccess) {
                if (isVideoPlaying && currentPos > 0) {
                    delay(300)
                    apiClient.control("seek", to = currentPos)
                }
                repository.refreshStateImmediately()
            } else {
                repository.showErrorMessage(res.exceptionOrNull()?.message ?: "Error al actualizar ajustes")
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

                val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val allowedExtensions = listOf("mkv", "mp4", "avi", "mov", "m4v", "webm")
                if (ext !in allowedExtensions) {
                    repository.showErrorMessage("Formato no compatible ($ext). Formatos permitidos: mkv, mp4, avi, mov, m4v, webm.")
                    _isUploading.value = false
                    return@launch
                }

                if (fileSize > 0) {
                    val findRes = apiClient.find(fileName, fileSize)
                    val existing = findRes.getOrNull()
                    if (existing != null) {
                        repository.showUserMessage("El video ya existía en la PC. Iniciando reproducción...")
                        playVideo(existing.r, existing.p)
                        _isUploading.value = false
                        return@launch
                    }
                }

                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    repository.showErrorMessage("No se pudo leer el archivo seleccionado")
                    _isUploading.value = false
                    return@launch
                }

                repository.showUserMessage("Subiendo $fileName a la PC...")
                val uploadRes = apiClient.upload(fileName, inputStream, fileSize)
                if (uploadRes.isSuccess) {
                    val uploaded = uploadRes.getOrNull()
                    repository.showUserMessage("Video subido con éxito")
                    if (uploaded != null) {
                        playVideo(uploaded.r, uploaded.p)
                    }
                    repository.refreshStateImmediately()
                } else {
                    repository.showErrorMessage(uploadRes.exceptionOrNull()?.message ?: "Error al subir video")
                }
            } catch (e: Exception) {
                repository.showErrorMessage("Error al procesar archivo: ${e.message}")
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun refreshStateImmediately() {
        repository.refreshStateImmediately()
    }
}

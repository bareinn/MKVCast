package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class QueueItem(
    val r: Int,
    val p: String
) {
    val fileName: String
        get() = p.replace('\\', '/').substringAfterLast('/')

    fun toJson(): JSONObject = JSONObject().apply {
        put("r", r)
        put("p", p)
    }

    companion object {
        fun fromJson(json: JSONObject): QueueItem = QueueItem(
            r = json.optInt("r", 0),
            p = json.optString("p", "")
        )
    }
}

data class NowPlaying(
    val r: Int,
    val p: String,
    val name: String,
    val audio: Int?,
    val sub: Int?,
    val offset: Double = 0.0,
    val duration: Double = 0.0,
    val delay: Double = 0.0
) {
    companion object {
        fun fromJson(json: JSONObject?): NowPlaying? {
            if (json == null) return null
            return NowPlaying(
                r = json.optInt("r", 0),
                p = json.optString("p", ""),
                name = json.optString("name", ""),
                audio = if (json.has("audio")) json.optInt("audio") else null,
                sub = if (json.has("sub")) json.optInt("sub") else null,
                offset = json.optDouble("offset", 0.0),
                duration = json.optDouble("duration", 0.0),
                delay = json.optDouble("delay", 0.0)
            )
        }
    }
}

data class PlaybackStatus(
    val state: String = "IDLE", // PLAYING, PAUSED, BUFFERING, IDLE
    val pos: Double = 0.0,
    val volume: Float? = null
) {
    val isPlaying: Boolean get() = state == "PLAYING"
    val isPaused: Boolean get() = state == "PAUSED"
    val isBuffering: Boolean get() = state == "BUFFERING"
    val isIdle: Boolean get() = state == "IDLE"

    companion object {
        fun fromJson(json: JSONObject?): PlaybackStatus {
            if (json == null) return PlaybackStatus()
            val vol = if (json.has("volume") && !json.isNull("volume")) {
                json.optDouble("volume").toFloat()
            } else {
                null
            }
            return PlaybackStatus(
                state = json.optString("state", "IDLE"),
                pos = json.optDouble("pos", 0.0),
                volume = vol
            )
        }
    }
}

data class SubtitleStyle(
    val color: String = "white", // white, yellow, cyan, green, orange, pink, gray
    val bg: String = "none", // none, box
    val bgOpacity: Int = 60, // 30, 50, 70, 100
    val outline: Int = 1, // 0 to 4
    val shadow: Int = 0, // 0 to 3
    val pos: String = "bottom" // bottom, raised, middle, top
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("color", color)
        put("bg", bg)
        put("bgOpacity", bgOpacity)
        put("outline", outline)
        put("shadow", shadow)
        put("pos", pos)
    }

    companion object {
        fun fromJson(json: JSONObject?): SubtitleStyle {
            if (json == null) return SubtitleStyle()
            return SubtitleStyle(
                color = json.optString("color", "white"),
                bg = json.optString("bg", "none"),
                bgOpacity = json.optInt("bgOpacity", 60),
                outline = json.optInt("outline", 1),
                shadow = json.optInt("shadow", 0),
                pos = json.optString("pos", "bottom")
            )
        }
    }
}

data class ServerSettings(
    val autoSub: Boolean = true,
    val subSize: String = "normal", // small, normal, large, xlarge
    val assRespect: Boolean = true,
    val hdrFix: Boolean = true,
    val audioMode: String = "normal", // normal, dialog, night
    val style: SubtitleStyle = SubtitleStyle()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("autoSub", autoSub)
        put("subSize", subSize)
        put("assRespect", assRespect)
        put("hdrFix", hdrFix)
        put("audioMode", audioMode)
        put("style", style.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject?): ServerSettings {
            if (json == null) return ServerSettings()
            return ServerSettings(
                autoSub = json.optBoolean("autoSub", true),
                subSize = json.optString("subSize", "normal"),
                assRespect = json.optBoolean("assRespect", true),
                hdrFix = json.optBoolean("hdrFix", true),
                audioMode = json.optString("audioMode", "normal"),
                style = SubtitleStyle.fromJson(json.optJSONObject("style"))
            )
        }
    }
}

data class PlaylistItem(
    val id: String,
    val name: String,
    val items: List<QueueItem>
) {
    companion object {
        fun fromJson(json: JSONObject): PlaylistItem {
            val itemsArray = json.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<QueueItem>()
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.optJSONObject(i)
                if (itemObj != null) {
                    list.add(QueueItem.fromJson(itemObj))
                }
            }
            return PlaylistItem(
                id = json.optString("id", ""),
                name = json.optString("name", ""),
                items = list
            )
        }
    }
}

data class RecentItem(
    val r: Int,
    val p: String,
    val name: String,
    val pos: Double,
    val dur: Double
) {
    companion object {
        fun fromJson(json: JSONObject): RecentItem = RecentItem(
            r = json.optInt("r", 0),
            p = json.optString("p", ""),
            name = json.optString("name", ""),
            pos = json.optDouble("pos", 0.0),
            dur = json.optDouble("dur", 0.0)
        )
    }
}

data class ServerState(
    val devices: List<String> = emptyList(),
    val device: String? = null,
    val folders: List<String> = emptyList(),
    val uploadsIdx: Int = -1,
    val encoder: String = "",
    val queue: List<QueueItem> = emptyList(),
    val qi: Int = -1,
    val now: NowPlaying? = null,
    val status: PlaybackStatus = PlaybackStatus(),
    val settings: ServerSettings = ServerSettings(),
    val playlists: List<PlaylistItem> = emptyList(),
    val recent: List<RecentItem> = emptyList(),
    val ip: String = "",
    val port: Int = 8099
) {
    companion object {
        fun fromJson(json: JSONObject): ServerState {
            val devicesList = mutableListOf<String>()
            val devArr = json.optJSONArray("devices")
            if (devArr != null) {
                for (i in 0 until devArr.length()) {
                    devicesList.add(devArr.optString(i))
                }
            }

            val foldersList = mutableListOf<String>()
            val folArr = json.optJSONArray("folders")
            if (folArr != null) {
                for (i in 0 until folArr.length()) {
                    foldersList.add(folArr.optString(i))
                }
            }

            val queueList = mutableListOf<QueueItem>()
            val qArr = json.optJSONArray("queue")
            if (qArr != null) {
                for (i in 0 until qArr.length()) {
                    val qObj = qArr.optJSONObject(i)
                    if (qObj != null) queueList.add(QueueItem.fromJson(qObj))
                }
            }

            val playlistsList = mutableListOf<PlaylistItem>()
            val plArr = json.optJSONArray("playlists")
            if (plArr != null) {
                for (i in 0 until plArr.length()) {
                    val plObj = plArr.optJSONObject(i)
                    if (plObj != null) playlistsList.add(PlaylistItem.fromJson(plObj))
                }
            }

            val recentList = mutableListOf<RecentItem>()
            val recArr = json.optJSONArray("recent")
            if (recArr != null) {
                for (i in 0 until recArr.length()) {
                    val recObj = recArr.optJSONObject(i)
                    if (recObj != null) recentList.add(RecentItem.fromJson(recObj))
                }
            }

            val deviceVal = if (json.isNull("device")) null else json.optString("device", "").takeIf { it.isNotEmpty() }

            return ServerState(
                devices = devicesList,
                device = deviceVal,
                folders = foldersList,
                uploadsIdx = json.optInt("uploadsIdx", -1),
                encoder = json.optString("encoder", ""),
                queue = queueList,
                qi = json.optInt("qi", -1),
                now = NowPlaying.fromJson(json.optJSONObject("now")),
                status = PlaybackStatus.fromJson(json.optJSONObject("status")),
                settings = ServerSettings.fromJson(json.optJSONObject("settings")),
                playlists = playlistsList,
                recent = recentList,
                ip = json.optString("ip", ""),
                port = json.optInt("port", 8099)
            )
        }
    }
}

data class VideoFile(
    val name: String,
    val p: String,
    val pos: Double = 0.0,
    val seen: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject): VideoFile = VideoFile(
            name = json.optString("name", ""),
            p = json.optString("p", ""),
            pos = json.optDouble("pos", 0.0),
            seen = json.optBoolean("seen", false)
        )
    }
}

data class FolderContent(
    val dirs: List<String> = emptyList(),
    val files: List<VideoFile> = emptyList()
) {
    companion object {
        fun fromJson(json: JSONObject): FolderContent {
            val dirsList = mutableListOf<String>()
            val dirsArr = json.optJSONArray("dirs")
            if (dirsArr != null) {
                for (i in 0 until dirsArr.length()) {
                    dirsList.add(dirsArr.optString(i))
                }
            }

            val filesList = mutableListOf<VideoFile>()
            val filesArr = json.optJSONArray("files")
            if (filesArr != null) {
                for (i in 0 until filesArr.length()) {
                    val fObj = filesArr.optJSONObject(i)
                    if (fObj != null) filesList.add(VideoFile.fromJson(fObj))
                }
            }
            return FolderContent(dirs = dirsList, files = filesList)
        }
    }
}

data class AudioTrack(
    val i: Int,
    val lang: String,
    val title: String,
    val codec: String,
    val channels: Int
) {
    val displayLabel: String
        get() {
            val parts = mutableListOf<String>()
            if (lang.isNotBlank()) parts.add(lang.uppercase())
            if (title.isNotBlank()) parts.add(title)
            val tech = mutableListOf<String>()
            if (codec.isNotBlank()) tech.add(codec.uppercase())
            if (channels > 0) {
                tech.add(if (channels == 6) "5.1" else if (channels == 2) "Stereo" else "${channels}ch")
            }
            if (tech.isNotEmpty()) parts.add("(${tech.joinToString(" ")})")
            return parts.joinToString(" ").ifBlank { "Pista $i" }
        }

    companion object {
        fun fromJson(json: JSONObject): AudioTrack = AudioTrack(
            i = json.optInt("i", 0),
            lang = json.optString("lang", ""),
            title = json.optString("title", ""),
            codec = json.optString("codec", ""),
            channels = json.optInt("channels", 0)
        )
    }
}

data class SubtitleTrack(
    val i: Int,
    val lang: String,
    val title: String,
    val codec: String,
    val def: Boolean = false,
    val forced: Boolean = false,
    val ext: Boolean = false
) {
    val isUnsupportedSubtitle: Boolean
        get() = codec.equals("xsub", ignoreCase = true)

    val isBitmapSubtitle: Boolean
        get() = codec.equals("hdmv_pgs_subtitle", ignoreCase = true) ||
                codec.equals("dvd_subtitle", ignoreCase = true) ||
                codec.equals("dvb_subtitle", ignoreCase = true)

    val subtitleType: String
        get() {
            if (ext && title.contains('.')) {
                return title.substringAfterLast('.').uppercase()
            }
            return when (codec.lowercase()) {
                "subrip" -> "SRT"
                "ass" -> "ASS"
                "ssa" -> "SSA"
                "mov_text" -> "texto"
                "webvtt" -> "VTT"
                "hdmv_pgs_subtitle" -> "PGS · imagen"
                "dvd_subtitle" -> "VobSub · imagen"
                "dvb_subtitle" -> "DVB · imagen"
                "xsub" -> "imagen"
                else -> codec.ifBlank { "sub" }
            }
        }

    val displayLabel: String
        get() {
            val langText = lang.ifBlank { "sub" }
            val titleText = if (title.isNotBlank()) " $title" else ""
            val typeText = " ($subtitleType)"
            val badges = mutableListOf<String>()
            if (isUnsupportedSubtitle) {
                badges.add("· no compatible")
            }
            if (ext) badges.add("· archivo externo")
            if (forced) badges.add("· forzado")
            if (def) badges.add("· predeterminado")

            val suffix = if (badges.isNotEmpty()) " " + badges.joinToString(" ") else ""
            return "$langText$titleText$typeText$suffix"
        }

    companion object {
        fun fromJson(json: JSONObject): SubtitleTrack = SubtitleTrack(
            i = json.optInt("i", 0),
            lang = json.optString("lang", ""),
            title = json.optString("title", ""),
            codec = json.optString("codec", ""),
            def = json.optBoolean("def", false),
            forced = json.optBoolean("forced", false),
            ext = json.optBoolean("ext", false)
        )
    }
}

data class VideoInfo(
    val duration: Double = 0.0,
    val vcodec: String = "",
    val pix: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Double = 0.0,
    val level: Int = 0,
    val profile: String = "",
    val audio: List<AudioTrack> = emptyList(),
    val subs: List<SubtitleTrack> = emptyList()
) {
    val isHevc: Boolean
        get() = vcodec.contains("hevc", ignoreCase = true) || vcodec.contains("h265", ignoreCase = true)

    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else ""

    companion object {
        fun fromJson(json: JSONObject): VideoInfo {
            val audioList = mutableListOf<AudioTrack>()
            val audArr = json.optJSONArray("audio")
            if (audArr != null) {
                for (i in 0 until audArr.length()) {
                    val aObj = audArr.optJSONObject(i)
                    if (aObj != null) audioList.add(AudioTrack.fromJson(aObj))
                }
            }

            val subsList = mutableListOf<SubtitleTrack>()
            val subArr = json.optJSONArray("subs")
            if (subArr != null) {
                for (i in 0 until subArr.length()) {
                    val sObj = subArr.optJSONObject(i)
                    if (sObj != null) subsList.add(SubtitleTrack.fromJson(sObj))
                }
            }

            return VideoInfo(
                duration = json.optDouble("duration", 0.0),
                vcodec = json.optString("vcodec", ""),
                pix = json.optString("pix", ""),
                width = json.optInt("width", 0),
                height = json.optInt("height", 0),
                fps = json.optDouble("fps", 0.0),
                level = json.optInt("level", 0),
                profile = json.optString("profile", ""),
                audio = audioList,
                subs = subsList
            )
        }
    }
}

fun List<SubtitleTrack>.pickDefaultSubtitle(): SubtitleTrack? {
    fun pick(list: List<SubtitleTrack>): SubtitleTrack? =
        list.firstOrNull { it.def }
            ?: list.firstOrNull { it.forced }
            ?: list.firstOrNull {
                val l = it.lang.lowercase()
                l.startsWith("spa") || l.startsWith("es")
            }
    val usable = this.filter { !it.isUnsupportedSubtitle }
    // Primero los de texto; si no hay ninguno que cumpla, los de imagen
    return pick(usable.filter { !it.isBitmapSubtitle }) ?: pick(usable.filter { it.isBitmapSubtitle })
}

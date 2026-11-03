package com.example.data.api

import com.example.data.local.ServerPreferences
import com.example.data.model.FolderContent
import com.example.data.model.QueueItem
import com.example.data.model.ServerSettings
import com.example.data.model.ServerState
import com.example.data.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AuthException(message: String = "Se requiere PIN de acceso") : IOException(message)
class ApiException(val code: Int, override val message: String) : IOException(message)

class MKVCastApiClient(
    private val preferences: ServerPreferences
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val octetStreamMediaType = "application/octet-stream".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val uploadHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    val baseUrl: String
        get() = ServerPreferences.normalizeUrl(preferences.serverUrl)

    val token: String
        get() = preferences.token

    fun getThumbnailUrl(r: Int, p: String): String {
        val base = baseUrl
        if (base.isEmpty()) return ""
        val encodedP = URLEncoder.encode(p, "UTF-8")
        return "$base/api/thumb?r=$r&p=$encodedP"
    }

    private fun newRequestBuilder(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        val currentToken = preferences.token
        if (currentToken.isNotBlank()) {
            builder.addHeader("x-token", currentToken)
        }
        return builder
    }

    private fun parseErrorMessage(code: Int, bodyString: String): String {
        return try {
            val json = JSONObject(bodyString)
            json.optString("error", "Error del servidor ($code)")
        } catch (_: Exception) {
            "Error de comunicación ($code)"
        }
    }

    private suspend fun executeCall(request: Request, client: OkHttpClient = okHttpClient): String =
        withContext(Dispatchers.IO) {
            val response = try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                throw IOException("No pude conectar con $baseUrl. Verificá que la PC esté encendida, que MKVCast esté abierto y que estén en la misma red wifi.", e)
            }

            response.use { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.code == 401) {
                    preferences.clearAuth()
                    throw AuthException(parseErrorMessage(resp.code, body).ifBlank { "PIN de acceso requerido o incorrecto" })
                }
                if (!resp.isSuccessful) {
                    throw ApiException(resp.code, parseErrorMessage(resp.code, body))
                }
                body
            }
        }

    suspend fun login(pin: String): Result<String> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val payload = JSONObject().apply { put("pin", pin) }.toString()
        val request = Request.Builder()
            .url("$base/api/login")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        val responseText = executeCall(request)
        val json = JSONObject(responseText)
        val token = json.optString("token", "")
        preferences.token = token
        preferences.lastPin = pin
        token
    }

    suspend fun getState(): Result<ServerState> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val request = newRequestBuilder("$base/api/state").get().build()
        val responseText = executeCall(request)
        ServerState.fromJson(JSONObject(responseText))
    }

    suspend fun getLs(r: Int, p: String): Result<FolderContent> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val encodedP = URLEncoder.encode(p, "UTF-8")
        val request = newRequestBuilder("$base/api/ls?r=$r&p=$encodedP").get().build()
        val responseText = executeCall(request)
        FolderContent.fromJson(JSONObject(responseText))
    }

    suspend fun getInfo(r: Int, p: String): Result<VideoInfo> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val encodedP = URLEncoder.encode(p, "UTF-8")
        val request = newRequestBuilder("$base/api/info?r=$r&p=$encodedP").get().build()
        val responseText = executeCall(request)
        VideoInfo.fromJson(JSONObject(responseText))
    }

    suspend fun find(name: String, size: Long): Result<QueueItem?> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val encodedName = URLEncoder.encode(name, "UTF-8")
        val request = newRequestBuilder("$base/api/find?name=$encodedName&size=$size").get().build()
        val responseText = executeCall(request)
        val json = JSONObject(responseText)
        if (json.has("r") && json.has("p")) {
            QueueItem.fromJson(json)
        } else {
            null
        }
    }

    suspend fun upload(name: String, inputStream: InputStream, length: Long): Result<QueueItem> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val encodedName = URLEncoder.encode(name, "UTF-8")

        val requestBody = object : RequestBody() {
            override fun contentType() = octetStreamMediaType
            override fun contentLength() = if (length <= 0) -1L else length
            override fun writeTo(sink: BufferedSink) {
                val buffer = ByteArray(8192)
                var read: Int
                inputStream.use { input ->
                    while (input.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }

        val request = newRequestBuilder("$base/api/upload?name=$encodedName")
            .put(requestBody)
            .build()
        val responseText = executeCall(request, uploadHttpClient)
        val json = JSONObject(responseText)
        QueueItem.fromJson(json)
    }

    suspend fun setDevice(name: String): Result<Unit> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val payload = JSONObject().apply { put("name", name) }.toString()
        val request = newRequestBuilder("$base/api/device")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        executeCall(request)
    }

    suspend fun play(
        r: Int,
        p: String,
        audio: Int? = null,
        sub: Int? = null,
        start: Double? = null
    ): Result<Unit> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val payload = JSONObject().apply {
            put("r", r)
            put("p", p)
            if (audio != null) put("audio", audio)
            if (sub != null) put("sub", sub)
            if (start != null) put("start", start)
        }.toString()
        val request = newRequestBuilder("$base/api/play")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        executeCall(request)
    }

    suspend fun control(cmd: String, value: Double? = null, to: Double? = null): Result<Unit> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val payload = JSONObject().apply {
            put("cmd", cmd)
            if (value != null) put("value", value)
            if (to != null) put("to", to)
        }.toString()
        val request = newRequestBuilder("$base/api/control")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        executeCall(request)
    }

    suspend fun queue(action: String, r: Int? = null, p: String? = null, i: Int? = null): Result<Unit> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val payload = JSONObject().apply {
            put("action", action)
            if (r != null) put("r", r)
            if (p != null) put("p", p)
            if (i != null) put("i", i)
        }.toString()
        val request = newRequestBuilder("$base/api/queue")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        executeCall(request)
    }

    suspend fun subDelay(delta: Double? = null, value: Double? = null, reset: Boolean = false): Result<Double> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val payload = JSONObject().apply {
            if (reset) {
                put("reset", true)
            } else if (delta != null) {
                put("delta", delta)
            } else if (value != null) {
                put("value", value)
            }
        }.toString()
        val request = newRequestBuilder("$base/api/subdelay")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        val responseText = executeCall(request)
        val json = JSONObject(responseText)
        json.optDouble("delay", 0.0)
    }

    suspend fun setSeen(r: Int, p: String, seen: Boolean): Result<Unit> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val payload = JSONObject().apply {
            put("r", r)
            put("p", p)
            put("seen", seen)
        }.toString()
        val request = newRequestBuilder("$base/api/seen")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        executeCall(request)
    }

    suspend fun removeRecent(r: Int, p: String): Result<Unit> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val payload = JSONObject().apply {
            put("r", r)
            put("p", p)
        }.toString()
        val request = newRequestBuilder("$base/api/recent")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        executeCall(request)
    }

    suspend fun playlistAction(payload: JSONObject): Result<JSONObject> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val request = newRequestBuilder("$base/api/playlist")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val responseText = executeCall(request)
        JSONObject(responseText)
    }

    suspend fun updateSettings(settings: ServerSettings): Result<ServerSettings> = runCatching {
        val base = baseUrl
        if (base.isEmpty()) throw IOException("Dirección del servidor vacía")
        val request = newRequestBuilder("$base/api/settings")
            .post(settings.toJson().toString().toRequestBody(jsonMediaType))
            .build()
        val responseText = executeCall(request)
        ServerSettings.fromJson(JSONObject(responseText))
    }
}

package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class ServerPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mkvcast_server_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var lastPin: String
        get() = prefs.getString(KEY_LAST_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_PIN, value).apply()

    fun clearAuth() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "key_server_url"
        private const val KEY_TOKEN = "key_token"
        private const val KEY_LAST_PIN = "key_last_pin"

        fun normalizeUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) return ""
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "http://$url"
            }
            if (url.endsWith("/")) {
                url = url.substring(0, url.length - 1)
            }
            return url
        }
    }
}

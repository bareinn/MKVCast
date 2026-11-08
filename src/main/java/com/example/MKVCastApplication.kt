package com.example

import android.app.Application
import com.example.data.repository.PlaybackRepository
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MKVCastApplication : Application() {
    lateinit var playbackRepository: PlaybackRepository
        private set

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        playbackRepository = PlaybackRepository.getInstance(this)
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(filesDir, CRASH_FILE_NAME)
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                pw.flush()

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())

                val crashLog = buildString {
                    append("=== MKVCast Crash Log ===\n")
                    append("Fecha: ").append(timestamp).append("\n")
                    append("Hilo: ").append(thread.name).append(" (ID: ").append(thread.id).append(")\n\n")
                    append("--- Stack Trace Completo ---\n")
                    append(sw.toString())
                }

                crashFile.writeText(crashLog)
            } catch (e: Exception) {
                // Ignore failure during crash saving
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        const val CRASH_FILE_NAME = "last_crash.txt"
    }
}

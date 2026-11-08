package com.example.ui.util

import java.util.Locale

object FormatUtils {
    fun formatSeconds(seconds: Double): String {
        val totalSec = seconds.toLong().coerceAtLeast(0)
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val secs = totalSec % 60

        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, secs)
        }
    }

    fun formatDelay(seconds: Double): String {
        val sign = if (seconds > 0) "+" else if (seconds < 0) "-" else ""
        val absVal = kotlin.math.abs(seconds)
        return String.format(Locale.ROOT, "Sincro %s%.1f s", sign, absVal).replace('.', ',')
    }
}

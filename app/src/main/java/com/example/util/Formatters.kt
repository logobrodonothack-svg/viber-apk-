package com.example.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val oneDecimal = DecimalFormat("#,##0.0")

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "Unknown size"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> "${oneDecimal.format(bytes / gb)} GB"
            bytes >= mb -> "${oneDecimal.format(bytes / mb)} MB"
            bytes >= kb -> "${oneDecimal.format(bytes / kb)} KB"
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "--:--"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDate(timestampMs: Long): String {
        val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }

    fun sanitizeFileName(raw: String, fallbackExt: String = "mp4"): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
            .ifBlank { "video_${System.currentTimeMillis()}" }
        return if (cleaned.contains('.')) cleaned else "$cleaned.$fallbackExt"
    }
}

package com.example.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val polishLocale = Locale("pl", "PL")

    fun formatDate(millis: Long): String =
        SimpleDateFormat("d MMM yyyy", polishLocale).format(Date(millis))

    fun formatTime(millis: Long): String =
        SimpleDateFormat("HH:mm", polishLocale).format(Date(millis))

    fun formatDateTime(millis: Long): String =
        SimpleDateFormat("dd.MM.yyyy • HH:mm", polishLocale).format(Date(millis))

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }
}

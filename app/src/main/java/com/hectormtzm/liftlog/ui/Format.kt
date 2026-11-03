package com.hectormtzm.liftlog.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}

fun formatRest(seconds: Int): String = if (seconds <= 0) "Off" else formatClock(seconds.toLong())

fun formatDuration(millis: Long): String {
    val totalMin = (millis / 60_000).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun formatDate(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) "EEE, MMM d" else "MMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

fun formatShortDate(millis: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))

fun formatTime(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

fun relativeDay(millis: Long): String {
    val days = ((startOfDay(System.currentTimeMillis()) - startOfDay(millis)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> formatDate(millis)
    }
}

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun String.titleCase(): String = split(' ').joinToString(" ") { w ->
    w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

val REST_OPTIONS = listOf(0, 30, 45, 60, 75, 90, 120, 150, 180, 240, 300)

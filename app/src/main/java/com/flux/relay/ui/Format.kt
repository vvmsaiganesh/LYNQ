package com.flux.relay.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

fun formatClock(timestamp: Long): String = clockFormat.format(Date(timestamp))

fun formatWhen(timestamp: Long): String {
    val age = System.currentTimeMillis() - timestamp
    return when {
        age < 60_000 -> "now"
        age < 3_600_000 -> "${age / 60_000}m"
        age < 86_400_000 -> clockFormat.format(Date(timestamp))
        else -> dayFormat.format(Date(timestamp))
    }
}

fun formatRelative(timestamp: Long): String {
    val age = System.currentTimeMillis() - timestamp
    return when {
        age < 60_000 -> "just now"
        age < 3_600_000 -> "${age / 60_000} min ago"
        age < 86_400_000 -> "${age / 3_600_000} h ago"
        else -> "${age / 86_400_000} d ago"
    }
}

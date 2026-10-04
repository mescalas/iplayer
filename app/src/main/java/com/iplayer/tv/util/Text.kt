package com.iplayer.tv.util

import java.text.Normalizer
import java.util.Locale

private val COMBINING = Regex("\\p{Mn}+")

/** Lowercase, accent-free form used for fast LIKE searches. */
fun String.searchKey(): String =
    COMBINING.replace(Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD), "")

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

fun formatMinutes(ms: Long): String {
    val min = ms / 60000
    return if (min >= 60) "${min / 60} h ${String.format(Locale.ROOT, "%02d", min % 60)}" else "$min min"
}

private val timeFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
    override fun initialValue() = java.text.SimpleDateFormat("HH:mm", Locale.FRANCE)
}

fun formatClock(epochMs: Long): String = timeFormat.get()!!.format(java.util.Date(epochMs))

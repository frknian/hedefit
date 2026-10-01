package com.hedefit.wear.ui

import androidx.compose.ui.graphics.Color

object HedefitColors {
    val Green = Color(0xFF22C55E)
    val OnGreen = Color(0xFF051B0B)
    val Water = Color(0xFF38BDF8)
    val Coral = Color(0xFFFF6B6B)
    val Amber = Color(0xFFFBBF24)
    val Surface = Color(0xFF1A1F27)
    val Muted = Color(0xFF94A3B8)
}

fun clock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

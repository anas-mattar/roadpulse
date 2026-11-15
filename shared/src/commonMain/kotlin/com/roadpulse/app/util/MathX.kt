package com.roadpulse.app.util

import kotlin.math.PI
import kotlin.math.roundToLong

/** Small maths helpers that exist on every platform (Android and iOS). */
fun toRadians(degrees: Double): Double = degrees * PI / 180.0
fun toDegrees(radians: Double): Double = radians * 180.0 / PI
/** Rounds to the nearest whole number, halves up (like Java's Math.round). */
fun mathRound(x: Double): Long = x.roundToLong()

/** "12.3": one decimal with a dot, whatever the phone's language. */
fun oneDecimal(x: Double): String {
    val tenths = mathRound(x * 10)
    val sign = if (tenths < 0) "-" else ""
    val abs = if (tenths < 0) -tenths else tenths
    return "$sign${abs / 10}.${abs % 10}"
}

/** "6.80": two decimals with a dot. */
fun twoDecimals(x: Double): String {
    val cents = mathRound(x * 100)
    val sign = if (cents < 0) "-" else ""
    val abs = if (cents < 0) -cents else cents
    return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

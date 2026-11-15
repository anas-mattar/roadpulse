package com.roadpulse.app.data

/** Android convenience: open status at a java.time moment. */
fun OpeningHours.status(text: String?, now: java.time.LocalDateTime): OpenStatus? =
    status(text, now.dayOfWeek.value - 1, now.hour, now.minute)

package com.roadpulse.app.data

/** Android convenience: a place row for [now] on this phone's clock (adds "Open · closes 22:00"). */
fun MapPlace.toPlace(
    s: com.roadpulse.app.i18n.AppStrings = com.roadpulse.app.i18n.EnglishStrings,
    now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
): Place = describe(s, OpeningHours.status(openingHours, now))

package com.roadpulse.app.nav

/** Which arrow to show for a maneuver (pure Kotlin; the UI maps it to an image). */
enum class TurnIcon {
    DEPART, STRAIGHT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, UTURN_RIGHT, UTURN_LEFT, SHARP_LEFT, LEFT,
    SLIGHT_LEFT, KEEP_RIGHT, KEEP_LEFT, RAMP_RIGHT, RAMP_LEFT, MERGE, ROUNDABOUT, ARRIVE, FERRY;

    companion object {
        /** Maps a Valhalla maneuver type to an arrow. */
        fun forType(type: Int?): TurnIcon = when (type) {
            1, 2, 3 -> DEPART
            4, 5, 6 -> ARRIVE
            9 -> SLIGHT_RIGHT
            10 -> RIGHT
            11 -> SHARP_RIGHT
            12 -> UTURN_RIGHT
            13 -> UTURN_LEFT
            14 -> SHARP_LEFT
            15 -> LEFT
            16 -> SLIGHT_LEFT
            18, 20 -> RAMP_RIGHT
            19, 21 -> RAMP_LEFT
            23 -> KEEP_RIGHT
            24 -> KEEP_LEFT
            25, 37, 38 -> MERGE
            26, 27 -> ROUNDABOUT
            28, 29 -> FERRY
            else -> STRAIGHT // 7 becomes, 8 continue, 17 ramp straight, 22 stay straight, unknown
        }
    }
}

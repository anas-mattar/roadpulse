package com.roadpulse.app.data

/** Place categories served by the RoadPulse backend (names match its PlaceCategory). */
enum class PlaceCategory(val label: String) {
    FUEL("Fuel"),
    PARKING("Parking"),
    FOOD("Food"),
    EV_CHARGING("EV charging"),
    REST_AREA("R&R"),
    SPEED_CAMERA("Speed camera"),
    SCHOOL_ZONE("School"),
}

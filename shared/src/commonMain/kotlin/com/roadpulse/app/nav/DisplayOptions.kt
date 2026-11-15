package com.roadpulse.app.nav

/** Map colours: follow the sun, or always light or dark. */
enum class MapColors(val label: String) {
    AUTO("Automatic (dark after sunset)"),
    LIGHT("Always light"),
    DARK("Always dark"),
}

/** When the speed limit sign is shown while driving. */
enum class LimitDisplay(val label: String) {
    ALWAYS("Always"),
    WHEN_SPEEDING("Only when speeding"),
    NEVER("Never"),
}

package com.roadpulse.app.i18n

import com.roadpulse.app.util.mathRound

import com.roadpulse.app.data.FlagReason
import com.roadpulse.app.data.PlaceCategory
import com.roadpulse.app.data.ReportType
import com.roadpulse.app.data.Vehicle
import com.roadpulse.app.nav.GuidanceLanguage
import com.roadpulse.app.nav.LimitDisplay
import com.roadpulse.app.nav.MapColors
import com.roadpulse.app.util.oneDecimal

/**
 * Every word the app shows on screen (outside the voice prompts, which are in nav/Narration.kt),
 * in one language. Pure Kotlin, so wording is unit tested and a review sheet can be generated.
 * Debug-only screens (simulation, field-test flags, laptop server) stay in English.
 *
 * To add a language: implement this interface (start from [EnglishStrings]) and return it from
 * [stringsFor]. Bahasa Melayu currently shows the English screens (voice is Malay).
 */
interface AppStrings {
    /** Right-to-left layout (Arabic). */
    val rtl: Boolean

    // Common
    val cancel: String
    val done: String
    val back: String
    val close: String
    val save: String
    val send: String
    val skip: String
    val show: String
    val hide: String
    val clear: String

    // Units and times
    fun distance(km: Double): String
    fun duration(minutes: Int): String
    fun justNow(): String
    fun minutesAgo(minutes: Long): String
    fun hoursAgo(hours: Long): String
    val kmh: String

    // Explore screen
    val report: String
    val settingsButton: String
    val showMyLocation: String
    val whereTo: String
    val yourHomeAddress: String
    val yourWorkAddress: String
    fun setShortcutHint(home: Boolean): String
    val home: String
    val work: String
    val setAddress: String
    fun yourCarParked(age: String): String
    val forgetParking: String
    fun remove(name: String): String
    val searchByVoice: String
    fun category(c: PlaceCategory): String
    fun categoryNearby(c: PlaceCategory): String
    fun noneWithin10Km(c: PlaceCategory): String
    /** Car park fee, shown in place lists. */
    val paid: String
    val free: String
    /** "Open 24 hours", "Open · closes 22:00", "Closed · opens 08:00", "Closed". */
    fun openStatus(status: com.roadpulse.app.data.OpenStatus): String

    // Route preview
    val destination: String
    fun stop(number: Int, name: String): String
    val removeStop: String
    fun carParksNearDestination(count: Int): String
    val tipAddStop: String
    val goNow: String
    val simulateDrive: String
    fun via(road: String): String
    val tagFastest: String
    val tagNoTolls: String
    val tagShortest: String
    fun tagExtraMinutes(minutes: Int): String
    val tollRoad: String
    val tollPriceUnknown: String
    fun tollAbout(rm: String): String
    fun tollAtLeast(rm: String): String
    fun vehicle(v: Vehicle): String
    fun avoiding(things: List<String>): String
    val avoidTollsShort: String
    val avoidHighwaysShort: String
    val avoidFerriesShort: String
    val unpavedAllowed: String

    // Driving
    val muteVoice: String
    val unmuteVoice: String
    val stopNavigation: String
    /** Button that shows the whole remaining route while driving. */
    val overview: String

    // Reports
    fun reportType(t: ReportType): String
    /** Detail of a report, or null for an unknown code. */
    fun reportDetail(t: ReportType, code: String): String?
    fun reportedAgo(age: String): String
    fun confirmations(count: Int): String
    val yourReport: String
    val isItStillThere: String
    val stillThere: String
    val notThere: String
    val reportAbuse: String
    fun stillThereQuestion(t: ReportType): String
    val howLong: String
    /** Road closure duration choice; null minutes = not sure. */
    fun closureDuration(minutes: Int?): String
    val whatsWrongWithReport: String
    fun flagReason(r: FlagReason): String

    // Place card
    val driveHere: String
    val addStop: String
    val parkedHere: String
    val savePlace: String
    val placeNameHint: String
    val droppedPin: String

    // Settings
    val settings: String
    val language: String
    fun noVoiceInstalled(language: String): String
    val muteVoiceSetting: String
    val mapColours: String
    fun mapColors(option: MapColors): String
    val route: String
    val avoidTolls: String
    val avoidHighways: String
    val avoidFerries: String
    val allowUnpaved: String
    val speed: String
    val showSpeedometer: String
    val showSpeedLimit: String
    fun limitDisplay(option: LimitDisplay): String
    val warnOverLimitBy: String
    val soundWhenSpeeding: String
    val mapView: String
    val northUp: String
    val showCompass: String
    val flatMap: String
    val privacy: String
    val privacyText: String
    val clearSavedPlaces: String

    // Messages
    fun resumedDrive(name: String): String
    val arrived: String
    fun searchFailed(error: String?): String
    val couldNotLoadPlaces: String
    fun parkingFor(name: String): String
    fun near(name: String): String
    fun shortcutSaved(home: Boolean): String
    val waitingForLocation: String
    fun routingFailed(error: String?): String
    val savedPlacesCleared: String
    fun couldNotStartNavigation(error: String?): String
    fun stopAdded(name: String): String
    val chooseDestinationFirst: String
    fun saved(name: String): String
    fun removed(name: String): String
    val locationUnavailable: String
    val parkingSpotSaved: String
    val thanks: String
    val locationUnavailableReport: String
    fun reported(t: ReportType): String
    val couldNotSendReport: String

    // Planned drives
    val leaveLater: String
    val planDrive: String
    val arriveBy: String
    val today: String
    val tomorrow: String
    /** "Leave at 07:55 · 25 min drive + 10 min spare" */
    fun leaveAt(time: String, driveMinutes: Int, spareMinutes: Int): String
    val setReminder: String
    val tooLate: String
    fun reminderSet(time: String): String
    /** Explore list row: "Arrive 08:30 tomorrow". */
    fun plannedFor(time: String, tomorrow: Boolean): String
    val plannedDrives: String
    fun timeToLeave(name: String): String
    fun timeToLeaveText(driveMinutes: Int, arriveBy: String): String
    val leaveRemindersChannel: String

    // Music
    val musicControls: String
    val musicControlsHelp: String
    val musicNeedsAccess: String
    val play: String
    val pause: String
    val nextTrack: String
    val previousTrack: String

    // Profile
    val profile: String
    val nickname: String
    val nicknameHint: String
    fun levelName(code: String): String
    fun points(n: Int): String
    fun pointsToNext(points: Int, nextLevel: String): String
    val topLevel: String
    val statReports: String
    val statConfirmed: String
    val statVotes: String
    val badges: String
    fun badge(code: String): String
    val howPointsWork: String
    val profileNeedsServer: String

    // Share my drive
    val shareDrive: String
    fun shareText(destination: String, eta: String, url: String): String
    val sharingLive: String
    val stopSharing: String
    val shareFailed: String

    // Live traffic
    val helpTraffic: String
    val helpTrafficText: String
    val trafficConsentTitle: String
    val allow: String
    val notNow: String
    fun fasterRoute(savedMinutes: Int): String
    val takeRoute: String
    val keepRoute: String
    fun trafficDelay(minutes: Int): String

    // Privacy
    val privacyPolicy: String
    /** Short policy for the app, one paragraph per item (the full page is /privacy on the server). */
    val privacyPolicyText: List<String>
    val exportData: String
    val deleteData: String
    val deleteDataConfirm: String
    val delete: String
    val dataDeleted: String
    val deleteFailed: String
    val exportFailed: String

    // Navigation notification
    fun navigatingTo(name: String): String
    val navigation: String
    fun inMeters(meters: Int): String
    val notificationStop: String
    val notificationChannel: String

    // Route list and drive sheet
    val mapTab: String
    val listTab: String
    val goShort: String
    val currentRoute: String
    val otherRoutes: String
    val noOtherRoutes: String

    // Share drive
    val shareYourDrive: String
    val shareExplain: String
    fun shareTo(destination: String): String
    val sharedDrive: String
    fun viewers(count: Int): String

    // Settings groups and menu
    val general: String
    val mapDisplay: String
    val voiceAndSound: String
    val navigationSettings: String
    val vehicleDetails: String
    val alertsAndReports: String
    val speedometer: String
    val menu: String
    val helpAndFeedback: String
    val helpTopics: List<Pair<String, String>>
    val sendFeedback: String
    fun version(v: String): String

    // Add a missing place
    val addPlace: String
    val placeName: String
    val whatKindOfPlace: String
    fun placeKind(kind: com.roadpulse.app.data.CommunityPlaceKind): String
    val placeNoteHint: String
    val placeAdded: String
    /** "Report a problem with this place" (a place from search that is wrong). */
    val reportPlaceProblem: String
    val whatIsWrong: String
    fun placeProblem(kind: com.roadpulse.app.data.PlaceProblemKind): String
    val placeIsWhereIAm: String
    val placeProblemNoteHint: String
    val placeProblemSent: String
    val placeProblemFailed: String
    val placeAddFailed: String
    val addedByDrivers: String
    val waitingForReview: String
}

/** Screen language for a chosen app language. */
fun stringsFor(language: GuidanceLanguage): AppStrings = when (language) {
    GuidanceLanguage.AR -> ArabicStrings
    GuidanceLanguage.EN, GuidanceLanguage.MS -> EnglishStrings
}

/**
 * Metres in steps of 5 for distances under a kilometre (120, 125, 130… easier to read at a glance
 * than 122), never below 5; null when the distance is shown in kilometres. Pure: tested.
 */
fun stepMetres(km: Double): Long? {
    val metres = (mathRound(km * 1000 / 5) * 5).coerceAtLeast(5)
    return if (km >= 1 || metres >= 1000) null else metres
}

object EnglishStrings : AppStrings {
    override val rtl = false
    override val cancel = "Cancel"
    override val done = "Done"
    override val back = "Back"
    override val close = "Close"
    override val save = "Save"
    override val send = "Send"
    override val skip = "Skip"
    override val show = "Show"
    override val hide = "Hide"
    override val clear = "Clear"

    override fun distance(km: Double) =
        stepMetres(km)?.let { "$it m" } ?: "${oneDecimal(km)} km"
    override fun duration(minutes: Int) = if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    override fun justNow() = "just now"
    override fun minutesAgo(minutes: Long) = "$minutes min ago"
    override fun hoursAgo(hours: Long) = "$hours h ago"
    override val kmh = "km/h"

    override val report = "Report"
    override val settingsButton = "Voice guidance settings"
    override val showMyLocation = "Show my location"
    override val whereTo = "Where to?"
    override val yourHomeAddress = "Your home address"
    override val yourWorkAddress = "Your work address"
    override fun setShortcutHint(home: Boolean) = "Set ${if (home) "Home" else "Work"}: search for the address and pick it"
    override val home = "Home"
    override val work = "Work"
    override val setAddress = "Set address"
    override fun yourCarParked(age: String) = "Your car · parked $age"
    override val forgetParking = "Forget parking spot"
    override fun remove(name: String) = "Remove $name"
    override val searchByVoice = "Search by voice"
    override fun category(c: PlaceCategory) = c.label
    override fun categoryNearby(c: PlaceCategory) = "${c.label} nearby"
    override fun noneWithin10Km(c: PlaceCategory) = "No ${c.label.lowercase()} within 10 km"
    override val paid = "paid"
    override val free = "free"
    override fun openStatus(status: com.roadpulse.app.data.OpenStatus) = when (status) {
        com.roadpulse.app.data.OpenStatus.AlwaysOpen -> "Open 24 hours"
        is com.roadpulse.app.data.OpenStatus.Open -> "Open · closes ${status.closesAt}"
        is com.roadpulse.app.data.OpenStatus.Closed -> status.opensAt?.let { "Closed · opens $it" } ?: "Closed"
    }

    override val destination = "Destination"
    override fun stop(number: Int, name: String) = "Stop $number: $name"
    override val removeStop = "Remove stop"
    override fun carParksNearDestination(count: Int) = "$count car park${if (count > 1) "s" else ""} near your destination"
    override val tipAddStop = "Tip: press and hold the map to add a stop"
    override val goNow = "Go now"
    override val simulateDrive = "Simulate a drive"
    override fun via(road: String) = "via $road"
    override val tagFastest = "Fastest"
    override val tagNoTolls = "No tolls"
    override val tagShortest = "Shortest"
    override fun tagExtraMinutes(minutes: Int) = "+$minutes min"
    override val tollRoad = "Toll road"
    override val tollPriceUnknown = "Toll: price unknown"
    override fun tollAbout(rm: String) = "Toll ~$rm"
    override fun tollAtLeast(rm: String) = "Toll $rm + ?"
    override fun vehicle(v: Vehicle) = v.label
    override fun avoiding(things: List<String>) = "avoiding " + things.joinToString(", ")
    override val avoidTollsShort = "tolls"
    override val avoidHighwaysShort = "highways"
    override val avoidFerriesShort = "ferries"
    override val unpavedAllowed = "unpaved roads allowed"

    override val muteVoice = "Mute voice"
    override val unmuteVoice = "Unmute voice"
    override val stopNavigation = "Stop navigation"
    override val overview = "Overview"

    override fun reportType(t: ReportType) = t.label
    override fun reportDetail(t: ReportType, code: String) = com.roadpulse.app.data.ReportDetails.label(t, code)
    override fun reportedAgo(age: String) = "Reported $age"
    override fun confirmations(count: Int) = "$count confirmed"
    override val yourReport = "Your report. Other drivers can confirm it."
    override val isItStillThere = "Is it still there?"
    override val stillThere = "Still there"
    override val notThere = "Not there"
    override val reportAbuse = "Report abuse"
    override fun stillThereQuestion(t: ReportType) = "${t.label}: still there?"
    override val howLong = "How long?"
    override fun closureDuration(minutes: Int?) = when (minutes) {
        null -> "Not sure"
        60 -> "1 hour"
        1440 -> "All day"
        else -> "${minutes / 60} hours"
    }
    override val whatsWrongWithReport = "What's wrong with this report?"
    override fun flagReason(r: FlagReason) = r.label

    override val driveHere = "Drive here"
    override val addStop = "Add stop"
    override val parkedHere = "Parked here"
    override val savePlace = "Save place"
    override val placeNameHint = "Name, e.g. Gym"
    override val droppedPin = "Dropped pin"

    override val settings = "Settings"
    override val language = "Language"
    override fun noVoiceInstalled(language: String) =
        "No $language voice is installed on this phone, so guidance stays in English. " +
            "Install one in Android Settings > System > Languages > Text-to-speech output."
    override val muteVoiceSetting = "Mute voice"
    override val mapColours = "Map colours"
    override fun mapColors(option: MapColors) = option.label
    override val route = "Route"
    override val avoidTolls = "Avoid toll roads"
    override val avoidHighways = "Avoid highways"
    override val avoidFerries = "Avoid ferries"
    override val allowUnpaved = "Allow unpaved roads"
    override val speed = "Speed"
    override val showSpeedometer = "Show speedometer"
    override val showSpeedLimit = "Show speed limit"
    override fun limitDisplay(option: LimitDisplay) = option.label
    override val warnOverLimitBy = "Warn when over the limit by"
    override val soundWhenSpeeding = "Sound when speeding"
    override val mapView = "Map view"
    override val northUp = "North always up"
    override val showCompass = "Show compass"
    override val flatMap = "Flat map while driving (2D)"
    override val privacy = "Privacy"
    override val privacyText = "Home, Work and recent places are stored only on this phone."
    override val clearSavedPlaces = "Clear saved places"

    override fun resumedDrive(name: String) = "Resumed your drive to $name"
    override val arrived = "You have arrived"
    override fun searchFailed(error: String?) = "Search failed: $error"
    override val couldNotLoadPlaces = "Could not load places. Check your connection."
    override fun parkingFor(name: String) = "Parking · $name"
    override fun near(name: String) = "Near $name"
    override fun shortcutSaved(home: Boolean) = "${if (home) "Home" else "Work"} saved"
    override val waitingForLocation = "Waiting for your location. Enable GPS and try again."
    override fun routingFailed(error: String?) = "Routing failed: $error"
    override val savedPlacesCleared = "Saved places cleared"
    override fun couldNotStartNavigation(error: String?) = "Could not start navigation: $error"
    override fun stopAdded(name: String) = "Stop added: $name"
    override val chooseDestinationFirst = "Choose a destination first, then add stops"
    override fun saved(name: String) = "Saved: $name"
    override fun removed(name: String) = "Removed: $name"
    override val locationUnavailable = "Location unavailable"
    override val parkingSpotSaved = "Parking spot saved"
    override val thanks = "Thanks!"
    override val locationUnavailableReport = "Location unavailable, cannot place report"
    override fun reported(t: ReportType) = "${t.label} reported. Thanks!"
    override val couldNotSendReport = "Could not send report. Check your connection and try again."

    override val leaveLater = "Leave later"
    override val planDrive = "Plan this drive"
    override val arriveBy = "Arrive by"
    override val today = "Today"
    override val tomorrow = "Tomorrow"
    override fun leaveAt(time: String, driveMinutes: Int, spareMinutes: Int) = "Leave at $time · $driveMinutes min drive + $spareMinutes min spare"
    override val setReminder = "Remind me"
    override val tooLate = "Too late to arrive by then: choose a later time."
    override fun reminderSet(time: String) = "Reminder set for $time"
    override fun plannedFor(time: String, tomorrow: Boolean) = "Arrive $time" + if (tomorrow) " tomorrow" else ""
    override val plannedDrives = "Planned drives"
    override fun timeToLeave(name: String) = "Time to leave for $name"
    override fun timeToLeaveText(driveMinutes: Int, arriveBy: String) = "About $driveMinutes min drive to arrive by $arriveBy. Tap for the route."
    override val leaveRemindersChannel = "Time to leave reminders"

    override val musicControls = "Music controls while driving"
    override val musicControlsHelp = "Play, pause and skip in Spotify, YouTube Music and other apps from the driving screen."
    override val musicNeedsAccess = "Android asks for notification access for this. Jalanna uses it only to see which music is playing, not to read your notifications."
    override val play = "Play"
    override val pause = "Pause"
    override val nextTrack = "Next track"
    override val previousTrack = "Previous track"

    override val profile = "Profile"
    override val nickname = "Nickname"
    override val nicknameHint = "Optional. Shown on your profile; no real name needed."
    override fun levelName(code: String) = when (code) {
        "ROAD_SCOUT" -> "Road Scout"
        "NAVIGATOR" -> "Navigator"
        "ROAD_GUARDIAN" -> "Road Guardian"
        "ROAD_LEGEND" -> "Road Legend"
        else -> "Learner"
    }
    override fun points(n: Int) = "$n points"
    override fun pointsToNext(points: Int, nextLevel: String) = "$points points to $nextLevel"
    override val topLevel = "Top level reached"
    override val statReports = "Reports"
    override val statConfirmed = "Confirmed by others"
    override val statVotes = "Reports checked"
    override val badges = "Badges"
    override fun badge(code: String) = when (code) {
        "FIRST_REPORT" -> "First report"
        "REPORTER_10" -> "10 reports"
        "REPORTER_50" -> "50 reports"
        "HELPFUL_10" -> "10 confirmations"
        "CHECKER_25" -> "25 checks"
        else -> code
    }
    override val howPointsWork = "10 points per report, 5 when another driver confirms it, 2 for checking someone's report. A report removed by moderators costs 20."
    override val profileNeedsServer = "Your profile needs the Jalanna server. Turn on \"Use my laptop's server\" in Settings."

    override val shareDrive = "Share drive"
    override fun shareText(destination: String, eta: String, url: String) = "I'll arrive at $eta at $destination. Follow my drive live: $url"
    override val sharingLive = "Sharing live"
    override val stopSharing = "Stop sharing"
    override val shareFailed = "Could not share the drive. Check the connection to the server."

    override val helpTraffic = "Help with live traffic"
    override val helpTrafficText = "When on, Jalanna sends your speed and position in short anonymous pieces while you navigate: no account or device ID, nothing from the first and last 500 m of a trip, and the server keeps only average road speeds. Turn off at any time."

    override val trafficConsentTitle = "Help with live traffic?"
    override val allow = "Allow"
    override val notNow = "Not now"
    override fun fasterRoute(savedMinutes: Int) = "Faster route found · saves $savedMinutes min"
    override val takeRoute = "Take it"
    override val keepRoute = "No thanks"
    override fun trafficDelay(minutes: Int) = "+$minutes min traffic"

    override val privacyPolicy = "Privacy policy"
    override val privacyPolicyText = listOf(
        "Draft for testing: to be reviewed for Malaysia's Personal Data Protection Act before public release.",
        "On your phone only: Home, Work, recent and saved places, planned drives, where you parked, and your settings.",
        "Sent to the Jalanna server: an anonymous device id (no name, email or phone number), the reports, votes and flags you make, and your nickname if you set one. Reports are public on the map without a name.",
        "Places you add (name, kind, note and location) are stored on the server with your anonymous device id. Only you see them until a moderator approves them; then they appear in everyone's search without your name. Export or delete them with your data.",
        "Problems you report with a place (which place, what is wrong, your note, and your position only if you choose \"It is where I am now\") are stored with your anonymous device id until a moderator has checked them. The correction to the place is kept without any link to you.",
        "Share my drive, only when you tap Share: destination, arrival time and position, for anyone with the link, until you stop, 30 minutes after arrival, or 12 hours. You see how many people have the link open; they are counted with a random number made up by each open page, nothing about them is kept.",
        "Searches: what you type and your approximate position go to the place search service (Photon, with OpenStreetMap data) and, when the Jalanna server is used, to that server to search Overture Maps places and places drivers added. Searches are not stored.",
        "Live traffic, only if you turn it on: speed and position in 2-minute pieces with no device id, nothing from the first and last 500 m of a trip. Only average road speeds are kept.",
        "Kept for: reports 90 days after they expire; road speed averages 12 months (no driver information).",
        "Your choices: export or delete your data below, and turn live traffic off at any time.",
    )
    override val exportData = "Export my data"
    override val deleteData = "Delete my data"
    override val deleteDataConfirm = "This deletes your reports, votes, flags, added places, nickname and share links from the server, and your saved places, planned drives and parking spot from this phone. It cannot be undone."
    override val delete = "Delete"
    override val dataDeleted = "Your data was deleted"
    override val deleteFailed = "Could not delete your data on the server. Check the connection and try again."
    override val exportFailed = "Could not get your data from the server."

    override fun navigatingTo(name: String) = "Navigating to $name"
    override val navigation = "Navigation"
    override fun inMeters(meters: Int) = "In $meters m: "
    override val notificationStop = "Stop"
    override val notificationChannel = "Shown while Jalanna is guiding a drive"

    override val mapTab = "Map"
    override val listTab = "List"
    override val goShort = "Go"
    override val currentRoute = "Current"
    override val otherRoutes = "Routes"
    override val noOtherRoutes = "No other routes found from here."

    override val shareYourDrive = "Share your drive"
    override val shareExplain = "Anyone with the link can follow your drive on a map until you arrive or stop sharing."
    override fun shareTo(destination: String) = "To $destination"
    override val sharedDrive = "Shared drive"
    override fun viewers(count: Int) = when (count) {
        0 -> "No one watching yet"
        1 -> "1 person watching"
        else -> "$count people watching"
    }

    override val general = "General"
    override val mapDisplay = "Map display"
    override val voiceAndSound = "Voice and sound"
    override val navigationSettings = "Navigation"
    override val vehicleDetails = "Vehicle details"
    override val alertsAndReports = "Alerts and reports"
    override val speedometer = "Speedometer"
    override val menu = "Menu"
    override val helpAndFeedback = "Help and feedback"
    override val helpTopics = listOf(
        "Report something on the road" to "Tap the yellow button, choose what you see, and add details if you like. It is sent at your position.",
        "Add a stop" to "Press and hold the map where you want to stop, then tap Add a stop.",
        "Plan a drive for later" to "Choose a destination, tap the clock and pick when you want to arrive. You get a reminder when it is time to leave.",
        "Share your drive" to "While driving, tap Share. Anyone with the link can follow you until you arrive.",
        "Reports, traffic and places" to "These need the Jalanna server. On iPhone, turn on the laptop server in Settings and use the same Wi-Fi.",
    )
    override val sendFeedback = "Send feedback by email"
    override fun version(v: String) = "Version $v"

    override val addPlace = "Add a missing place"
    override val placeName = "Name of the place"
    override val whatKindOfPlace = "What is it?"
    override fun placeKind(kind: com.roadpulse.app.data.CommunityPlaceKind) = when (kind) {
        com.roadpulse.app.data.CommunityPlaceKind.SHOP -> "Shop"
        com.roadpulse.app.data.CommunityPlaceKind.FOOD -> "Food"
        com.roadpulse.app.data.CommunityPlaceKind.CAFE -> "Cafe"
        com.roadpulse.app.data.CommunityPlaceKind.CLINIC -> "Clinic"
        com.roadpulse.app.data.CommunityPlaceKind.OFFICE -> "Office"
        com.roadpulse.app.data.CommunityPlaceKind.HOTEL -> "Hotel"
        com.roadpulse.app.data.CommunityPlaceKind.RESIDENCE -> "Residence"
        com.roadpulse.app.data.CommunityPlaceKind.BUILDING -> "Building"
        com.roadpulse.app.data.CommunityPlaceKind.ENTRANCE -> "Entrance"
        com.roadpulse.app.data.CommunityPlaceKind.PARKING -> "Parking"
        com.roadpulse.app.data.CommunityPlaceKind.MALL -> "Shopping mall"
        com.roadpulse.app.data.CommunityPlaceKind.HOSPITAL -> "Hospital"
        com.roadpulse.app.data.CommunityPlaceKind.FUEL -> "Petrol station"
        com.roadpulse.app.data.CommunityPlaceKind.SCHOOL -> "School"
        com.roadpulse.app.data.CommunityPlaceKind.WORSHIP -> "Place of worship"
        com.roadpulse.app.data.CommunityPlaceKind.BANK -> "Bank"
        com.roadpulse.app.data.CommunityPlaceKind.GYM -> "Gym"
        com.roadpulse.app.data.CommunityPlaceKind.OTHER -> "Other"
    }
    override val placeNoteHint = "Note (optional), e.g. Level 2, next to the lifts"
    override val placeAdded = "Thanks! You can find it in search now; others will after a quick check."
    override val reportPlaceProblem = "Report a problem with this place"
    override val whatIsWrong = "What is wrong?"
    override fun placeProblem(kind: com.roadpulse.app.data.PlaceProblemKind) = when (kind) {
        com.roadpulse.app.data.PlaceProblemKind.WRONG_POSITION -> "The pin is in the wrong spot"
        com.roadpulse.app.data.PlaceProblemKind.CLOSED -> "It has closed for good"
        com.roadpulse.app.data.PlaceProblemKind.NOT_THERE -> "It is not there"
    }
    override val placeIsWhereIAm = "It is where I am now"
    override val placeProblemNoteHint = "Where is it really? (street, building, landmark)"
    override val placeProblemSent = "Thanks! We will check it and fix the place."
    override val placeProblemFailed = "Could not send the report. Please try again later."
    override val placeAddFailed = "Could not add the place. Check the connection and try again."
    override val addedByDrivers = "Added by drivers"
    override val waitingForReview = "Waiting for review"
}

/** Modern Standard Arabic. Review sheet: docs/ARABIC_SCREENS.md. */
object ArabicStrings : AppStrings {
    override val rtl = true
    override val cancel = "إلغاء"
    override val done = "تم"
    override val back = "رجوع"
    override val close = "إغلاق"
    override val save = "حفظ"
    override val send = "إرسال"
    override val skip = "تخطٍّ"
    override val show = "عرض"
    override val hide = "إخفاء"
    override val clear = "مسح"

    override fun distance(km: Double) =
        stepMetres(km)?.let { "$it م" } ?: "${oneDecimal(km)} كم"
    override fun duration(minutes: Int) = if (minutes < 60) "$minutes د" else "${minutes / 60} س ${minutes % 60} د"
    override fun justNow() = "الآن"
    override fun minutesAgo(minutes: Long) = "منذ $minutes د"
    override fun hoursAgo(hours: Long) = "منذ $hours س"
    override val kmh = "كم/س"

    override val report = "بلّغ"
    override val settingsButton = "الإعدادات"
    override val showMyLocation = "أظهر موقعي"
    override val whereTo = "إلى أين؟"
    override val yourHomeAddress = "عنوان منزلك"
    override val yourWorkAddress = "عنوان عملك"
    override fun setShortcutHint(home: Boolean) = "تعيين ${if (home) "المنزل" else "العمل"}: ابحث عن العنوان واختره"
    override val home = "المنزل"
    override val work = "العمل"
    override val setAddress = "عيّن العنوان"
    override fun yourCarParked(age: String) = "سيارتك · رُكنت $age"
    override val forgetParking = "انسَ مكان الركن"
    override fun remove(name: String) = "إزالة $name"
    override val searchByVoice = "البحث بالصوت"
    override fun category(c: PlaceCategory) = when (c) {
        PlaceCategory.FUEL -> "وقود"
        PlaceCategory.PARKING -> "مواقف"
        PlaceCategory.FOOD -> "طعام"
        PlaceCategory.EV_CHARGING -> "شحن كهربائي"
        PlaceCategory.REST_AREA -> "استراحة"
        PlaceCategory.SPEED_CAMERA -> "كاميرا سرعة"
        PlaceCategory.SCHOOL_ZONE -> "مدرسة"
    }
    override fun categoryNearby(c: PlaceCategory) = "${category(c)} بالقرب منك"
    override fun noneWithin10Km(c: PlaceCategory) = "لا يوجد ${category(c)} ضمن 10 كم"
    override val paid = "مدفوع"
    override val free = "مجاني"
    override fun openStatus(status: com.roadpulse.app.data.OpenStatus) = when (status) {
        com.roadpulse.app.data.OpenStatus.AlwaysOpen -> "مفتوح 24 ساعة"
        is com.roadpulse.app.data.OpenStatus.Open -> "مفتوح · يغلق ${status.closesAt}"
        is com.roadpulse.app.data.OpenStatus.Closed -> status.opensAt?.let { "مغلق · يفتح $it" } ?: "مغلق"
    }

    override val destination = "الوجهة"
    override fun stop(number: Int, name: String) = "توقف $number: $name"
    override val removeStop = "إزالة التوقف"
    override fun carParksNearDestination(count: Int) =
        if (count == 1) "موقف سيارات واحد قرب وجهتك" else "$count مواقف سيارات قرب وجهتك"
    override val tipAddStop = "تلميح: اضغط مطولًا على الخريطة لإضافة توقف"
    override val goNow = "انطلق الآن"
    override val simulateDrive = "محاكاة رحلة"
    override fun via(road: String) = "عبر $road"
    override val tagFastest = "الأسرع"
    override val tagNoTolls = "بلا رسوم"
    override val tagShortest = "الأقصر"
    override fun tagExtraMinutes(minutes: Int) = "+$minutes د"
    override val tollRoad = "طريق برسوم"
    override val tollPriceUnknown = "رسوم: السعر غير معروف"
    override fun tollAbout(rm: String) = "رسوم تقريبًا $rm"
    override fun tollAtLeast(rm: String) = "رسوم $rm أو أكثر"
    override fun vehicle(v: Vehicle) = when (v) {
        Vehicle.CAR -> "سيارة"
        Vehicle.MOTORCYCLE -> "دراجة نارية"
        Vehicle.TAXI -> "تاكسي"
    }
    override fun avoiding(things: List<String>) = "مع تجنب " + things.joinToString(" و")
    override val avoidTollsShort = "الرسوم"
    override val avoidHighwaysShort = "الطرق السريعة"
    override val avoidFerriesShort = "العبّارات"
    override val unpavedAllowed = "مع السماح بالطرق غير المعبدة"

    override val muteVoice = "كتم الصوت"
    override val unmuteVoice = "إلغاء كتم الصوت"
    override val stopNavigation = "إيقاف الملاحة"
    override val overview = "عرض المسار"

    override fun reportType(t: ReportType) = when (t) {
        ReportType.POLICE -> "شرطة"
        ReportType.CRASH -> "حادث"
        ReportType.HAZARD -> "خطر"
        ReportType.TRAFFIC_JAM -> "ازدحام"
        ReportType.ROAD_CLOSED -> "طريق مغلق"
    }
    override fun reportDetail(t: ReportType, code: String) = when (code) {
        "VISIBLE" -> "ظاهرة"
        "HIDDEN" -> "مخفية"
        "OTHER_SIDE" -> "في الاتجاه المعاكس"
        "MINOR" -> "بسيط"
        "MAJOR" -> "كبير"
        "OBJECT_ON_ROAD" -> "جسم على الطريق"
        "POTHOLE" -> "حفرة"
        "STOPPED_VEHICLE" -> "مركبة متوقفة"
        "ANIMAL" -> "حيوان"
        "FLOOD" -> "فيضان"
        "CONSTRUCTION" -> "أعمال إنشاء"
        "MODERATE" -> "متوسط"
        "HEAVY" -> "شديد"
        "STANDSTILL" -> "متوقف تمامًا"
        "FULL" -> "مغلق كليًا"
        "LANE_BLOCKED" -> "مسار مغلق"
        else -> null
    }
    override fun reportedAgo(age: String) = "أُبلغ عنه $age"
    override fun confirmations(count: Int) = "أكّده $count"
    override val yourReport = "بلاغك. يمكن للسائقين الآخرين تأكيده."
    override val isItStillThere = "هل ما زال موجودًا؟"
    override val stillThere = "ما زال موجودًا"
    override val notThere = "غير موجود"
    override val reportAbuse = "الإبلاغ عن إساءة"
    override fun stillThereQuestion(t: ReportType) = "${reportType(t)}: هل ما زال موجودًا؟"
    override val howLong = "كم المدة؟"
    override fun closureDuration(minutes: Int?) = when (minutes) {
        null -> "لست متأكدًا"
        60 -> "ساعة"
        120 -> "ساعتان"
        1440 -> "طوال اليوم"
        else -> "${minutes / 60} ساعات"
    }
    override val whatsWrongWithReport = "ما المشكلة في هذا البلاغ؟"
    override fun flagReason(r: FlagReason) = when (r) {
        FlagReason.FAKE -> "بلاغ كاذب"
        FlagReason.SPAM -> "رسائل مزعجة"
        FlagReason.OFFENSIVE -> "مسيء"
        FlagReason.WRONG_LOCATION -> "موقع خاطئ"
    }

    override val driveHere = "انطلق إلى هنا"
    override val addStop = "أضف توقفًا"
    override val parkedHere = "ركنت هنا"
    override val savePlace = "حفظ المكان"
    override val placeNameHint = "الاسم، مثلًا النادي"
    override val droppedPin = "دبوس على الخريطة"

    override val settings = "الإعدادات"
    override val language = "اللغة"
    override fun noVoiceInstalled(language: String) =
        "لا يوجد صوت $language مثبّت على هذا الهاتف، لذلك ستبقى الإرشادات الصوتية بالإنجليزية. " +
            "ثبّته من إعدادات أندرويد > النظام > اللغات > تحويل النص إلى كلام."
    override val muteVoiceSetting = "كتم الصوت"
    override val mapColours = "ألوان الخريطة"
    override fun mapColors(option: MapColors) = when (option) {
        MapColors.AUTO -> "تلقائي (داكن بعد الغروب)"
        MapColors.LIGHT -> "فاتح دائمًا"
        MapColors.DARK -> "داكن دائمًا"
    }
    override val route = "المسار"
    override val avoidTolls = "تجنّب الطرق ذات الرسوم"
    override val avoidHighways = "تجنّب الطرق السريعة"
    override val avoidFerries = "تجنّب العبّارات"
    override val allowUnpaved = "السماح بالطرق غير المعبدة"
    override val speed = "السرعة"
    override val showSpeedometer = "إظهار عدّاد السرعة"
    override val showSpeedLimit = "إظهار حد السرعة"
    override fun limitDisplay(option: LimitDisplay) = when (option) {
        LimitDisplay.ALWAYS -> "دائمًا"
        LimitDisplay.WHEN_SPEEDING -> "عند تجاوز السرعة فقط"
        LimitDisplay.NEVER -> "أبدًا"
    }
    override val warnOverLimitBy = "التنبيه عند تجاوز الحد بمقدار"
    override val soundWhenSpeeding = "صوت تنبيه عند تجاوز السرعة"
    override val mapView = "عرض الخريطة"
    override val northUp = "الشمال للأعلى دائمًا"
    override val showCompass = "إظهار البوصلة"
    override val flatMap = "خريطة مسطحة أثناء القيادة (ثنائية الأبعاد)"
    override val privacy = "الخصوصية"
    override val privacyText = "يُحفظ المنزل والعمل والأماكن الأخيرة على هذا الهاتف فقط."
    override val clearSavedPlaces = "مسح الأماكن المحفوظة"

    override fun resumedDrive(name: String) = "تم استئناف رحلتك إلى $name"
    override val arrived = "لقد وصلت"
    override fun searchFailed(error: String?) = "فشل البحث: $error"
    override val couldNotLoadPlaces = "تعذّر تحميل الأماكن. تحقق من الاتصال."
    override fun parkingFor(name: String) = "موقف · $name"
    override fun near(name: String) = "قرب $name"
    override fun shortcutSaved(home: Boolean) = "تم حفظ ${if (home) "المنزل" else "العمل"}"
    override val waitingForLocation = "بانتظار موقعك. فعّل GPS وحاول مجددًا."
    override fun routingFailed(error: String?) = "تعذّر إيجاد مسار: $error"
    override val savedPlacesCleared = "تم مسح الأماكن المحفوظة"
    override fun couldNotStartNavigation(error: String?) = "تعذّر بدء الملاحة: $error"
    override fun stopAdded(name: String) = "تمت إضافة توقف: $name"
    override val chooseDestinationFirst = "اختر وجهة أولًا، ثم أضف التوقفات"
    override fun saved(name: String) = "تم الحفظ: $name"
    override fun removed(name: String) = "تمت الإزالة: $name"
    override val locationUnavailable = "الموقع غير متاح"
    override val parkingSpotSaved = "تم حفظ مكان الركن"
    override val thanks = "شكرًا!"
    override val locationUnavailableReport = "الموقع غير متاح، لا يمكن إضافة البلاغ"
    override fun reported(t: ReportType) = "تم الإبلاغ عن ${reportType(t)}. شكرًا!"
    override val couldNotSendReport = "تعذّر إرسال البلاغ. تحقق من الاتصال وحاول مجددًا."

    override val leaveLater = "انطلق لاحقًا"
    override val planDrive = "خطط لهذه الرحلة"
    override val arriveBy = "الوصول قبل"
    override val today = "اليوم"
    override val tomorrow = "غدًا"
    override fun leaveAt(time: String, driveMinutes: Int, spareMinutes: Int) = "انطلق الساعة $time · $driveMinutes د قيادة + $spareMinutes د احتياط"
    override val setReminder = "ذكّرني"
    override val tooLate = "فات الأوان للوصول في هذا الوقت: اختر وقتًا لاحقًا."
    override fun reminderSet(time: String) = "تم ضبط التذكير الساعة $time"
    override fun plannedFor(time: String, tomorrow: Boolean) = "الوصول $time" + if (tomorrow) " غدًا" else ""
    override val plannedDrives = "رحلات مخطط لها"
    override fun timeToLeave(name: String) = "حان وقت الانطلاق إلى $name"
    override fun timeToLeaveText(driveMinutes: Int, arriveBy: String) = "نحو $driveMinutes د قيادة للوصول قبل $arriveBy. اضغط لعرض المسار."
    override val leaveRemindersChannel = "تذكيرات وقت الانطلاق"

    override val musicControls = "التحكم بالموسيقى أثناء القيادة"
    override val musicControlsHelp = "شغّل وأوقف وتنقّل بين المقاطع في Spotify وYouTube Music وغيرها من شاشة القيادة."
    override val musicNeedsAccess = "سيطلب أندرويد إذن الوصول إلى الإشعارات. يستخدمه Jalanna فقط لمعرفة الموسيقى المشغّلة، ولا يقرأ إشعاراتك."
    override val play = "تشغيل"
    override val pause = "إيقاف مؤقت"
    override val nextTrack = "المقطع التالي"
    override val previousTrack = "المقطع السابق"

    override val profile = "الملف الشخصي"
    override val nickname = "الاسم المستعار"
    override val nicknameHint = "اختياري. يظهر في ملفك الشخصي، ولا حاجة لاسمك الحقيقي."
    override fun levelName(code: String) = when (code) {
        "ROAD_SCOUT" -> "مستكشف الطريق"
        "NAVIGATOR" -> "الملّاح"
        "ROAD_GUARDIAN" -> "حارس الطريق"
        "ROAD_LEGEND" -> "أسطورة الطريق"
        else -> "متعلّم"
    }
    override fun points(n: Int) = "$n نقطة"
    override fun pointsToNext(points: Int, nextLevel: String) = "$points نقطة للوصول إلى $nextLevel"
    override val topLevel = "وصلت إلى أعلى مستوى"
    override val statReports = "البلاغات"
    override val statConfirmed = "أكّدها آخرون"
    override val statVotes = "بلاغات تحققت منها"
    override val badges = "الأوسمة"
    override fun badge(code: String) = when (code) {
        "FIRST_REPORT" -> "أول بلاغ"
        "REPORTER_10" -> "10 بلاغات"
        "REPORTER_50" -> "50 بلاغًا"
        "HELPFUL_10" -> "10 تأكيدات"
        "CHECKER_25" -> "25 تحققًا"
        else -> code
    }
    override val howPointsWork = "10 نقاط لكل بلاغ، و5 عندما يؤكده سائق آخر، و2 عند التحقق من بلاغ غيرك. البلاغ الذي يحذفه المشرفون يخصم 20."
    override val profileNeedsServer = "يحتاج ملفك الشخصي إلى خادم Jalanna. فعّل \"Use my laptop's server\" في الإعدادات."

    override val shareDrive = "شارك الرحلة"
    override fun shareText(destination: String, eta: String, url: String) = "سأصل الساعة $eta إلى $destination. تابع رحلتي مباشرة: $url"
    override val sharingLive = "المشاركة مباشرة"
    override val stopSharing = "إيقاف المشاركة"
    override val shareFailed = "تعذّرت مشاركة الرحلة. تحقق من الاتصال بالخادم."

    override val helpTraffic = "ساعد في معرفة حركة المرور"
    override val helpTrafficText = "عند التفعيل، يرسل Jalanna سرعتك وموقعك على شكل أجزاء قصيرة مجهولة الهوية أثناء الملاحة: بلا حساب أو معرّف جهاز، ولا شيء من أول وآخر 500 م من الرحلة، ويحتفظ الخادم بمتوسط سرعات الطرق فقط. يمكنك إيقافه في أي وقت."

    override val trafficConsentTitle = "هل تساعد في معرفة حركة المرور؟"
    override val allow = "سماح"
    override val notNow = "ليس الآن"
    override fun fasterRoute(savedMinutes: Int) = "وُجد مسار أسرع · يوفّر $savedMinutes د"
    override val takeRoute = "اسلكه"
    override val keepRoute = "لا، شكرًا"
    override fun trafficDelay(minutes: Int) = "+$minutes د بسبب الازدحام"

    override val privacyPolicy = "سياسة الخصوصية"
    override val privacyPolicyText = listOf(
        "مسودة للاختبار: تحتاج إلى مراجعة وفق قانون حماية البيانات الشخصية الماليزي قبل الإطلاق العام.",
        "على هاتفك فقط: المنزل والعمل والأماكن الأخيرة والمحفوظة، والرحلات المخطط لها، ومكان ركن سيارتك، وإعداداتك.",
        "يُرسل إلى خادم Jalanna: معرّف جهاز مجهول (بلا اسم أو بريد أو رقم هاتف)، والبلاغات والتصويتات والإبلاغات التي تقوم بها، واسمك المستعار إن عيّنته. تظهر البلاغات على الخريطة بلا اسم.",
        "الأماكن التي تضيفها (الاسم والنوع والملاحظة والموقع) تُحفظ على الخادم مع معرّف جهازك المجهول. لا يراها غيرك حتى يوافق عليها مشرف، ثم تظهر في بحث الجميع دون اسمك. يمكنك تصديرها أو حذفها مع بياناتك.",
        "المشكلات التي تبلّغ عنها في مكان (أي مكان، ما المشكلة، ملاحظتك، وموقعك فقط إذا اخترت «المكان حيث أنا الآن») تُحفظ مع معرّف جهازك المجهول حتى يراجعها مشرف. يبقى تصحيح المكان دون أي ربط بك.",
        "مشاركة الرحلة، فقط عند الضغط على مشاركة: الوجهة ووقت الوصول والموقع لمن لديه الرابط، حتى تتوقف أو بعد 30 دقيقة من الوصول أو 12 ساعة. ترى عدد من يفتحون الرابط؛ يُحسبون برقم عشوائي تنشئه كل صفحة مفتوحة، ولا يُحفظ عنهم شيء.",
        "حركة المرور، فقط إذا فعّلتها: السرعة والموقع على شكل أجزاء مدتها دقيقتان بلا معرّف جهاز، ولا شيء من أول وآخر 500 م من الرحلة. يُحتفظ بمتوسط سرعات الطرق فقط.",
        "مدة الحفظ: البلاغات 90 يومًا بعد انتهائها، ومتوسط سرعات الطرق 12 شهرًا (بلا معلومات عن السائقين).",
        "عمليات البحث: ما تكتبه وموقعك التقريبي يُرسلان إلى خدمة البحث عن الأماكن (Photon ببيانات OpenStreetMap)، وعند استخدام خادم Jalanna إلى ذلك الخادم للبحث في أماكن Overture Maps والأماكن التي أضافها السائقون. لا تُحفظ عمليات البحث.",
        "خياراتك: صدّر بياناتك أو احذفها أدناه، وأوقف مشاركة حركة المرور في أي وقت.",
    )
    override val exportData = "تصدير بياناتي"
    override val deleteData = "حذف بياناتي"
    override val deleteDataConfirm = "سيحذف هذا بلاغاتك وتصويتاتك وإبلاغاتك والأماكن التي أضفتها واسمك المستعار وروابط المشاركة من الخادم، وأماكنك المحفوظة ورحلاتك المخطط لها ومكان ركن سيارتك من هذا الهاتف. لا يمكن التراجع عن ذلك."
    override val delete = "حذف"
    override val dataDeleted = "تم حذف بياناتك"
    override val deleteFailed = "تعذّر حذف بياناتك من الخادم. تحقق من الاتصال وحاول مجددًا."
    override val exportFailed = "تعذّر الحصول على بياناتك من الخادم."

    override fun navigatingTo(name: String) = "الملاحة إلى $name"
    override val navigation = "الملاحة"
    override fun inMeters(meters: Int) = "بعد $meters م: "
    override val notificationStop = "إيقاف"
    override val notificationChannel = "يظهر أثناء إرشاد Jalanna لرحلتك"

    override val mapTab = "خريطة"
    override val listTab = "قائمة"
    override val goShort = "انطلق"
    override val currentRoute = "الحالي"
    override val otherRoutes = "المسارات"
    override val noOtherRoutes = "لا توجد مسارات أخرى من هنا."

    override val shareYourDrive = "شارك رحلتك"
    override val shareExplain = "يمكن لأي شخص لديه الرابط متابعة رحلتك على الخريطة حتى تصل أو توقف المشاركة."
    override fun shareTo(destination: String) = "إلى $destination"
    override val sharedDrive = "رحلة مُشارَكة"
    override fun viewers(count: Int) = when (count) {
        0 -> "لا أحد يتابع بعد"
        1 -> "شخص واحد يتابع"
        2 -> "شخصان يتابعان"
        in 3..10 -> "$count أشخاص يتابعون"
        else -> "$count شخصًا يتابعون"
    }

    override val general = "عام"
    override val mapDisplay = "عرض الخريطة"
    override val voiceAndSound = "الصوت"
    override val navigationSettings = "الملاحة"
    override val vehicleDetails = "تفاصيل المركبة"
    override val alertsAndReports = "التنبيهات والبلاغات"
    override val speedometer = "عداد السرعة"
    override val menu = "القائمة"
    override val helpAndFeedback = "المساعدة والملاحظات"
    override val helpTopics = listOf(
        "الإبلاغ عن شيء على الطريق" to "اضغط الزر الأصفر واختر ما تراه، وأضف التفاصيل إن أردت. يُرسل البلاغ عند موقعك.",
        "إضافة محطة توقف" to "اضغط مطولًا على الخريطة حيث تريد التوقف، ثم اختر إضافة محطة.",
        "التخطيط لرحلة لاحقة" to "اختر الوجهة، واضغط الساعة وحدد وقت الوصول. ستصلك تذكرة عندما يحين وقت الانطلاق.",
        "مشاركة رحلتك" to "أثناء القيادة اضغط مشاركة. يمكن لأي شخص لديه الرابط متابعتك حتى تصل.",
        "البلاغات والازدحام والأماكن" to "تحتاج إلى خادم Jalanna. على iPhone فعّل خادم الحاسوب في الإعدادات واستخدم شبكة Wi-Fi نفسها.",
    )
    override val sendFeedback = "أرسل ملاحظاتك بالبريد"
    override fun version(v: String) = "الإصدار $v"

    override val addPlace = "أضف مكانًا غير موجود"
    override val placeName = "اسم المكان"
    override val whatKindOfPlace = "ما نوعه؟"
    override fun placeKind(kind: com.roadpulse.app.data.CommunityPlaceKind) = when (kind) {
        com.roadpulse.app.data.CommunityPlaceKind.SHOP -> "متجر"
        com.roadpulse.app.data.CommunityPlaceKind.FOOD -> "مطعم"
        com.roadpulse.app.data.CommunityPlaceKind.CAFE -> "مقهى"
        com.roadpulse.app.data.CommunityPlaceKind.CLINIC -> "عيادة"
        com.roadpulse.app.data.CommunityPlaceKind.OFFICE -> "مكتب"
        com.roadpulse.app.data.CommunityPlaceKind.HOTEL -> "فندق"
        com.roadpulse.app.data.CommunityPlaceKind.RESIDENCE -> "سكن"
        com.roadpulse.app.data.CommunityPlaceKind.BUILDING -> "مبنى"
        com.roadpulse.app.data.CommunityPlaceKind.ENTRANCE -> "مدخل"
        com.roadpulse.app.data.CommunityPlaceKind.PARKING -> "موقف سيارات"
        com.roadpulse.app.data.CommunityPlaceKind.MALL -> "مركز تسوق"
        com.roadpulse.app.data.CommunityPlaceKind.HOSPITAL -> "مستشفى"
        com.roadpulse.app.data.CommunityPlaceKind.FUEL -> "محطة وقود"
        com.roadpulse.app.data.CommunityPlaceKind.SCHOOL -> "مدرسة"
        com.roadpulse.app.data.CommunityPlaceKind.WORSHIP -> "دار عبادة"
        com.roadpulse.app.data.CommunityPlaceKind.BANK -> "بنك"
        com.roadpulse.app.data.CommunityPlaceKind.GYM -> "نادٍ رياضي"
        com.roadpulse.app.data.CommunityPlaceKind.OTHER -> "أخرى"
    }
    override val placeNoteHint = "ملاحظة (اختيارية)، مثل: الطابق 2 بجانب المصاعد"
    override val placeAdded = "شكرًا! يمكنك إيجاده في البحث الآن، وسيراه الآخرون بعد مراجعة سريعة."
    override val reportPlaceProblem = "الإبلاغ عن مشكلة في هذا المكان"
    override val whatIsWrong = "ما المشكلة؟"
    override fun placeProblem(kind: com.roadpulse.app.data.PlaceProblemKind) = when (kind) {
        com.roadpulse.app.data.PlaceProblemKind.WRONG_POSITION -> "الموقع على الخريطة خاطئ"
        com.roadpulse.app.data.PlaceProblemKind.CLOSED -> "أُغلق نهائيًا"
        com.roadpulse.app.data.PlaceProblemKind.NOT_THERE -> "غير موجود هناك"
    }
    override val placeIsWhereIAm = "المكان حيث أنا الآن"
    override val placeProblemNoteHint = "أين هو فعلًا؟ (الشارع، المبنى، معلم قريب)"
    override val placeProblemSent = "شكرًا! سنتحقق ونصحّح المكان."
    override val placeProblemFailed = "تعذّر إرسال البلاغ. حاول مرة أخرى لاحقًا."
    override val placeAddFailed = "تعذّرت إضافة المكان. تحقق من الاتصال وحاول مجددًا."
    override val addedByDrivers = "أضافه السائقون"
    override val waitingForReview = "بانتظار المراجعة"
}

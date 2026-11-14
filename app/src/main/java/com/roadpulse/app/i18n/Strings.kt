package com.roadpulse.app.i18n

import com.roadpulse.app.data.FlagReason
import com.roadpulse.app.data.PlaceCategory
import com.roadpulse.app.data.ReportType
import com.roadpulse.app.data.Vehicle
import com.roadpulse.app.nav.GuidanceLanguage
import com.roadpulse.app.nav.LimitDisplay
import com.roadpulse.app.nav.MapColors
import java.util.Locale

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

    // Navigation notification
    fun navigatingTo(name: String): String
    val navigation: String
    fun inMeters(meters: Int): String
    val notificationStop: String
    val notificationChannel: String
}

/** Screen language for a chosen app language. */
fun stringsFor(language: GuidanceLanguage): AppStrings = when (language) {
    GuidanceLanguage.AR -> ArabicStrings
    GuidanceLanguage.EN, GuidanceLanguage.MS -> EnglishStrings
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
        if (km < 1) "${Math.round(km * 1000)} m" else String.format(Locale.ROOT, "%.1f km", km)
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

    override fun navigatingTo(name: String) = "Navigating to $name"
    override val navigation = "Navigation"
    override fun inMeters(meters: Int) = "In $meters m: "
    override val notificationStop = "Stop"
    override val notificationChannel = "Shown while RoadPulse is guiding a drive"
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
        if (km < 1) "${Math.round(km * 1000)} م" else String.format(Locale.ROOT, "%.1f كم", km)
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

    override fun navigatingTo(name: String) = "الملاحة إلى $name"
    override val navigation = "الملاحة"
    override fun inMeters(meters: Int) = "بعد $meters م: "
    override val notificationStop = "إيقاف"
    override val notificationChannel = "يظهر أثناء إرشاد RoadPulse لرحلتك"
}

/** Screen language of the current composition (provided at the root, see MainActivity). */
val LocalStrings = androidx.compose.runtime.staticCompositionLocalOf<AppStrings> { EnglishStrings }

/** Shortcut for composables: `strings.cancel`. */
val strings: AppStrings
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = LocalStrings.current

/** Screen language outside composition (view model messages, notification). */
fun currentStrings(): AppStrings = stringsFor(com.roadpulse.app.nav.VoiceSettings.language.value)

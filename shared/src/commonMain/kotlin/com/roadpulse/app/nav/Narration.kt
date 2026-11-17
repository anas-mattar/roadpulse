package com.roadpulse.app.nav

import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.ReportType

/** Languages for voice prompts and the navigation banner. */
/** [localeTag] is the language tag for the text-to-speech voice, e.g. "ms-MY". */
enum class GuidanceLanguage(val code: String, val label: String, val localeTag: String) {
    EN("en", "English", "en-US"),
    MS("ms", "Bahasa Melayu", "ms-MY"),
    AR("ar", "العربية (Arabic)", "ar");

    companion object {
        fun fromCode(code: String?): GuidanceLanguage? = entries.firstOrNull { it.code == code }
    }
}

/** Everything RoadPulse says or shows during a drive, in one language. Pure Kotlin: unit tested. */
interface Narrator {
    /** Banner text for an upcoming maneuver. */
    fun banner(m: Maneuver): String
    /** Spoken at the maneuver (and as the opening instruction). */
    fun atTurn(m: Maneuver): String
    /** Spoken ahead of the maneuver, e.g. "In 450 meters, turn right onto Jalan Ampang." */
    fun alert(m: Maneuver, meters: Int): String

    /** At-turn prompt that also announces a [next] turn coming right after. */
    fun atTurnThen(m: Maneuver, next: Maneuver): String
    /** Advance prompt that also announces a [next] turn coming right after. */
    fun alertThen(m: Maneuver, meters: Int, next: Maneuver): String

    val arrived: String
    val rerouting: String
    val gpsLost: String
    val noInternet: String
    fun resuming(destination: String): String

    /** Banner distance label, e.g. "In 421 m". */
    fun inDistance(distance: String): String
    val followRoute: String
    /** Prefix for the next-but-one turn hint, e.g. "Then". */
    val thenLabel: String
    /** Button that brings the camera back to the car. */
    val recenter: String
    val statusFindingRoute: String
    val statusOffline: String
    val statusGpsLost: String
    val statusWeakGps: String

    /** Short name of a report type, as shown and spoken ("Police", "Road closure"). */
    fun reportName(type: ReportType): String
    /** Warning for a report on the route ahead, e.g. "Police reported 700 meters ahead." */
    fun reportAhead(type: ReportType, meters: Int): String
    /** Warning for a fixed speed camera ahead, with its limit when known. */
    fun cameraAhead(meters: Int, limitKph: Int?): String
    /** Short label for the speed camera hint. */
    val speedCamera: String
    /** Warning for a school zone on the route ahead. */
    fun schoolZoneAhead(meters: Int): String
    /** Short label for the school zone hint. */
    val schoolZone: String
    /** Spoken when a route around heavy traffic would save time. */
    fun fasterRouteFound(savedMinutes: Int): String
    /** Spoken when a road on the route ahead was reported as closed and a route around it is ready. */
    val closureAheadNewRoute: String
}

fun narratorFor(language: GuidanceLanguage): Narrator = when (language) {
    GuidanceLanguage.EN -> EnglishNarrator
    GuidanceLanguage.MS -> MalayNarrator
    GuidanceLanguage.AR -> ArabicNarrator
}

private fun String.lowercaseFirst() = replaceFirstChar { it.lowercase() }
private fun String.ensurePeriod() = trimEnd().let { if (it.endsWith(".")) it else "$it." }

/** English: the routing server's own, field-tested wording. */
object EnglishNarrator : Narrator {
    override fun banner(m: Maneuver) = m.instruction
    override fun atTurn(m: Maneuver) = m.verbalPre ?: m.instruction
    override fun alert(m: Maneuver, meters: Int) =
        "In $meters meters, ${(m.verbalAlert ?: m.instruction).lowercaseFirst()}"

    /** The server already chains some turns ("Turn right. Then turn left."); keep its wording then. */
    private fun alreadyChained(text: String) = text.contains(" Then ", ignoreCase = true) || text.contains(", then ")

    private fun short(m: Maneuver) = (m.verbalAlert ?: m.instruction).trimEnd('.').lowercaseFirst()

    override fun atTurnThen(m: Maneuver, next: Maneuver): String {
        val base = atTurn(m)
        return if (alreadyChained(base)) base else "${base.trimEnd('.')}, then ${short(next)}."
    }

    override fun alertThen(m: Maneuver, meters: Int, next: Maneuver): String {
        val base = alert(m, meters)
        return if (alreadyChained(base)) base else "${base.trimEnd('.')}, then ${short(next)}."
    }

    override val arrived = "You have arrived."
    override val rerouting = "Rerouting."
    override val gpsLost = "GPS signal lost. Keep following the road."
    override val noInternet = "No internet connection. Keeping your current route."
    override fun resuming(destination: String) = "Resuming your drive to $destination."
    override fun inDistance(distance: String) = "In $distance"
    override val followRoute = "Follow the route"
    override val thenLabel = "Then"
    override val recenter = "Re-center"
    override val statusFindingRoute = "Finding a new route…"
    override val statusOffline = "Offline: keeping your current route"
    override val statusGpsLost = "GPS signal lost: keep following the road"
    override val statusWeakGps = "Weak GPS signal"

    override fun reportName(type: ReportType) = when (type) {
        ReportType.POLICE -> "Police"
        ReportType.CRASH -> "Crash"
        ReportType.HAZARD -> "Hazard"
        ReportType.TRAFFIC_JAM -> "Traffic jam"
        ReportType.ROAD_CLOSED -> "Road closure"
    }
    override fun reportAhead(type: ReportType, meters: Int) = "${reportName(type)} reported $meters meters ahead."
    override fun cameraAhead(meters: Int, limitKph: Int?) =
        "Speed camera in $meters meters." + (limitKph?.let { " Limit $it." } ?: "")
    override val speedCamera = "Speed camera"
    override fun schoolZoneAhead(meters: Int) = "School zone in $meters meters. Watch for children."
    override val schoolZone = "School zone"
    override fun fasterRouteFound(savedMinutes: Int) = "Heavy traffic ahead. A faster route saves $savedMinutes minutes."
    override val closureAheadNewRoute = "Road closed ahead. A new route is ready."
}

/**
 * Bahasa Melayu, generated from the maneuver data (the routing server has no Malay).
 * All Malay wording lives in this object so a native speaker can review it in one place.
 * Unknown maneuver types fall back to the English text rather than guessing.
 */
object MalayNarrator : Narrator {

    /** Core instruction without final punctuation, or null for an unsupported maneuver type. */
    fun core(m: Maneuver): String? {
        val name = m.streetNames.firstOrNull()
        val onto = name?.let { " ke $it" }.orEmpty()
        val target = (m.exitBranch.firstOrNull() ?: name)
        val toward = m.exitToward.take(2).joinToString(", ").let { if (it.isBlank()) "" else " menuju $it" }
        return when (m.type) {
            1, 2, 3 -> {
                val dir = m.bearingAfter?.let { " ke arah ${compass(it)}" }.orEmpty()
                "Pandu$dir" + (name?.let { " di $it" }.orEmpty())
            }
            4 -> "Anda telah tiba di destinasi"
            com.roadpulse.app.data.ManeuverTypes.STOP_REACHED -> "Tiba di hentian anda"
            5 -> "Destinasi anda di sebelah kanan"
            6 -> "Destinasi anda di sebelah kiri"
            7 -> "Teruskan$onto"
            8 -> "Teruskan" + (name?.let { " di $it" }.orEmpty())
            9 -> "Belok kanan sedikit$onto"
            10 -> "Belok kanan$onto"
            11 -> "Belok tajam ke kanan$onto"
            12, 13 -> "Buat pusingan U$onto"
            14 -> "Belok tajam ke kiri$onto"
            15 -> "Belok kiri$onto"
            16 -> "Belok kiri sedikit$onto"
            17 -> "Terus ke susur" + (target?.let { " ke $it" }.orEmpty()) + toward
            18 -> "Ambil susur di sebelah kanan" + (target?.let { " ke $it" }.orEmpty()) + toward
            19 -> "Ambil susur di sebelah kiri" + (target?.let { " ke $it" }.orEmpty()) + toward
            20, 21 -> {
                val side = if (m.type == 20) "kanan" else "kiri"
                val number = m.exitNumber?.let { " $it" }.orEmpty()
                "Ambil susur keluar$number di sebelah $side" + (target?.let { " ke $it" }.orEmpty()) + toward
            }
            22, 23, 24 -> {
                val keep = when (m.type) {
                    22 -> "Kekal lurus"
                    23 -> "Kekal di sebelah kanan"
                    else -> "Kekal di sebelah kiri"
                }
                when {
                    target != null -> "$keep untuk masuk $target$toward"
                    toward.isNotEmpty() -> "$keep$toward"
                    else -> "$keep di persimpangan"
                }
            }
            25, 37, 38 -> name?.let { "Bergabung ke $it" } ?: "Bergabung dengan lalu lintas"
            26 -> m.roundaboutExitCount?.let { "Masuk ke bulatan dan ambil jalan keluar ${ordinal(it)}$onto" }
                ?: "Masuk ke bulatan"
            27 -> "Keluar dari bulatan$onto"
            28 -> "Naik feri"
            29 -> "Turun dari feri$onto"
            else -> null
        }
    }

    override fun banner(m: Maneuver) = core(m)?.ensurePeriod() ?: m.instruction
    override fun atTurn(m: Maneuver) = core(m)?.ensurePeriod() ?: (m.verbalPre ?: m.instruction)
    override fun alert(m: Maneuver, meters: Int): String {
        val c = core(m) ?: return EnglishNarrator.alert(m, meters)
        return "Dalam $meters meter, ${c.lowercaseFirst()}".ensurePeriod()
    }

    override fun atTurnThen(m: Maneuver, next: Maneuver): String {
        val c = core(m) ?: return EnglishNarrator.atTurnThen(m, next)
        val n = core(next) ?: return atTurn(m)
        return "$c, kemudian ${n.lowercaseFirst()}".ensurePeriod()
    }

    override fun alertThen(m: Maneuver, meters: Int, next: Maneuver): String {
        val c = core(m) ?: return EnglishNarrator.alertThen(m, meters, next)
        val n = core(next) ?: return alert(m, meters)
        return "Dalam $meters meter, ${c.lowercaseFirst()}, kemudian ${n.lowercaseFirst()}".ensurePeriod()
    }

    override val arrived = "Anda telah tiba."
    override val rerouting = "Mencari laluan baharu."
    override val gpsLost = "Isyarat GPS hilang. Teruskan mengikut jalan."
    override val noInternet = "Tiada sambungan internet. Laluan semasa dikekalkan."
    override fun resuming(destination: String) = "Menyambung perjalanan ke $destination."
    override fun inDistance(distance: String) = "Dalam $distance"
    override val followRoute = "Ikut laluan"
    override val thenLabel = "Kemudian"
    override val recenter = "Fokus semula"
    override val statusFindingRoute = "Mencari laluan baharu…"
    override val statusOffline = "Luar talian: laluan semasa dikekalkan"
    override val statusGpsLost = "Isyarat GPS hilang: teruskan mengikut jalan"
    override val statusWeakGps = "Isyarat GPS lemah"

    override fun reportName(type: ReportType) = when (type) {
        ReportType.POLICE -> "Polis"
        ReportType.CRASH -> "Kemalangan"
        ReportType.HAZARD -> "Bahaya"
        ReportType.TRAFFIC_JAM -> "Kesesakan lalu lintas"
        ReportType.ROAD_CLOSED -> "Penutupan jalan"
    }
    override fun reportAhead(type: ReportType, meters: Int) = "${reportName(type)} dilaporkan $meters meter di hadapan."
    override fun cameraAhead(meters: Int, limitKph: Int?) =
        "Kamera had laju dalam $meters meter." + (limitKph?.let { " Had laju $it." } ?: "")
    override val speedCamera = "Kamera had laju"
    override fun schoolZoneAhead(meters: Int) = "Zon sekolah dalam $meters meter. Berhati-hati dengan kanak-kanak."
    override val schoolZone = "Zon sekolah"
    override fun fasterRouteFound(savedMinutes: Int) = "Kesesakan teruk di hadapan. Laluan lebih pantas menjimatkan $savedMinutes minit."
    override val closureAheadNewRoute = "Jalan ditutup di hadapan. Laluan baharu tersedia."

    private val ordinals = listOf(
        "pertama", "kedua", "ketiga", "keempat", "kelima",
        "keenam", "ketujuh", "kelapan", "kesembilan", "kesepuluh",
    )

    fun ordinal(n: Int): String = ordinals.getOrNull(n - 1) ?: "ke-$n"

    fun compass(bearing: Int): String {
        val names = listOf("utara", "timur laut", "timur", "tenggara", "selatan", "barat daya", "barat", "barat laut")
        return names[(((bearing % 360) + 360) % 360 + 22) / 45 % 8]
    }
}

/**
 * Modern Standard Arabic, generated from the maneuver data like [MalayNarrator] (the routing server
 * has no Arabic). All Arabic wording lives here for review. Street names stay as on the map
 * (usually Malay in Latin letters). Unknown maneuver types fall back to the English text.
 */
object ArabicNarrator : Narrator {

    fun core(m: Maneuver): String? {
        val name = m.streetNames.firstOrNull()
        val onto = name?.let { " إلى $it" }.orEmpty()
        val target = (m.exitBranch.firstOrNull() ?: name)
        val toward = m.exitToward.take(2).joinToString("، ").let { if (it.isBlank()) "" else " باتجاه $it" }
        return when (m.type) {
            1, 2, 3 -> {
                val dir = m.bearingAfter?.let { " باتجاه ${compass(it)}" }.orEmpty()
                "انطلق$dir" + (name?.let { " على $it" }.orEmpty())
            }
            4 -> "لقد وصلت إلى وجهتك"
            com.roadpulse.app.data.ManeuverTypes.STOP_REACHED -> "لقد وصلت إلى محطة التوقف"
            5 -> "وجهتك على اليمين"
            6 -> "وجهتك على اليسار"
            7 -> "تابع$onto"
            8 -> "تابع" + (name?.let { " على $it" }.orEmpty())
            9 -> "انعطف قليلًا إلى اليمين$onto"
            10 -> "انعطف يمينًا$onto"
            11 -> "انعطف بحدة إلى اليمين$onto"
            12, 13 -> "استدر للخلف$onto"
            14 -> "انعطف بحدة إلى اليسار$onto"
            15 -> "انعطف يسارًا$onto"
            16 -> "انعطف قليلًا إلى اليسار$onto"
            17 -> "تابع مباشرة إلى المنحدر" + (target?.let { " نحو $it" }.orEmpty()) + toward
            18 -> "اسلك المنحدر على اليمين" + (target?.let { " نحو $it" }.orEmpty()) + toward
            19 -> "اسلك المنحدر على اليسار" + (target?.let { " نحو $it" }.orEmpty()) + toward
            20, 21 -> {
                val side = if (m.type == 20) "اليمين" else "اليسار"
                val number = m.exitNumber?.let { " رقم $it" }.orEmpty()
                "اسلك المخرج$number على $side" + (target?.let { " نحو $it" }.orEmpty()) + toward
            }
            22, 23, 24 -> {
                val keep = when (m.type) {
                    22 -> "تابع مباشرة"
                    23 -> "الزم اليمين"
                    else -> "الزم اليسار"
                }
                when {
                    target != null -> "$keep للدخول إلى $target$toward"
                    toward.isNotEmpty() -> "$keep$toward"
                    else -> "$keep عند التفرع"
                }
            }
            25, 37, 38 -> name?.let { "اندمج في $it" } ?: "اندمج مع حركة المرور"
            26 -> m.roundaboutExitCount?.let { "ادخل الدوار واسلك المخرج ${ordinal(it)}$onto" } ?: "ادخل الدوار"
            27 -> "اخرج من الدوار$onto"
            28 -> "استقل العبارة"
            29 -> "انزل من العبارة$onto"
            else -> null
        }
    }

    private fun String.withStop() = trimEnd().let { if (it.endsWith(".")) it else "$it." }

    /** "450 مترًا", "200 متر", "5 أمتار": the word for metres agrees with the number. */
    fun meters(n: Int): String {
        val lastTwo = n % 100
        val word = when {
            n == 1 -> return "متر واحد"
            n == 2 -> return "متران"
            lastTwo == 0 -> "متر"
            lastTwo in 3..10 -> "أمتار"
            lastTwo in 1..2 -> "متر"
            else -> "مترًا"
        }
        return "$n $word"
    }

    override fun banner(m: Maneuver) = core(m)?.withStop() ?: m.instruction
    override fun atTurn(m: Maneuver) = core(m)?.withStop() ?: (m.verbalPre ?: m.instruction)
    override fun alert(m: Maneuver, meters: Int): String {
        val c = core(m) ?: return EnglishNarrator.alert(m, meters)
        return "بعد ${meters(meters)}، $c".withStop()
    }

    override fun atTurnThen(m: Maneuver, next: Maneuver): String {
        val c = core(m) ?: return EnglishNarrator.atTurnThen(m, next)
        val n = core(next) ?: return atTurn(m)
        return "$c، ثم $n".withStop()
    }

    override fun alertThen(m: Maneuver, meters: Int, next: Maneuver): String {
        val c = core(m) ?: return EnglishNarrator.alertThen(m, meters, next)
        val n = core(next) ?: return alert(m, meters)
        return "بعد ${meters(meters)}، $c، ثم $n".withStop()
    }

    override val arrived = "لقد وصلت."
    override val rerouting = "جارٍ البحث عن مسار جديد."
    override val gpsLost = "فُقدت إشارة GPS. تابع السير على الطريق."
    override val noInternet = "لا يوجد اتصال بالإنترنت. سيبقى المسار الحالي."
    override fun resuming(destination: String) = "متابعة الرحلة إلى $destination."
    override fun inDistance(distance: String) = "بعد $distance"
    override val followRoute = "اتبع المسار"
    override val thenLabel = "ثم"
    override val recenter = "عودة إلى موقعي"
    override val statusFindingRoute = "جارٍ البحث عن مسار جديد…"
    override val statusOffline = "غير متصل: سيبقى المسار الحالي"
    override val statusGpsLost = "فُقدت إشارة GPS: تابع السير على الطريق"
    override val statusWeakGps = "إشارة GPS ضعيفة"

    override fun reportName(type: ReportType) = when (type) {
        ReportType.POLICE -> "شرطة"
        ReportType.CRASH -> "حادث"
        ReportType.HAZARD -> "خطر"
        ReportType.TRAFFIC_JAM -> "ازدحام مروري"
        ReportType.ROAD_CLOSED -> "إغلاق طريق"
    }
    override fun reportAhead(type: ReportType, meters: Int) = "${reportName(type)} على بعد ${meters(meters)} أمامك."
    override fun cameraAhead(meters: Int, limitKph: Int?) =
        "كاميرا سرعة بعد ${meters(meters)}." + (limitKph?.let { " الحد الأقصى $it." } ?: "")
    override val speedCamera = "كاميرا سرعة"
    override fun schoolZoneAhead(meters: Int) = "منطقة مدرسة بعد ${meters(meters)}. انتبه للأطفال."
    override val schoolZone = "منطقة مدرسة"
    override fun fasterRouteFound(savedMinutes: Int) = "ازدحام شديد أمامك. يوجد مسار أسرع يوفّر $savedMinutes ${if (savedMinutes in 3..10) "دقائق" else "دقيقة"}."
    override val closureAheadNewRoute = "الطريق مغلق أمامك. يوجد مسار جديد جاهز."

    private val ordinals = listOf("الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر")

    fun ordinal(n: Int): String = ordinals.getOrNull(n - 1) ?: "رقم $n"

    fun compass(bearing: Int): String {
        val names = listOf("الشمال", "الشمال الشرقي", "الشرق", "الجنوب الشرقي", "الجنوب", "الجنوب الغربي", "الغرب", "الشمال الغربي")
        return names[(((bearing % 360) + 360) % 360 + 22) / 45 % 8]
    }
}

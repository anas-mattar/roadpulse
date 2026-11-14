package com.roadpulse.app.nav

import com.roadpulse.app.data.Maneuver
import java.util.Locale

/** Languages for voice prompts and the navigation banner. */
enum class GuidanceLanguage(val code: String, val label: String, val locale: Locale) {
    EN("en", "English", Locale.US),
    MS("ms", "Bahasa Melayu", Locale("ms", "MY"));

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
}

fun narratorFor(language: GuidanceLanguage): Narrator = when (language) {
    GuidanceLanguage.EN -> EnglishNarrator
    GuidanceLanguage.MS -> MalayNarrator
}

private fun String.lowercaseFirst() = replaceFirstChar { it.lowercase() }
private fun String.ensurePeriod() = trimEnd().let { if (it.endsWith(".")) it else "$it." }

/** English: the routing server's own, field-tested wording. */
object EnglishNarrator : Narrator {
    override fun banner(m: Maneuver) = m.instruction
    override fun atTurn(m: Maneuver) = m.verbalPre ?: m.instruction
    override fun alert(m: Maneuver, meters: Int) =
        "In $meters meters, ${(m.verbalAlert ?: m.instruction).lowercaseFirst()}"

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

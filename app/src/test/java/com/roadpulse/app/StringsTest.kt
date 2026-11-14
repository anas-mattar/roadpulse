package com.roadpulse.app

import com.roadpulse.app.data.FlagReason
import com.roadpulse.app.data.PlaceCategory
import com.roadpulse.app.data.ReportDetails
import com.roadpulse.app.data.ReportType
import com.roadpulse.app.data.Vehicle
import com.roadpulse.app.i18n.AppStrings
import com.roadpulse.app.i18n.ArabicStrings
import com.roadpulse.app.i18n.EnglishStrings
import com.roadpulse.app.i18n.stringsFor
import com.roadpulse.app.nav.GuidanceLanguage
import com.roadpulse.app.nav.LimitDisplay
import com.roadpulse.app.nav.MapColors
import com.roadpulse.app.nav.RouteChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StringsTest {
    /** Every text property of an [AppStrings], by name (reflection over the interface's getters). */
    private fun texts(s: AppStrings): Map<String, String> =
        AppStrings::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == String::class.java && it.name.startsWith("get") }
            .associate { it.name.removePrefix("get") to it.invoke(s) as String }

    @Test
    fun everyArabicTextIsTranslated() {
        val en = texts(EnglishStrings)
        val ar = texts(ArabicStrings)
        assertTrue(en.size > 80)
        en.forEach { (name, text) ->
            assertTrue("$name is empty", ar.getValue(name).isNotBlank())
            assertNotEquals("$name is not translated", text, ar.getValue(name))
        }
    }

    @Test
    fun everyLabelHasArabic() {
        PlaceCategory.entries.forEach { assertNotEquals(it.label, ArabicStrings.category(it)) }
        ReportType.entries.forEach { assertNotEquals(it.label, ArabicStrings.reportType(it)) }
        Vehicle.entries.forEach { assertNotEquals(it.label, ArabicStrings.vehicle(it)) }
        FlagReason.entries.forEach { assertNotEquals(it.label, ArabicStrings.flagReason(it)) }
        MapColors.entries.forEach { assertNotEquals(it.label, ArabicStrings.mapColors(it)) }
        LimitDisplay.entries.forEach { assertNotEquals(it.label, ArabicStrings.limitDisplay(it)) }
        ReportDetails.subtypes.forEach { (type, list) -> list.forEach { (code, _) -> assertNotNull(code, ArabicStrings.reportDetail(type, code)) } }
        ReportDetails.closureDurations.forEach { (minutes, label) ->
            assertEquals(label, EnglishStrings.closureDuration(minutes))
            assertNotEquals(label, ArabicStrings.closureDuration(minutes))
        }
    }

    @Test
    fun englishMatchesTheOldWording() {
        assertEquals("1.5 km", EnglishStrings.distance(1.5))
        assertEquals("350 m", EnglishStrings.distance(0.35))
        assertEquals("1 h 5 min", EnglishStrings.duration(65))
        assertEquals("Visible", EnglishStrings.reportDetail(ReportType.POLICE, "VISIBLE"))
        assertEquals("350 م", ArabicStrings.distance(0.35))
        assertEquals("19 د", ArabicStrings.duration(19))
    }

    @Test
    fun languageChoiceSelectsScreens() {
        assertSame(ArabicStrings, stringsFor(GuidanceLanguage.AR))
        assertSame(EnglishStrings, stringsFor(GuidanceLanguage.MS))
        assertTrue(ArabicStrings.rtl)
        val r = com.roadpulse.app.data.Route(emptyList(), emptyList(), 1.0, 60.0)
        assertEquals("الأسرع", RouteChoices.describe(listOf(r), ArabicStrings)[0].tag)
    }

    /** Arabic screen texts next to the English, for review (docs/ARABIC_SCREENS.md is made from it). */
    @Test
    fun writeArabicScreensSheet() {
        val en = texts(EnglishStrings)
        val ar = texts(ArabicStrings)
        val rows = en.keys.sorted().filter { it != "Rtl" }.map { listOf(it, en.getValue(it), ar.getValue(it)) }.toMutableList()
        PlaceCategory.entries.forEach { rows += listOf("category $it", EnglishStrings.category(it), ArabicStrings.category(it)) }
        ReportType.entries.forEach { rows += listOf("report $it", EnglishStrings.reportType(it), ArabicStrings.reportType(it)) }
        ReportDetails.subtypes.forEach { (t, l) -> l.forEach { (c, e) -> rows += listOf("detail $c", e, ArabicStrings.reportDetail(t, c)!!) } }
        Vehicle.entries.forEach { rows += listOf("vehicle $it", EnglishStrings.vehicle(it), ArabicStrings.vehicle(it)) }
        FlagReason.entries.forEach { rows += listOf("flag $it", EnglishStrings.flagReason(it), ArabicStrings.flagReason(it)) }
        MapColors.entries.forEach { rows += listOf("map colours $it", EnglishStrings.mapColors(it), ArabicStrings.mapColors(it)) }
        LimitDisplay.entries.forEach { rows += listOf("limit $it", EnglishStrings.limitDisplay(it), ArabicStrings.limitDisplay(it)) }
        listOf<Pair<String, (AppStrings) -> String>>(
            "parked" to { it.yourCarParked(it.minutesAgo(25)) },
            "stop" to { it.stop(1, "KLCC") },
            "car parks" to { it.carParksNearDestination(3) },
            "toll" to { it.tollAbout("RM 6.80") },
            "toll partial" to { it.tollAtLeast("RM 5.00") },
            "summary" to { com.roadpulse.app.data.RouteOptions(Vehicle.MOTORCYCLE, avoidTolls = true, avoidHighways = true).summary(it)!! },
            "reported" to { it.reported(ReportType.POLICE) },
            "nearby" to { it.categoryNearby(PlaceCategory.FUEL) },
            "none within" to { it.noneWithin10Km(PlaceCategory.FUEL) },
            "closure 3h" to { it.closureDuration(180) },
            "notification" to { it.inMeters(350) + it.navigatingTo("KLCC") },
        ).forEach { (k, f) -> rows += listOf(k, f(EnglishStrings), f(ArabicStrings)) }
        File("build/arabic_screens.tsv").writeText(rows.joinToString("\n") { it.joinToString("\t") })
    }
}

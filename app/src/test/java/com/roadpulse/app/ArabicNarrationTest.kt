package com.roadpulse.app

import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.ReportType
import com.roadpulse.app.nav.ArabicNarrator
import com.roadpulse.app.nav.GuidanceLanguage
import com.roadpulse.app.nav.narratorFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ArabicNarrationTest {
    private val right = Maneuver("Turn right onto Jalan Ampang.", 0.5, 30.0, 0, type = 10, streetNames = listOf("Jalan Ampang"))
    private val left = Maneuver("Turn left.", 0.2, 10.0, 0, type = 15)

    @Test
    fun turnsAndWarnings() {
        assertSame(ArabicNarrator, narratorFor(GuidanceLanguage.AR))
        assertEquals("انعطف يمينًا إلى Jalan Ampang.", ArabicNarrator.atTurn(right))
        assertEquals("بعد 450 مترًا، انعطف يمينًا إلى Jalan Ampang.", ArabicNarrator.alert(right, 450))
        assertEquals("انعطف يمينًا إلى Jalan Ampang، ثم انعطف يسارًا.", ArabicNarrator.atTurnThen(right, left))
        assertEquals("شرطة على بعد 700 متر أمامك.", ArabicNarrator.reportAhead(ReportType.POLICE, 700))
    }

    @Test
    fun meterWordAgreesWithTheNumber() {
        assertEquals("متر واحد", ArabicNarrator.meters(1))
        assertEquals("متران", ArabicNarrator.meters(2))
        assertEquals("5 أمتار", ArabicNarrator.meters(5))
        assertEquals("50 مترًا", ArabicNarrator.meters(50))
        assertEquals("200 متر", ArabicNarrator.meters(200))
        assertEquals("1000 متر", ArabicNarrator.meters(1000))
        assertEquals("103 أمتار", ArabicNarrator.meters(103))
    }

    @Test
    fun unknownManeuversFallBackToEnglish() {
        val odd = Maneuver("Do something unusual.", 0.1, 5.0, 0, type = 99)
        assertEquals("Do something unusual.", ArabicNarrator.banner(odd))
    }
}

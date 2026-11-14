package com.roadpulse.app

import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.nav.EnglishNarrator
import com.roadpulse.app.nav.MalayNarrator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Malay instructions generated from real routing-server responses (© OpenStreetMap contributors). */
class NarrationTest {
    private fun load(name: String) =
        RoutingClient("http://unused").parseRoute(javaClass.classLoader!!.getResource(name)!!.readText())

    private val putrajaya by lazy { load("route_putrajaya.json") }
    private val motorway by lazy { load("route_kl_seremban.json") }

    @Test
    fun parsesTurnDetails() {
        val roundabout = putrajaya.maneuvers.first { it.type == 26 }
        assertEquals(2, roundabout.roundaboutExitCount)
        val exit608 = motorway.maneuvers.first { it.exitNumber == "608" }
        assertEquals(21, exit608.type)
        assertTrue(exit608.exitToward.isNotEmpty())
        assertTrue(motorway.maneuvers.all { m -> m.exitToward.none { it.startsWith(" ") } })
    }

    @Test
    fun everyManeuverOnRealRoutesHasMalayText() {
        for (m in putrajaya.maneuvers + motorway.maneuvers) {
            assertNotNull("no Malay for type ${m.type}: ${m.instruction}", MalayNarrator.core(m))
            val text = MalayNarrator.atTurn(m)
            listOf("Turn", "Keep", "Take", "Drive", "Continue", "destination is").forEach { english ->
                assertFalse("English left in: $text", text.contains(english))
            }
        }
    }

    @Test
    fun malayWordingForTypicalManeuvers() {
        val p = putrajaya.maneuvers
        assertEquals("Belok kanan ke Lebuh Bestari.", MalayNarrator.atTurn(p.first { it.streetNames == listOf("Lebuh Bestari") }))
        assertEquals(
            "Masuk ke bulatan dan ambil jalan keluar kedua ke Jalan P18 C1.",
            MalayNarrator.atTurn(p.first { it.type == 26 }),
        )
        assertEquals("Destinasi anda di sebelah kanan.", MalayNarrator.atTurn(p.last()))
        assertEquals("Pandu ke arah tenggara.", MalayNarrator.atTurn(p.first()))
        assertEquals(
            "Dalam 300 meter, belok kiri ke Lebuh Perdana Selatan.",
            MalayNarrator.alert(p.first { it.streetNames == listOf("Lebuh Perdana Selatan") }, 300),
        )
        val exit = motorway.maneuvers.first { it.exitNumber == "608" }
        val text = MalayNarrator.atTurn(exit)
        assertTrue(text, text.startsWith("Ambil susur keluar 608 di sebelah kiri"))
        assertTrue(text, text.contains("menuju"))
    }

    @Test
    fun unknownTypeFallsBackToEnglish() {
        val m = Maneuver("Take the elevator.", 0.0, 0.0, 0, type = 39)
        assertEquals("Take the elevator.", MalayNarrator.atTurn(m))
        assertEquals("In 200 meters, take the elevator.", MalayNarrator.alert(m, 200))
    }

    @Test
    fun englishKeepsServerWording() {
        val m = Maneuver("Turn right onto Jalan A.", 0.0, 0.0, 0, verbalAlert = "Turn right onto Jalan A.", verbalPre = "Turn right onto Jalan A now.")
        assertEquals("Turn right onto Jalan A now.", EnglishNarrator.atTurn(m))
        assertEquals("In 450 meters, turn right onto Jalan A.", EnglishNarrator.alert(m, 450))
    }

    @Test
    fun compassAndOrdinals() {
        assertEquals("utara", MalayNarrator.compass(5))
        assertEquals("timur", MalayNarrator.compass(87))
        assertEquals("barat laut", MalayNarrator.compass(320))
        assertEquals("utara", MalayNarrator.compass(355))
        assertEquals("ketiga", MalayNarrator.ordinal(3))
        assertEquals("ke-12", MalayNarrator.ordinal(12))
    }
}

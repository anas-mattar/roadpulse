package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.Route
import com.roadpulse.app.nav.EnglishNarrator
import com.roadpulse.app.nav.MalayNarrator
import com.roadpulse.app.nav.Narrator
import com.roadpulse.app.nav.RouteTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergedPromptsTest {
    // Vertices ~55.6 m apart northbound. Right turn at vertex 20 (~1.1 km), left turn 2 vertices
    // (~111 m) later, then a third turn far away at vertex 35.
    private val shape = (0..40).map { GeoPoint(3.0 + it * 0.0005, 101.0) }
    private val right = Maneuver("Turn right onto Jalan Ampang.", 0.1, 10.0, 20, type = 10, streetNames = listOf("Jalan Ampang"))
    private val left = Maneuver("Turn left onto Jalan Pinang.", 0.8, 50.0, 22, type = 15, streetNames = listOf("Jalan Pinang"))
    private val route = Route(
        shape,
        listOf(
            Maneuver("Drive north.", 1.1, 60.0, 0, type = 1),
            right,
            left,
            Maneuver("Turn right onto Jalan Raja Chulan.", 0.3, 20.0, 35, type = 10, streetNames = listOf("Jalan Raja Chulan")),
            Maneuver("You have arrived.", 0.0, 0.0, 40, type = 4),
        ),
        2.2, 140.0,
    )

    private fun drive(narrator: Narrator): List<String> {
        val tracker = RouteTracker(route, narrator = { narrator })
        return shape.indices.mapNotNull { tracker.update(shape[it]).prompt }
    }

    @Test
    fun closeTurnsAreAnnouncedTogetherInEnglish() {
        val prompts = drive(EnglishNarrator)
        assertTrue(prompts.toString(), prompts.contains("In 450 meters, turn right onto Jalan Ampang, then turn left onto Jalan Pinang."))
        assertTrue(prompts.toString(), prompts.contains("Turn right onto Jalan Ampang, then turn left onto Jalan Pinang."))
        // No separate advance warning for the left turn, but it is still announced at the turn.
        assertTrue(prompts.none { it.startsWith("In") && it.contains("turn left onto Jalan Pinang") && !it.contains("Ampang") })
        assertTrue(prompts.contains("Turn left onto Jalan Pinang."))
        // A turn far from the next one is announced on its own.
        assertTrue(prompts.contains("Turn right onto Jalan Raja Chulan."))
    }

    @Test
    fun closeTurnsAreAnnouncedTogetherInMalay() {
        val prompts = drive(MalayNarrator)
        assertTrue(prompts.toString(), prompts.contains("Dalam 450 meter, belok kanan ke Jalan Ampang, kemudian belok kiri ke Jalan Pinang."))
        assertTrue(prompts.toString(), prompts.contains("Belok kanan ke Jalan Ampang, kemudian belok kiri ke Jalan Pinang."))
    }

    @Test
    fun serverChainedEnglishIsNotChainedTwice() {
        val chained = right.copy(verbalPre = "Turn right onto Jalan Ampang. Then Turn left onto Jalan Pinang.")
        assertEquals("Turn right onto Jalan Ampang. Then Turn left onto Jalan Pinang.", EnglishNarrator.atTurnThen(chained, left))
    }
}

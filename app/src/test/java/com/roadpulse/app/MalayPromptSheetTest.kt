package com.roadpulse.app

import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.nav.EnglishNarrator
import com.roadpulse.app.nav.MalayNarrator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Every Malay phrase the app can say, next to the English, for native-speaker review. Writes
 * app/build/malay_prompts.tsv (one row per phrase) and checks that every row has Malay wording.
 */
class MalayPromptSheetTest {
    private fun m(type: Int, en: String, street: String? = null, bearing: Int? = null, exits: Int? = null,
                  exitNumber: String? = null, branch: String? = null, toward: List<String> = emptyList()) =
        Maneuver(en, 0.5, 30.0, 0, type = type, streetNames = listOfNotNull(street), bearingAfter = bearing,
            roundaboutExitCount = exits, exitNumber = exitNumber, exitBranch = listOfNotNull(branch), exitToward = toward)

    private val examples = listOf(
        "Start" to m(1, "Drive southeast.", bearing = 135),
        "Start" to m(1, "Drive east on Jalan Damansara.", "Jalan Damansara", bearing = 90),
        "Arrive" to m(4, "You have arrived at your destination."),
        "Arrive" to m(5, "Your destination is on the right."),
        "Arrive" to m(6, "Your destination is on the left."),
        "Continue" to m(7, "Jalan Duta becomes Jalan Ampang.", "Jalan Ampang"),
        "Continue" to m(8, "Continue on E6.", "E6"),
        "Turn" to m(9, "Bear right onto Jalan Tun Razak.", "Jalan Tun Razak"),
        "Turn" to m(10, "Turn right onto Jalan Ampang.", "Jalan Ampang"),
        "Turn" to m(10, "Turn right."),
        "Turn" to m(11, "Make a sharp right onto Jalan Pinang.", "Jalan Pinang"),
        "U-turn" to m(12, "Make a right U-turn to stay on Jalan P18 C.", "Jalan P18 C"),
        "Turn" to m(14, "Make a sharp left onto Jalan Raja Chulan.", "Jalan Raja Chulan"),
        "Turn" to m(15, "Turn left onto Jalan P18 C.", "Jalan P18 C"),
        "Turn" to m(16, "Bear left onto Jalan Sultan Ismail.", "Jalan Sultan Ismail"),
        "Ramp" to m(17, "Take the ramp straight ahead toward Putrajaya.", toward = listOf("Putrajaya")),
        "Ramp" to m(18, "Take the ramp on the right toward E2.", branch = "E2"),
        "Ramp" to m(19, "Take the E37 ramp on the left toward Putrajaya, Cyberjaya.", branch = "E37", toward = listOf("Putrajaya", "Cyberjaya")),
        "Exit" to m(20, "Take exit 608 on the right toward Sepang.", exitNumber = "608", toward = listOf("Sepang")),
        "Exit" to m(21, "Take exit 608 onto E6 toward Kuala Lumpur International Airport, Sepang.", exitNumber = "608", branch = "E6",
            toward = listOf("Kuala Lumpur International Airport", "Sepang")),
        "Keep" to m(22, "Keep straight to take E2.", branch = "E2"),
        "Keep" to m(23, "Keep right to take E37 toward E2, E26.", branch = "E37", toward = listOf("E2", "E26")),
        "Keep" to m(24, "Keep left at the fork."),
        "Keep" to m(24, "Keep left toward Kuantan, Hulu Kelang.", toward = listOf("Kuantan", "Hulu Kelang")),
        "Merge" to m(25, "Merge onto E6.", "E6"),
        "Merge" to m(25, "Merge."),
        "Roundabout" to m(26, "Enter the roundabout and take the 2nd exit onto Jalan P18 C1.", "Jalan P18 C1", exits = 2),
        "Roundabout" to m(26, "Enter the roundabout and take the 3rd exit.", exits = 3),
        "Roundabout" to m(27, "Exit the roundabout onto Jalan P18 C1.", "Jalan P18 C1"),
        "Ferry" to m(28, "Take the ferry."),
        "Ferry" to m(29, "Leave the ferry onto Jalan Pelabuhan.", "Jalan Pelabuhan"),
    )

    @Test
    fun writeReviewSheet() {
        val en = EnglishNarrator
        val ms = MalayNarrator
        val rows = mutableListOf(listOf("Group", "When it is used", "English", "Bahasa Melayu (current)"))
        examples.forEach { (group, man) -> rows += listOf(group, "Spoken at the turn and shown in the banner", man.instruction, ms.atTurn(man)) }
        val right = m(10, "Turn right onto Jalan Ampang.", "Jalan Ampang")
        val left = m(15, "Turn left onto Jalan Pinang.", "Jalan Pinang")
        rows += listOf("Warning", "Spoken before a turn", en.alert(right, 450), ms.alert(right, 450))
        rows += listOf("Two turns", "Two turns less than 150 m apart, at the turn", en.atTurnThen(right, left), ms.atTurnThen(right, left))
        rows += listOf("Two turns", "Two turns less than 150 m apart, before the turn", en.alertThen(right, 300, left), ms.alertThen(right, 300, left))
        listOf(
            Triple("Status", "Spoken on arrival", en.arrived to ms.arrived),
            Triple("Status", "Spoken when finding a new route", en.rerouting to ms.rerouting),
            Triple("Status", "Spoken when GPS is lost", en.gpsLost to ms.gpsLost),
            Triple("Status", "Spoken when there is no internet", en.noInternet to ms.noInternet),
            Triple("Status", "Spoken when the app reopens during a drive", en.resuming("M City") to ms.resuming("M City")),
            Triple("Screen", "Distance in the banner", en.inDistance("421 m") to ms.inDistance("421 m")),
            Triple("Screen", "Banner when there is no turn soon", en.followRoute to ms.followRoute),
            Triple("Screen", "Label for the turn after next", en.thenLabel to ms.thenLabel),
            Triple("Screen", "Button that brings the map back to the car", en.recenter to ms.recenter),
            Triple("Screen", "Status line: rerouting", en.statusFindingRoute to ms.statusFindingRoute),
            Triple("Screen", "Status line: offline", en.statusOffline to ms.statusOffline),
            Triple("Screen", "Status line: GPS lost", en.statusGpsLost to ms.statusGpsLost),
            Triple("Screen", "Status line: weak GPS", en.statusWeakGps to ms.statusWeakGps),
        ).forEach { (g, w, p) -> rows += listOf(g, w, p.first, p.second) }
        com.roadpulse.app.data.ReportType.entries.forEach { t ->
            rows += listOf("Reports", "Warning for a report on the route ahead", en.reportAhead(t, 700), ms.reportAhead(t, 700))
        }
        (1..4).forEach { rows += listOf("Words", "Roundabout exit number", "exit $it", ms.ordinal(it)) }
        listOf(0, 45, 90, 135, 180, 225, 270, 315).forEach { rows += listOf("Words", "Compass direction at the start", "$it°", ms.compass(it)) }

        rows.drop(1).forEach { assertEquals("Malay missing for ${it[2]}", false, it[3].isBlank() || it[3] == it[2]) }
        File("build/malay_prompts.tsv").writeText(rows.joinToString("\n") { it.joinToString("\t") })
    }
}

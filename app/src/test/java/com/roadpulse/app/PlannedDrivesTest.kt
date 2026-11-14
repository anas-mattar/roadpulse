package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.PlacesStore
import com.roadpulse.app.data.PlannedDrive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlannedDrivesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val office = Place("Menara KL", "Kuala Lumpur", GeoPoint(3.15, 101.70))
    private val min = 60_000L

    @Test
    fun leaveTimeIsTravelTimePlusSpare() {
        val arrive = 1_000 * min
        val d = PlannedDrive(1, office, arrive, driveSeconds = 25 * 60.0)
        assertEquals(arrive - 35 * min, d.leaveAtMillis)   // 25 min drive + 10 min spare
        assertEquals(arrive - 40 * min, d.remindAtMillis)  // reminder 5 min before leaving
        assertEquals(25, d.driveMinutes)
    }

    @Test
    fun storeKeepsUpcomingDrivesInOrder() {
        val store = PlacesStore(File(tmp.root, "places.json"))
        val now = 10_000 * min
        store.addPlanned(PlannedDrive(1, office, now + 120 * min, 600.0), now)
        store.addPlanned(PlannedDrive(2, office.copy(name = "KLCC"), now + 60 * min, 600.0), now)
        assertEquals(listOf(2L, 1L), store.load().planned.map { it.id })
        // A drive that ended over an hour ago is dropped when the next one is added.
        store.addPlanned(PlannedDrive(3, office, now + 30 * min, 600.0), now + 200 * min)
        assertEquals(listOf(3L), store.load().planned.map { it.id })
        assertEquals(emptyList<Long>(), store.removePlanned(3).planned.map { it.id })
    }
}

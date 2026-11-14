package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.PlacesStore
import com.roadpulse.app.data.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PlacesStoreTest {
    private fun store(max: Int = 8) = PlacesStore(File.createTempFile("places", ".json").apply { delete() }, max)
    private fun place(name: String, lat: Double = 3.1, lon: Double = 101.6) = Place(name, "KL", GeoPoint(lat, lon))

    @Test
    fun homeAndWorkPersist() {
        val file = File.createTempFile("places", ".json").apply { delete() }
        PlacesStore(file).setShortcut(Shortcut.HOME, place("Home street"))
        PlacesStore(file).setShortcut(Shortcut.WORK, place("Office tower"))
        val reloaded = PlacesStore(file).load()
        assertEquals("Home street", reloaded.home?.name)
        assertEquals("Office tower", reloaded.work?.name)
    }

    @Test
    fun recentIsNewestFirstWithoutDuplicatesAndCapped() {
        val s = store(max = 3)
        s.addRecent(place("A", 3.10))
        s.addRecent(place("B", 3.20))
        s.addRecent(place("a", 3.10001)) // same place as A (case and ~1 m apart): moves to front
        s.addRecent(place("C", 3.30))
        s.addRecent(place("D", 3.40))
        assertEquals(listOf("D", "C", "a"), s.load().recent.map { it.name })
    }

    @Test
    fun samePlaceNeedsSameNameAndProximity() {
        assertFalse(PlacesStore.samePlace(place("M City", 3.1579, 101.7116), place("M City", 3.2, 101.7116)))
    }

    @Test
    fun clearAndCorruptFileGiveEmpty() {
        val file = File.createTempFile("places", ".json")
        file.writeText("{ broken")
        val s = PlacesStore(file)
        assertNull(s.load().home)
        s.setShortcut(Shortcut.HOME, place("X"))
        assertEquals("X", s.load().home?.name)
        s.clear()
        assertNull(s.load().home)
        assertFalse(file.exists())
    }
}

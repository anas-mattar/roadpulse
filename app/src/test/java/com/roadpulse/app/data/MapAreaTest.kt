package com.roadpulse.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapAreaTest {
    private val city = MapArea(101.60, 3.10, 101.70, 3.20) // about 11 km square, Kuala Lumpur

    @Test
    fun subscriptionPadsTheVisibleArea() {
        val sub = MapArea.subscriptionFor(city)
        assertEquals(101.55, sub.minLon, 1e-9)
        assertEquals(3.25, sub.maxLat, 1e-9)
        assertTrue(sub.contains(city))
    }

    @Test
    fun smallPansKeepTheSubscriptionLargeOnesReplaceIt() {
        val sub = MapArea.subscriptionFor(city)
        assertFalse(MapArea.needsNewSubscription(sub, city.shifted(0.03)))
        assertTrue(MapArea.needsNewSubscription(sub, city.shifted(0.2)))
        assertTrue(MapArea.needsNewSubscription(null, city))
    }

    @Test
    fun zoomedOutMapsAreClampedToTheServerLimit() {
        val country = MapArea(99.0, 1.0, 105.0, 7.0)
        val sub = MapArea.subscriptionFor(country)
        assertTrue(sub.width <= MapArea.MAX_DEGREES + 1e-9 && sub.height <= MapArea.MAX_DEGREES + 1e-9)
        assertEquals(102.0, (sub.minLon + sub.maxLon) / 2, 1e-9) // same centre
        // Still zoomed out at the same place: no churn.
        assertFalse(MapArea.needsNewSubscription(sub, country))
    }

    @Test
    fun bboxUsesDotsWhateverTheLocale() {
        val old = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("101.600000,3.100000,101.700000,3.200000", city.bboxParam())
        } finally {
            java.util.Locale.setDefault(old)
        }
    }

    private fun MapArea.shifted(d: Double) = MapArea(minLon + d, minLat, maxLon + d, maxLat)
}

package com.roadpulse.app

import com.roadpulse.app.data.Geo
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Polyline
import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineTest {
    @Test
    fun decodesReferencePolylineAtPrecision5() {
        // Reference example from the encoded polyline algorithm documentation.
        val points = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].lat, 1e-6)
        assertEquals(-120.2, points[0].lon, 1e-6)
        assertEquals(40.7, points[1].lat, 1e-6)
        assertEquals(-120.95, points[1].lon, 1e-6)
        assertEquals(43.252, points[2].lat, 1e-6)
        assertEquals(-126.453, points[2].lon, 1e-6)
    }

    @Test
    fun nearestIndexPicksClosestVertex() {
        val shape = listOf(GeoPoint(3.10, 101.60), GeoPoint(3.14, 101.69), GeoPoint(3.20, 101.75))
        assertEquals(1, Geo.nearestIndex(shape, GeoPoint(3.139, 101.687)))
    }
}

package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.RoutingClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutingRequestTest {
    @Test
    fun requestBodyIncludesCostingAndUnits() {
        val body = RoutingClient("http://unused").requestBody(GeoPoint(3.139, 101.6869), GeoPoint(3.1579, 101.7116))
        // Regression: defaults were silently dropped, and Valhalla answered "No edge/node costing provided".
        assertTrue(body, body.contains("\"costing\":\"auto\""))
        assertTrue(body, body.contains("\"units\":\"kilometers\""))
        System.getProperty("routingBodyOut")?.let { File(it).writeText(body) }
    }
}

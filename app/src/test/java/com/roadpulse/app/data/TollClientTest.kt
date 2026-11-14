package com.roadpulse.app.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TollClientTest {
    @Test
    fun sendsTheRouteAndVehicleClass() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(
            """{"currency":"MYR","vehicle":"CAR","totalRm":5.0,"complete":false,"ratesCheckedOn":"2026-11-15",
               "plazas":[{"name":"Bukit Kiara","highway":"SPRINT","status":"PRICED","priceRm":3.0,"lat":3.1,"lon":101.6},
                         {"name":"Jalan Duta","status":"CLOSED_SYSTEM","lat":3.1,"lon":101.7}]}"""
        ))
        server.start()
        val e = TollClient(server.url("").toString().trimEnd('/'))
            .estimate(listOf(GeoPoint(3.1, 101.5), GeoPoint(3.1, 101.7)), Vehicle.TAXI)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("CAR", body["vehicle"]!!.jsonPrimitive.content) // taxis pay the car price
        assertEquals(101.5, body["points"]!!.jsonArray[0].jsonArray[1].jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals(2, e.plazas.size)
        assertEquals("Toll RM 5.00 + ?", TollClient.label(true, e))
        server.shutdown()
    }

    @Test
    fun motorcyclesUseTheirOwnClass() {
        assertEquals("MOTORCYCLE", TollClient.tollClass(Vehicle.MOTORCYCLE))
        assertEquals("CAR", TollClient.tollClass(Vehicle.CAR))
    }

    @Test
    fun cardLabels() {
        val priced = TollPlaza("Bukit Kiara", status = "PRICED", priceRm = 3.0)
        assertNull(TollClient.label(false, null))
        assertNull(TollClient.label(false, TollEstimate()))
        assertEquals("Toll road", TollClient.label(true, null))
        assertEquals("Toll: price unknown", TollClient.label(true, TollEstimate()))
        assertEquals("Toll ~RM 6.80", TollClient.label(true, TollEstimate(totalRm = 6.8, complete = true, plazas = listOf(priced))))
        assertEquals("Toll: price unknown", TollClient.label(true, TollEstimate(totalRm = 0.0, complete = false, plazas = listOf(priced.copy(status = "NOT_IN_TABLE")))))
    }
}

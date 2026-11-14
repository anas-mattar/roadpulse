package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.ParkedCar
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.PlacesStore
import com.roadpulse.app.data.Polyline
import com.roadpulse.app.data.Report
import com.roadpulse.app.data.ReportType
import com.roadpulse.app.data.RouteOptions
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.data.Vehicle
import com.roadpulse.app.nav.EnglishNarrator
import com.roadpulse.app.nav.MalayNarrator
import com.roadpulse.app.nav.PassedReports
import com.roadpulse.app.nav.ReportAhead
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QuickFeaturesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val client = RoutingClient("http://localhost")

    @Test
    fun routeOptionsBecomeValhallaCostingOptions() {
        val body = Json.parseToJsonElement(
            client.requestBody(
                GeoPoint(3.1, 101.6), GeoPoint(3.2, 101.7),
                stops = listOf(GeoPoint(3.15, 101.65)),
                options = RouteOptions(vehicle = Vehicle.MOTORCYCLE, avoidTolls = true, avoidFerries = true),
                alternates = 2,
            )
        ).jsonObject
        assertEquals("motorcycle", body["costing"]!!.jsonPrimitive.content)
        assertEquals(3, body["locations"]!!.jsonArray.size) // start, stop, destination
        assertEquals(3.15, body["locations"]!!.jsonArray[1].jsonObject["lat"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        val moto = body["costing_options"]!!.jsonObject["motorcycle"]!!.jsonObject
        assertEquals("0.0", moto["use_tolls"]!!.jsonPrimitive.content)
        assertEquals("0.0", moto["use_ferry"]!!.jsonPrimitive.content)
        assertNull(moto["use_highways"])
        assertNull(body["alternates"]) // the server offers no alternatives with stops

        val car = Json.parseToJsonElement(client.requestBody(GeoPoint(3.1, 101.6), GeoPoint(3.2, 101.7))).jsonObject
        assertEquals("auto", car["costing"]!!.jsonPrimitive.content)
        assertEquals("true", car["costing_options"]!!.jsonObject["auto"]!!.jsonObject["exclude_unpaved"]!!.jsonPrimitive.content)
    }

    @Test
    fun optionSummaries() {
        assertNull(RouteOptions().summary())
        assertEquals("Motorcycle · avoiding tolls, highways", RouteOptions(Vehicle.MOTORCYCLE, avoidTolls = true, avoidHighways = true).summary())
    }

    @Test
    fun legsAreJoinedAndTheStopIsAnnouncedAsAStop() {
        val leg1 = listOf(GeoPoint(3.10, 101.60), GeoPoint(3.11, 101.60), GeoPoint(3.12, 101.60))
        val leg2 = listOf(GeoPoint(3.12, 101.60), GeoPoint(3.12, 101.61))
        fun m(type: Int, idx: Int, text: String) = """{"type":$type,"instruction":"$text","begin_shape_index":$idx,"length":1.0,"time":60}"""
        val body = """{"trip":{"summary":{"length":3.3,"time":300},"legs":[
            {"shape":"${Polyline.encode(leg1)}","maneuvers":[${m(1, 0, "Drive north.")},${m(4, 2, "You have arrived at your destination.")}]},
            {"shape":"${Polyline.encode(leg2)}","maneuvers":[${m(1, 0, "Drive east.")},${m(4, 1, "You have arrived at your destination.")}]}
        ]}}"""
        val stop = GeoPoint(3.12, 101.60)
        val route = client.parseRoute(body, listOf(stop))
        assertEquals(4, route.shape.size) // the shared point is not repeated
        assertEquals(listOf(1, RoutingClient.STOP_REACHED, 1, 4), route.maneuvers.map { it.type })
        assertEquals(listOf(0, 2, 2, 3), route.maneuvers.map { it.beginShapeIndex })
        assertEquals(listOf(2), route.stopShapeIndexes)
        assertEquals(listOf(stop), route.stops)
        val atStop = route.maneuvers[1]
        assertEquals("You have reached your stop.", EnglishNarrator.atTurn(atStop))
        assertEquals("In 300 meters, arrive at your stop.", EnglishNarrator.alert(atStop, 300))
        assertEquals("Dalam 300 meter, tiba di hentian anda.", MalayNarrator.alert(atStop, 300))
    }

    @Test
    fun favouritesAndParkingAreSavedOnThePhone() {
        val store = PlacesStore(tmp.newFile("places.json"))
        val gym = Place("Dropped pin", "3.1, 101.6", GeoPoint(3.1, 101.6))
        store.addFavourite("Gym", gym)
        store.addFavourite("gym", gym.copy(location = GeoPoint(3.2, 101.7))) // same name replaces
        assertEquals(listOf("gym"), store.load().favourites.map { it.name })
        store.setParking(ParkedCar(GeoPoint(3.15, 101.65), 1_000L))
        assertEquals(1_000L, store.load().parking?.savedAtMillis)
        store.removeFavourite(store.load().favourites.first())
        assertTrue(store.load().favourites.isEmpty())
        store.setParking(null)
        assertNull(store.load().parking)
    }

    @Test
    fun aReportIsPassedOnceAfterTheCarGetsClose() {
        val p = PassedReports(nearM = 80.0)
        val police = Report("p", ReportType.POLICE, GeoPoint(3.0, 101.6), 0)
        assertTrue(p.update(listOf(ReportAhead(police, 300.0))).isEmpty())
        assertTrue(p.update(listOf(ReportAhead(police, 40.0))).isEmpty())
        assertEquals(listOf("p"), p.update(emptyList())) // now behind the car
        p.update(listOf(ReportAhead(police, 40.0)))
        assertTrue(p.update(emptyList()).isEmpty()) // asked once only

        val crash = Report("c", ReportType.CRASH, GeoPoint(3.0, 101.7), 0)
        p.update(listOf(ReportAhead(crash, 50.0)))
        p.newRoute() // rerouted away from it
        assertTrue(p.update(emptyList()).isEmpty())
    }
}

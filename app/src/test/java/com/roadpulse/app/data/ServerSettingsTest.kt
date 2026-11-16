package com.roadpulse.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerSettingsTest {
    @Test
    fun localUrlsFollowTheChosenLink() {
        val usb = ServerSettings.LaptopLink.USB
        assertEquals("http://127.0.0.1:8080", ServerSettings.forLink("http://10.0.2.2:8080", usb))
        assertEquals("http://127.0.0.1:8090/styles/liberty/style.json",
            ServerSettings.forLink("http://10.0.2.2:8090/styles/liberty/style.json", usb))
        assertEquals("http://10.0.2.2:8002", ServerSettings.forLink("http://10.0.2.2:8002", ServerSettings.LaptopLink.EMULATOR))
        assertEquals("", ServerSettings.forLink("", usb))
    }

    @Test
    fun foundServerIsUsedWhenNothingElseIsSet() {
        val found = "https://demo.example"
        // Laptop link on (debug): the laptop wins.
        assertEquals("http://10.0.2.2:8080", ServerSettings.chooseBackend(true, "http://10.0.2.2:8080", "", found))
        // A server fixed in the build wins over the found one, and keeps its own routing server.
        assertEquals("https://api.example", ServerSettings.chooseBackend(false, "", "https://api.example", found))
        assertEquals("https://routing.example", ServerSettings.chooseRouting(false, "", "https://api.example", "https://routing.example", found))
        // Otherwise the found server, with routing under /routing.
        assertEquals(found, ServerSettings.chooseBackend(false, "", "", found))
        assertEquals("$found/routing", ServerSettings.chooseRouting(false, "", "", "https://public.example", found))
        // Nothing found: public routing, no backend.
        assertEquals("", ServerSettings.chooseBackend(false, "", "", ""))
        assertEquals("https://public.example", ServerSettings.chooseRouting(false, "", "", "https://public.example", ""))
    }
}

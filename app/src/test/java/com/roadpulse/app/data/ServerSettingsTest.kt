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
}

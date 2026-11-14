package com.roadpulse.app.data

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalRTest {
    private val rs = "\u001e"

    @Test
    fun splitsSeveralMessagesInOneFrame() {
        val frames = SignalR.frames("""{}$rs{"type":6}$rs""")
        assertEquals(listOf("{}", """{"type":6}"""), frames)
    }

    @Test
    fun parsesAReportUpsert() {
        val json = """{"type":1,"target":"ReportUpserted","arguments":[{"id":"2bb592dd-4c27-4330-9e2c-451870ea5771",""" +
            """"type":"HAZARD","lat":3.139,"lon":101.6869,"headingDeg":null,"createdAt":"2026-11-14T13:00:12.772446+00:00",""" +
            """"expiresAt":"2026-11-14T14:30:12.772446+00:00","confirmations":0,"dismissals":0}]}"""
        val m = SignalR.parse(json) as SignalR.Message.Invocation
        assertEquals("ReportUpserted", m.target)
        val report = Http.json.decodeFromJsonElement(ReportDto.serializer(), m.arguments[0]).toReport()
        assertEquals(ReportType.HAZARD, report.type)
        assertEquals(3.139, report.location.lat, 1e-9)
        assertEquals(90 * 60_000L, report.expiresAtMillis!! - report.createdAtMillis)
    }

    @Test
    fun parsesRemovalPingCompletionAndClose() {
        val removed = SignalR.parse("""{"type":1,"target":"ReportRemoved","arguments":["abc"]}""") as SignalR.Message.Invocation
        assertEquals("abc", removed.arguments[0].jsonPrimitive.content)
        assertEquals(SignalR.Message.Ping, SignalR.parse("""{"type":6}"""))
        val done = SignalR.parse("""{"type":3,"invocationId":"1","error":"Area too large"}""") as SignalR.Message.Completion
        assertEquals("Area too large", done.error)
        assertTrue(SignalR.parse("""{"type":7}""") is SignalR.Message.Close)
    }
}

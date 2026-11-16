package com.roadpulse.app.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerDirectoryTest {
    private val original = Http.transport

    @After
    fun restore() { Http.transport = original }

    /** A pretend internet: url -> body; anything else cannot be reached. */
    private fun internet(vararg pages: Pair<String, String>) {
        val map = pages.toMap()
        Http.transport = object : HttpTransport {
            override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpResult =
                map[url]?.let { HttpResult(200, it) } ?: throw IOException("unreachable $url")
        }
    }

    @Test
    fun addressBookKeepsOnlyTidyHttpsServers() {
        val text = """{"servers":["https://a.example/"," https://b.example ","http://plain.example","ftp://x","https://a.example"],"updated":"x"}"""
        assertEquals(listOf("https://a.example", "https://b.example"), ServerDirectory.parse(text))
        assertEquals(emptyList<String>(), ServerDirectory.parse("not json"))
        assertEquals(emptyList<String>(), ServerDirectory.parse("{}"))
    }

    @Test
    fun rememberedServerIsTriedFirst() {
        assertEquals(listOf("https://old.example", "https://new.example"),
            ServerDirectory.candidates("https://old.example/", listOf("https://new.example", "https://old.example")))
        assertEquals(listOf("https://new.example"), ServerDirectory.candidates("", listOf("https://new.example")))
    }

    @Test
    fun keepsTheRememberedServerWhileItAnswers() = runBlocking {
        internet("https://old.example/health" to """{"status":"ok"}""")
        assertEquals("https://old.example", ServerDirectory.find("https://old.example", listOf("https://book.example/s.json")))
    }

    @Test
    fun movesToThePublishedServerWhenTheOldOneIsGone() = runBlocking {
        internet(
            "https://book2.example/s.json" to """{"servers":["https://dead.example","https://new.example"]}""",
            "https://new.example/health" to "ok",
        )
        // The first address book is unreachable (blocked): the second one is used.
        val books = listOf("https://book1.example/s.json", "https://book2.example/s.json")
        assertEquals("https://new.example", ServerDirectory.find("https://old.example", books))
        assertEquals("https://new.example/routing", ServerDirectory.routingUrl("https://new.example"))
    }

    @Test
    fun noServerWhenNothingAnswers() = runBlocking {
        internet("https://book.example/s.json" to """{"servers":["https://dead.example"]}""")
        assertEquals("", ServerDirectory.find("", listOf("https://book.example/s.json")))
    }
}

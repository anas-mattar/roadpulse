package com.roadpulse.app.data

import kotlinx.serialization.Serializable

/**
 * Finds the Jalanna server without the driver typing an address.
 *
 * The app knows a few fixed places ("address books": small public JSON files) that list the
 * current server addresses, plus any servers built in here. It remembers the server that answered
 * last time, and looks again when that one stops answering. So the server can move (a new tunnel
 * address, a new host, a blocked domain) and installed apps follow without an update.
 *
 * An address book looks like: {"servers": ["https://one.example", "https://two.example"]}
 * Each server answers the API at its address and routing under /routing (backend/gateway).
 * Only https addresses are accepted. Whoever can edit an address book decides where the app
 * connects, so keep them in accounts only you control.
 */
object ServerDirectory {
    /** Servers the app ships with (tried after the remembered one). Add your fixed address here when you have one. */
    val builtInServers: List<String> = emptyList()

    /** Where the current server list is published. More than one, on unrelated hosts, survives one being blocked. */
    val addressBooks: List<String> = listOf(
        "https://raw.githubusercontent.com/anas-mattar/jalanna-config/main/servers.json",
    )

    @Serializable
    private data class AddressBook(val servers: List<String> = emptyList())

    /** The usable server addresses in an address book's text (https only, no trailing slash, no repeats). Pure: tested. */
    fun parse(text: String): List<String> =
        runCatching { Http.json.decodeFromString(AddressBook.serializer(), text).servers }.getOrDefault(emptyList())
            .mapNotNull(::clean).distinct()

    /** A tidy https address, or null when [address] is not one. */
    fun clean(address: String): String? {
        val a = address.trim().trimEnd('/')
        return if (a.startsWith("https://") && a.length > "https://".length && ' ' !in a) a else null
    }

    /** The order to try servers in: the remembered one, then the published ones, then the built-in ones. Pure: tested. */
    fun candidates(remembered: String, published: List<String>): List<String> =
        (listOfNotNull(clean(remembered)) + published + builtInServers.mapNotNull(::clean)).distinct()

    /** Routing lives under /routing on the same address. */
    fun routingUrl(server: String): String = "$server/routing"

    private suspend fun answers(server: String): Boolean =
        runCatching { Http.execute("$server/health").contains("ok") }.getOrDefault(false)

    private suspend fun published(books: List<String>): List<String> {
        for (book in books) {
            val found = runCatching { parse(Http.execute(book)) }.getOrDefault(emptyList())
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /**
     * The server to use, or "" when none answers. Fast when the [remembered] server still works
     * (one request); otherwise reads the address books and tries each listed server in order.
     */
    suspend fun find(remembered: String, books: List<String> = addressBooks): String {
        clean(remembered)?.let { if (answers(it)) return it }
        for (server in candidates("", published(books))) {
            if (server != clean(remembered) && answers(server)) return server
        }
        return ""
    }
}

package com.roadpulse.app.ios

import com.roadpulse.app.data.ServerDirectory
import kotlin.coroutines.cancellation.CancellationException

/** For the iPhone app: finds the server from the address books (see [ServerDirectory]). */
class ServerFinder {
    /** The server that answers, or "" when none does. [remembered] is the one used last time ("" for none). */
    @Throws(Exception::class, CancellationException::class)
    suspend fun find(remembered: String): String = ServerDirectory.find(remembered)

    fun routingUrl(server: String): String = ServerDirectory.routingUrl(server)
}

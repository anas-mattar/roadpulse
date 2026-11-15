package com.roadpulse.app.nav

import android.content.Context
import com.roadpulse.app.data.ServerSettings
import com.roadpulse.app.data.ShareLink

/** Android: share the current drive through the server chosen in Settings ([ShareSession] is shared). */
suspend fun ShareSession.start(context: Context, name: String?): ShareLink {
    val url = ServerSettings.backendUrl()
    return start(url, ServerSettings.tokenStore(context, url), name)
}

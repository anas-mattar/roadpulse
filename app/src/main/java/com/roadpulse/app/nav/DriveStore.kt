package com.roadpulse.app.nav

import com.roadpulse.app.data.Place
import com.roadpulse.app.data.Route
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Keeps the active drive on disk so navigation can continue after Android stops the app.
 * Cleared when the drive ends normally (arrival or Stop). Pure JVM: unit tested.
 */
class DriveStore(
    private val file: File,
    private val maxAgeMs: Long = 60 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis,
) : DriveStorage {
    private val json = Json { ignoreUnknownKeys = true }

    override fun save(route: Route, destination: Place) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(SavedDrive(route, destination, now())))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    /** The saved drive, or null if there is none, it is unreadable, or it is older than [maxAgeMs]. */
    override fun load(): SavedDrive? {
        if (!file.exists()) return null
        val drive = runCatching { json.decodeFromString<SavedDrive>(file.readText()) }.getOrNull()
        val age = drive?.let { now() - it.savedAtEpochMs }
        if (drive == null || age == null || age < 0 || age > maxAgeMs) {
            clear()
            return null
        }
        return drive
    }

    override fun clear() {
        file.delete()
    }
}

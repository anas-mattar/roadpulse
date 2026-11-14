package com.roadpulse.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedPlaces(
    val home: Place? = null,
    val work: Place? = null,
    /** Most recent first. */
    val recent: List<Place> = emptyList(),
)

enum class Shortcut { HOME, WORK }

/**
 * Home, Work and recent destinations, stored only on this device (app-private file, removed on
 * uninstall or with "Clear saved places"). Pure JVM: unit tested.
 */
class PlacesStore(private val file: File, private val maxRecent: Int = 8) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): SavedPlaces =
        runCatching { json.decodeFromString<SavedPlaces>(file.readText()) }.getOrDefault(SavedPlaces())

    fun setShortcut(shortcut: Shortcut, place: Place): SavedPlaces = update {
        when (shortcut) {
            Shortcut.HOME -> it.copy(home = place)
            Shortcut.WORK -> it.copy(work = place)
        }
    }

    /** Adds a destination to the front of the recent list, removing an earlier copy of it. */
    fun addRecent(place: Place): SavedPlaces = update { saved ->
        val others = saved.recent.filterNot { samePlace(it, place) }
        saved.copy(recent = (listOf(place) + others).take(maxRecent))
    }

    fun clear(): SavedPlaces {
        file.delete()
        return SavedPlaces()
    }

    private fun update(change: (SavedPlaces) -> SavedPlaces): SavedPlaces {
        val updated = change(load())
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(updated))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
        return updated
    }

    companion object {
        /** Same name within ~50 m counts as the same place. */
        fun samePlace(a: Place, b: Place): Boolean =
            a.name.equals(b.name, ignoreCase = true) && Geo.distanceMeters(a.location, b.location) < 50
    }
}

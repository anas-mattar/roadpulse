package com.roadpulse.app.data

import com.roadpulse.app.util.mathRound
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SavedPlaces(
    val home: Place? = null,
    val work: Place? = null,
    /** Most recent first. */
    val recent: List<Place> = emptyList(),
    /** Places saved under the driver's own name, in the order saved. */
    val favourites: List<Place> = emptyList(),
    /** Where the driver parked, if they marked it. */
    val parking: ParkedCar? = null,
    /** Drives planned for later, soonest first. */
    val planned: List<PlannedDrive> = emptyList(),
)

/**
 * A drive planned for later: be at [destination] by [arriveByMillis]. [driveSeconds] is the
 * route's travel time when it was planned (no live traffic yet, so it does not change).
 */
@Serializable
data class PlannedDrive(val id: Long, val destination: Place, val arriveByMillis: Long, val driveSeconds: Double) {
    /** When to set off: travel time plus a margin for parking and surprises. */
    val leaveAtMillis: Long get() = arriveByMillis - (driveSeconds * 1000).toLong() - SPARE_MINUTES * 60_000L
    /** When the reminder appears: a few minutes before it is time to leave. */
    val remindAtMillis: Long get() = leaveAtMillis - REMIND_EARLY_MINUTES * 60_000L
    val driveMinutes: Int get() = mathRound(driveSeconds / 60).toInt()

    companion object {
        const val SPARE_MINUTES = 10
        const val REMIND_EARLY_MINUTES = 5
    }
}

/** A marked parking spot and when it was marked (wall-clock milliseconds). */
@Serializable
data class ParkedCar(val location: GeoPoint, val savedAtMillis: Long)

enum class Shortcut { HOME, WORK }

/** Where the saved places live: a file on Android, the app's settings on iPhone. */
interface TextFile {
    /** The text, or null when nothing is saved yet. */
    fun read(): String?
    fun write(text: String)
    fun delete()
}

/**
 * Home, Work and recent destinations, stored only on this device (app-private file, removed on
 * uninstall or with "Clear saved places"). Shared by Android and iPhone; unit tested.
 */
class PlacesStore(private val file: TextFile, private val maxRecent: Int = 8) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): SavedPlaces =
        runCatching { json.decodeFromString(SavedPlaces.serializer(), file.read()!!) }.getOrDefault(SavedPlaces())

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

    /** Saves [place] as a favourite under [name], replacing a favourite with the same name. */
    fun addFavourite(name: String, place: Place): SavedPlaces = update { saved ->
        val named = place.copy(name = name.trim())
        saved.copy(favourites = saved.favourites.filterNot { it.name.equals(named.name, ignoreCase = true) } + named)
    }

    fun removeFavourite(place: Place): SavedPlaces = update { saved ->
        saved.copy(favourites = saved.favourites.filterNot { samePlace(it, place) })
    }

    fun setParking(parked: ParkedCar?): SavedPlaces = update { it.copy(parking = parked) }

    /** Adds a planned drive and drops ones that are over (arrival time more than an hour ago). */
    fun addPlanned(drive: PlannedDrive, nowMillis: Long): SavedPlaces = update { saved ->
        saved.copy(planned = (saved.planned.filter { it.id != drive.id && it.arriveByMillis > nowMillis - 3_600_000 } + drive)
            .sortedBy { it.arriveByMillis })
    }

    fun removePlanned(id: Long): SavedPlaces = update { saved -> saved.copy(planned = saved.planned.filterNot { it.id == id }) }

    fun clear(): SavedPlaces {
        file.delete()
        return SavedPlaces()
    }

    private fun update(change: (SavedPlaces) -> SavedPlaces): SavedPlaces {
        val updated = change(load())
        runCatching { file.write(json.encodeToString(SavedPlaces.serializer(), updated)) }
        return updated
    }

    companion object {
        /** Same name within ~50 m counts as the same place. */
        fun samePlace(a: Place, b: Place): Boolean =
            a.name.equals(b.name, ignoreCase = true) && Geo.distanceMeters(a.location, b.location) < 50
    }
}

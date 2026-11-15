package com.roadpulse.app.ios

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.PlacesStore
import com.roadpulse.app.data.PlannedDrive
import com.roadpulse.app.data.SavedPlaces
import com.roadpulse.app.data.Shortcut
import com.roadpulse.app.data.TextFile

/** Where this phone keeps its saved places (the app's own settings on iPhone). */
expect fun phonePlacesFile(): TextFile

/** A drive planned for later, in simple types. Times are milliseconds since 1970. */
class PlannedItem(
    val id: Long, val place: SearchResult, val arriveByMillis: Long, val leaveAtMillis: Long,
    val remindAtMillis: Long, val driveMinutes: Int,
)

/**
 * Home, Work, favourites, recent destinations and planned drives on the iPhone, through the same
 * [PlacesStore] as Android. Stored only on the phone.
 */
class SavedPlacesBook(file: TextFile) {
    /** The book kept on this phone. */
    constructor() : this(phonePlacesFile())

    private val store = PlacesStore(file)
    private var saved: SavedPlaces = store.load()

    val home: SearchResult? get() = saved.home?.let(::result)
    val work: SearchResult? get() = saved.work?.let(::result)
    val favourites: List<SearchResult> get() = saved.favourites.map(::result)
    val recent: List<SearchResult> get() = saved.recent.map(::result)

    /** Planned drives that are not over yet, soonest first. */
    fun planned(nowMillis: Long): List<PlannedItem> = saved.planned.filter { it.arriveByMillis > nowMillis }.map(::item)

    fun setHome(place: SearchResult) { saved = store.setShortcut(Shortcut.HOME, place(place)) }
    fun setWork(place: SearchResult) { saved = store.setShortcut(Shortcut.WORK, place(place)) }
    fun addRecent(place: SearchResult) { saved = store.addRecent(place(place)) }
    fun addFavourite(name: String, place: SearchResult) { saved = store.addFavourite(name, place(place)) }
    fun removeFavourite(place: SearchResult) { saved = store.removeFavourite(place(place)) }

    /**
     * Plans a drive to [place] arriving by [arriveByMillis] with the chosen route; returns it, or
     * null when it is already too late to leave on time.
     */
    fun addPlanned(place: SearchResult, route: RouteSummary, arriveByMillis: Long, nowMillis: Long): PlannedItem? {
        val drive = PlannedDrive(nowMillis, place(place), arriveByMillis, route.route.timeSeconds)
        if (drive.leaveAtMillis <= nowMillis) return null
        saved = store.addPlanned(drive, nowMillis)
        return item(drive)
    }

    /** When to leave for [arriveByMillis] with [route] (to show before saving). */
    fun leaveAtMillis(route: RouteSummary, arriveByMillis: Long): Long =
        PlannedDrive(0, Place("", "", GeoPoint(0.0, 0.0)), arriveByMillis, route.route.timeSeconds).leaveAtMillis

    fun removePlanned(id: Long) { saved = store.removePlanned(id) }

    fun clear() { saved = store.clear() }

    private fun result(p: Place) = SearchResult(p.name, p.description, p.location.lat, p.location.lon)
    private fun place(r: SearchResult) = Place(r.name, r.detail, GeoPoint(r.lat, r.lon))
    private fun item(d: PlannedDrive) = PlannedItem(d.id, result(d.destination), d.arriveByMillis, d.leaveAtMillis, d.remindAtMillis, d.driveMinutes)

    /** Minutes added to the drive for parking and surprises. */
    val spareMinutes: Int get() = PlannedDrive.SPARE_MINUTES
}

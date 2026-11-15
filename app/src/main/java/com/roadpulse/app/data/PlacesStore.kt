package com.roadpulse.app.data

import java.io.File

/** Saved places in an app-private file (written to a temporary file first, then renamed). */
class FileText(private val file: File) : TextFile {
    override fun read(): String? = if (file.exists()) file.readText() else null

    override fun write(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    override fun delete() {
        file.delete()
    }
}

/** The Android places store: [PlacesStore] (shared) on a file. */
fun PlacesStore(file: File, maxRecent: Int = 8): PlacesStore = PlacesStore(FileText(file), maxRecent)

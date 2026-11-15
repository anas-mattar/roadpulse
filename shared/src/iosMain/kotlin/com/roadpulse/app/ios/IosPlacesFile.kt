package com.roadpulse.app.ios

import com.roadpulse.app.data.TextFile
import platform.Foundation.NSUserDefaults

/** Saved places as JSON text in the app's own settings (removed when the app is deleted). */
private class UserDefaultsText(private val key: String) : TextFile {
    override fun read(): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)
    override fun write(text: String) = NSUserDefaults.standardUserDefaults.setObject(text, key)
    override fun delete() = NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
}

actual fun phonePlacesFile(): TextFile = UserDefaultsText("places.json")

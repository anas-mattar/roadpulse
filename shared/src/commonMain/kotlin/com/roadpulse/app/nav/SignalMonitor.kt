package com.roadpulse.app.nav

/**
 * Decides when GPS counts as lost (no usable fix for [lostAfterMs]) and when it is back.
 * Times come from one monotonic clock. Pure Kotlin: unit tested.
 */
class SignalMonitor(private val lostAfterMs: Long = 6_000) {
    private var lastUsableAt: Long? = null
    var lost = false
        private set

    /** Records a fix. Returns true if this fix ends a GPS-lost period. */
    fun onFix(nowMs: Long, usable: Boolean): Boolean {
        if (!usable) return false
        lastUsableAt = nowMs
        if (lost) {
            lost = false
            return true
        }
        return false
    }

    /** Called periodically. Returns true when GPS has just become lost. */
    fun check(nowMs: Long): Boolean {
        val last = lastUsableAt ?: nowMs.also { lastUsableAt = it }
        if (!lost && nowMs - last >= lostAfterMs) {
            lost = true
            return true
        }
        return false
    }

    fun reset(nowMs: Long) {
        lastUsableAt = nowMs
        lost = false
    }
}

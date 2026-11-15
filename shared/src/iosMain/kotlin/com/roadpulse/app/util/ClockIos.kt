package com.roadpulse.app.util

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun wallClockMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

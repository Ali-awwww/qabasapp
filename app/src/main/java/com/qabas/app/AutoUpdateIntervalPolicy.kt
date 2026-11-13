package com.qabas.app

/** Pure interval rules shared by UpdateManager and JVM unit tests. */
internal object AutoUpdateIntervalPolicy {
    const val MIN_INTERVAL_HOURS = 2
    const val DEFAULT_INTERVAL_HOURS = 2

    fun clampHours(hours: Int): Int = hours.coerceAtLeast(MIN_INTERVAL_HOURS)

    fun hoursToMillis(hours: Int): Long =
        clampHours(hours) * 60L * 60L * 1000L
}

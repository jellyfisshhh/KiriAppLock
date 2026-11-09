package com.example.applock.permissions

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

/**
 * Best-effort fallback used when an accessibility window event does not carry a
 * package name. Requires the usage-access permission; returns null without it.
 *
 * [lookbackMillis] can be widened when the caller needs the last foreground app
 * even if nothing happened recently, for example right after the monitoring
 * service restarts while a protected app is already on screen.
 */
fun currentForegroundPackage(
    context: Context,
    lookbackMillis: Long = 10_000L
): String? {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
        as? UsageStatsManager ?: return null

    val now = System.currentTimeMillis()
    val begin = if (lookbackMillis >= now) 0L else now - lookbackMillis
    val events = usageStatsManager.queryEvents(begin, now) ?: return null
    val event = UsageEvents.Event()
    var foreground: String? = null

    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        val movedToForeground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
        if (movedToForeground) {
            foreground = event.packageName
        }
    }
    return foreground
}

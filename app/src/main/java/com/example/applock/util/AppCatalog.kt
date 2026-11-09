package com.example.applock.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

fun loadLaunchableApps(context: Context): List<AppEntry> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, 0)
    }

    return resolved.asSequence()
        .mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val applicationInfo = activityInfo.applicationInfo ?: return@mapNotNull null
            AppEntry(
                packageName = applicationInfo.packageName,
                label = applicationInfo.loadLabel(packageManager).toString(),
                icon = applicationInfo.loadIcon(packageManager)
            )
        }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .toList()
}

fun appLabel(context: Context, packageName: String?): String? {
    if (packageName.isNullOrBlank()) return null
    return runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}

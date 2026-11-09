package com.example.applock.permissions

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.example.applock.service.AppLockAccessibilityService

data class PermissionSnapshot(
    val usageAccess: Boolean,
    val batteryUnrestricted: Boolean,
    val accessibilityEnabled: Boolean
)

fun readPermissionSnapshot(context: Context): PermissionSnapshot = PermissionSnapshot(
    usageAccess = hasUsageAccess(context),
    batteryUnrestricted = isIgnoringBatteryOptimizations(context),
    accessibilityEnabled = isAccessibilityServiceEnabled(context)
)

fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, AppLockAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()

    return enabledServices.split(':').any { entry ->
        val component = ComponentName.unflattenFromString(entry) ?: return@any false
        component == expected
    }
}

fun openUsageAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    launchSafely(context, intent, fallback = appDetailsIntent(context))
}

fun requestBatteryOptimizationExemption(context: Context) {
    val request = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )
    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    launchSafely(context, request, fallback)
}

fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    launchSafely(context, intent, fallback = appDetailsIntent(context))
}

private fun appDetailsIntent(context: Context): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.parse("package:${context.packageName}")
)

private fun launchSafely(context: Context, intent: Intent, fallback: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val launched = runCatching { context.startActivity(intent) }.isSuccess
    if (!launched) {
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}

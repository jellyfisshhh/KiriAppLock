package com.example.applock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.example.applock.data.AppSettings
import com.example.applock.data.RelockBehavior
import com.example.applock.data.SettingsRepository
import com.example.applock.permissions.currentForegroundPackage
import com.example.applock.ui.LockActivity

/**
 * Watches window changes so protected applications can be gated with the PIN
 * screen. The service never reads window content and never logs package names
 * or PIN input.
 *
 * Only real application transitions are considered. Activity transitions inside
 * the same package, in-app WebViews, Chrome Custom Tabs, this app's own windows
 * (including [LockActivity]), system surfaces that appear on top of an app
 * (notification shade, keyboard, permission dialogs, choosers) and system-hosted
 * delegate activities such as Google settings opened from Settings never count
 * as leaving the app, so they cannot end an authenticated session.
 *
 * Sessions are tracked per package by [LockSessionManager]. Switching to another
 * application leaves every other app's session intact for the timed and
 * screen-off policies. For [RelockBehavior.IMMEDIATELY] a real app switch is
 * the relock condition and ends the previous app's session.
 */
class AppLockAccessibilityService : AccessibilityService() {

    private var repository: SettingsRepository? = null
    private var receiverRegistered = false
    private val applicationWindowCache = mutableMapOf<String, Boolean>()
    private var trackedForegroundPackage: String? = null

    private val homePackages: Set<String> by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val packages = packageManager.queryIntentActivities(homeIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toMutableSet()
        packageManager.resolveActivity(homeIntent, 0)
            ?.activityInfo?.packageName
            ?.let(packages::add)
        packages
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            val settings = settings() ?: return
            LockSessionManager.onScreenOff(settings.relockBehavior)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = SettingsRepository.getInstance(applicationContext)

        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }

        // If a protected application is already in the foreground nothing will
        // emit a window change until the user switches away, so check once here.
        // This also re-locks after a service reconnect or process restart.
        syncWithForegroundApp()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val packageName = event.packageName?.toString()
            ?: currentForegroundPackage(this)
            ?: return
        val className = event.className?.toString().orEmpty()

        onForegroundPackage(packageName, className)
    }

    private fun syncWithForegroundApp() {
        // Nothing emits a window event while the user is idle, so look far
        // enough back to find the app that was last brought to the foreground.
        // This is what re-locks a protected app after a service or process
        // restart instead of leaving it exposed until the next interaction.
        val foreground = currentForegroundPackage(
            this,
            lookbackMillis = 24 * 60 * 60 * 1000L
        ) ?: return
        onForegroundPackage(foreground, className = "")
    }

    /**
     * [packageName] is the package of the window that just came to the front
     * and [className] is its activity/window class when the event provides one.
     */
    private fun onForegroundPackage(packageName: String, className: String) {
        if (isSystemSurface(packageName)) return

        val settings = settings() ?: return
        if (!settings.appLockEnabled || !settings.hasPin) {
            LockSessionManager.logout()
            return
        }

        // Chrome Custom Tabs (including the translucent variants) are hosted by
        // the browser but belong to the flow of the app that opened them. They
        // are therefore not an app switch: keep the calling app's session and
        // do not gate the browser window.
        if (isCustomTab(className)) return

        // Delegate activities are hosted by another package but are still part
        // of the current app's flow (Google settings opened from Settings, a
        // permission controller, a document chooser). They must not end the
        // authenticated session, or coming back would show the lock again.
        if (packageName !in settings.protectedApps && !isApplicationWindow(packageName)) return

        val behavior = settings.relockBehavior

        // A real foreground change from one app to another is the relock
        // condition for the immediate policy: end the previous package's
        // session before gating the new one. Delegate surfaces, custom tabs,
        // system overlays and this app's own windows return earlier, so they
        // never count as leaving the protected app.
        val previousPackage = trackedForegroundPackage
        if (previousPackage != null && previousPackage != packageName) {
            LockSessionManager.onOtherAppForeground(previousPackage, behavior)
        }
        trackedForegroundPackage = packageName

        if (packageName !in settings.protectedApps) return
        if (LockSessionManager.isAuthenticated(packageName, behavior)) return
        if (!LockSessionManager.shouldShowLock(packageName)) return

        LockSessionManager.requestLock(packageName)
        val intent = Intent(this, LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(LockActivity.EXTRA_PACKAGE_NAME, packageName)

        val launched = runCatching { startActivity(intent) }.isSuccess
        if (!launched) {
            // Let the next window event try again instead of believing a lock
            // screen is showing when none was started.
            LockSessionManager.onLockScreenHidden(packageName)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun settings(): AppSettings? = repository?.settings?.value

    /**
     * Windows that can appear above the current application without the user
     * actually switching applications. Treating these as an app switch would
     * end an authenticated session and make the lock screen reappear.
     */
    private fun isSystemSurface(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        if (packageName == "android" || packageName == "com.android.systemui") return true
        if (packageName in SYSTEM_SURFACE_PACKAGES) return true

        val defaultIme = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        return defaultIme?.startsWith("$packageName/") == true
    }

    /**
     * True for browser-hosted Chrome Custom Tab windows. They look like a
     * browser app switch in the accessibility stream even though the user never
     * left the calling application.
     */
    private fun isCustomTab(className: String): Boolean =
        className.contains("CustomTab", ignoreCase = true)

    /**
     * True when [packageName] is an application the user can switch to: it has
     * a launcher or home entry, or it is a user-installed app. System packages
     * without either are delegate windows that belong to another app's flow.
     */
    private fun isApplicationWindow(packageName: String): Boolean =
        applicationWindowCache.getOrPut(packageName) {
            if (packageManager.getLaunchIntentForPackage(packageName) != null) {
                return@getOrPut true
            }
            if (packageName in homePackages) return@getOrPut true

            runCatching {
                val info = packageManager.getApplicationInfo(packageName, 0)
                info.flags and (
                    ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                    ) == 0
            }.getOrDefault(true)
        }

    private companion object {
        val SYSTEM_SURFACE_PACKAGES = setOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.intentresolver",
            "com.google.android.intentresolver",
            "com.android.documentsui",
            "com.google.android.documentsui"
        )
    }
}

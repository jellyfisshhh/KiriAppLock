package com.example.applock.service

import android.os.SystemClock
import com.example.applock.data.RelockBehavior

/**
 * Process-wide authentication state for protected applications.
 *
 * [AppLockAccessibilityService], [com.example.applock.ui.LockActivity] and
 * [com.example.applock.MainActivity] all run in the same process and share this
 * object, so there is exactly one source of truth for:
 *
 *  - which applications are currently authenticated, and when each was unlocked;
 *  - whether a lock screen for an application is already showing or pending,
 *    which prevents duplicate lock screens when many window events arrive in a
 *    row (rapid app switching, activity transitions, service reconnects).
 *
 * Sessions are kept per package. Authenticating one application never replaces
 * or invalidates the session of another application.
 *
 * A session ends when a real relock condition applies to that package:
 *  - [RelockBehavior.IMMEDIATELY]: the protected application leaves the
 *    foreground for a different real application (reported through
 *    [onOtherAppForeground]).
 *  - [RelockBehavior.SCREEN_OFF] / [RelockBehavior.IMMEDIATELY] on screen-off
 *    (see [onScreenOff]).
 *  - [RelockBehavior.AFTER_1_MINUTE] / [RelockBehavior.AFTER_5_MINUTES]: the
 *    session ages out by the configured delay.
 *  - The process is recreated (memory-only state).
 *
 * Switching to another application does not end the session for the timed and
 * screen-off policies; they relock only on screen-off or on expiry.
 *
 * The state is deliberately memory-only. If the process is recreated every
 * session disappears and the user authenticates again, which is the safe
 * default for a lock.
 */
object LockSessionManager {

    private val authenticatedAt = mutableMapOf<String, Long>()
    private var lockScreenPackage: String? = null
    private var pendingLockPackage: String? = null

    fun authenticate(
        packageName: String,
        now: Long = SystemClock.elapsedRealtime()
    ) {
        authenticatedAt[packageName] = now
        if (pendingLockPackage == packageName) pendingLockPackage = null
        if (lockScreenPackage == packageName) lockScreenPackage = null
    }

    fun logout() {
        authenticatedAt.clear()
    }

    fun logout(packageName: String) {
        authenticatedAt.remove(packageName)
        if (pendingLockPackage == packageName) pendingLockPackage = null
        if (lockScreenPackage == packageName) lockScreenPackage = null
    }

    /**
     * True when [packageName] was authenticated and its relock policy has not
     * expired yet. Expired timed sessions are dropped as they are observed.
     */
    fun isAuthenticated(
        packageName: String,
        behavior: RelockBehavior,
        now: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        val startedAt = authenticatedAt[packageName] ?: return false
        return when (behavior) {
            RelockBehavior.IMMEDIATELY,
            RelockBehavior.SCREEN_OFF -> true

            RelockBehavior.AFTER_1_MINUTE,
            RelockBehavior.AFTER_5_MINUTES -> {
                val stillValid = now - startedAt < behavior.delayMillis
                if (!stillValid) authenticatedAt.remove(packageName)
                stillValid
            }
        }
    }

    fun onScreenOff(behavior: RelockBehavior) {
        when (behavior) {
            RelockBehavior.IMMEDIATELY,
            RelockBehavior.SCREEN_OFF -> logout()

            // Timed sessions are controlled by their own age, not by the
            // screen state, so they keep counting down while the screen is off.
            RelockBehavior.AFTER_1_MINUTE,
            RelockBehavior.AFTER_5_MINUTES -> Unit
        }
    }

    /**
     * Called when a different real application becomes foreground. Ends the
     * session of the previous package when the policy is
     * [RelockBehavior.IMMEDIATELY] (a real app switch is the relock condition).
     * The timed and screen-off policies are unaffected by a plain switch.
     */
    fun onOtherAppForeground(previousPackage: String, behavior: RelockBehavior) {
        if (behavior == RelockBehavior.IMMEDIATELY) {
            logout(previousPackage)
        }
    }

    /** Marks that a lock screen is about to be launched for [packageName]. */
    fun requestLock(packageName: String) {
        pendingLockPackage = packageName
    }

    fun onLockScreenShown(packageName: String) {
        lockScreenPackage = packageName
        if (pendingLockPackage == packageName) pendingLockPackage = null
    }

    fun onLockScreenHidden(packageName: String? = null) {
        if (packageName == null || lockScreenPackage == packageName) {
            lockScreenPackage = null
        }
        if (packageName == null || pendingLockPackage == packageName) {
            pendingLockPackage = null
        }
    }

    /**
     * False when the lock screen for [packageName] is already visible or being
     * launched, so callers never stack duplicate authentication overlays.
     */
    fun shouldShowLock(packageName: String): Boolean =
        lockScreenPackage != packageName && pendingLockPackage != packageName
}

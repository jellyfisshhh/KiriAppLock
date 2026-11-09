package com.example.applock.data

enum class ThemeMode(val label: String) {
    SYSTEM("Follow System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class RelockBehavior(val label: String, val delayMillis: Long) {
    IMMEDIATELY("Immediately", 0L),
    SCREEN_OFF("When screen turns off", -1L),
    AFTER_1_MINUTE("After 1 minute", 60_000L),
    AFTER_5_MINUTES("After 5 minutes", 300_000L)
}

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val pinLength: Int = 4,
    val fakePinHash: String? = null,
    val fakePinSalt: String? = null,
    val fakePinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAutoPrompt: Boolean = true,
    val scrambleKeypad: Boolean = false,
    val keypadLeftInLandscape: Boolean = false,
    val appLockEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val relockBehavior: RelockBehavior = RelockBehavior.IMMEDIATELY,
    val protectedApps: Set<String> = emptySet(),
    val decoyPackage: String? = null
) {
    val hasPin: Boolean
        get() = !pinHash.isNullOrBlank() && !pinSalt.isNullOrBlank()

    val hasFakePin: Boolean
        get() = !fakePinHash.isNullOrBlank() && !fakePinSalt.isNullOrBlank()

    /**
     * Decoy app that is safe to launch after a fake PIN. A decoy that is itself
     * protected (or is the app being unlocked) must never be launched, because
     * that would hand over the very app the fake PIN is meant to hide.
     */
    fun safeDecoyPackage(lockedPackage: String? = null): String? =
        decoyPackage?.takeIf {
            it.isNotBlank() && it !in protectedApps && it != lockedPackage
        }
}

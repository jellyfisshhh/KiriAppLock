package com.example.applock.data

import android.content.Context
import com.example.applock.security.PinHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class PinMatch { REAL, FAKE, NONE }

class SettingsRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings {
        val protectedApps = prefs.getStringSet(KEY_PROTECTED_APPS, emptySet())
            ?.toSet()
            .orEmpty()
        return AppSettings(
            onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false),
            pinHash = prefs.getString(KEY_PIN_HASH, null),
            pinSalt = prefs.getString(KEY_PIN_SALT, null),
            pinLength = prefs.getInt(KEY_PIN_LENGTH, 4).coerceIn(MIN_PIN_LENGTH, MAX_PIN_LENGTH),
            fakePinHash = prefs.getString(KEY_FAKE_PIN_HASH, null),
            fakePinSalt = prefs.getString(KEY_FAKE_PIN_SALT, null),
            fakePinEnabled = prefs.getBoolean(KEY_FAKE_PIN_ENABLED, false),
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false),
            biometricAutoPrompt = prefs.getBoolean(KEY_BIOMETRIC_AUTO_PROMPT, true),
            scrambleKeypad = prefs.getBoolean(KEY_SCRAMBLE, false),
            keypadLeftInLandscape = prefs.getBoolean(KEY_KEYPAD_LEFT, false),
            appLockEnabled = prefs.getBoolean(KEY_APP_LOCK, true),
            themeMode = prefs.getString(KEY_THEME, null)
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            relockBehavior = prefs.getString(KEY_RELOCK, null)
                ?.let { runCatching { RelockBehavior.valueOf(it) }.getOrNull() }
                ?: RelockBehavior.IMMEDIATELY,
            protectedApps = protectedApps,
            decoyPackage = prefs.getString(KEY_DECOY_PACKAGE, null)
        )
    }

    private fun persist(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, settings.onboardingCompleted)
            .putString(KEY_PIN_HASH, settings.pinHash)
            .putString(KEY_PIN_SALT, settings.pinSalt)
            .putInt(KEY_PIN_LENGTH, settings.pinLength)
            .putString(KEY_FAKE_PIN_HASH, settings.fakePinHash)
            .putString(KEY_FAKE_PIN_SALT, settings.fakePinSalt)
            .putBoolean(KEY_FAKE_PIN_ENABLED, settings.fakePinEnabled)
            .putBoolean(KEY_BIOMETRIC, settings.biometricEnabled)
            .putBoolean(KEY_BIOMETRIC_AUTO_PROMPT, settings.biometricAutoPrompt)
            .putBoolean(KEY_SCRAMBLE, settings.scrambleKeypad)
            .putBoolean(KEY_KEYPAD_LEFT, settings.keypadLeftInLandscape)
            .putBoolean(KEY_APP_LOCK, settings.appLockEnabled)
            .putString(KEY_THEME, settings.themeMode.name)
            .putString(KEY_RELOCK, settings.relockBehavior.name)
            .putStringSet(KEY_PROTECTED_APPS, settings.protectedApps)
            .putString(KEY_DECOY_PACKAGE, settings.decoyPackage)
            .apply()
        _settings.value = settings
    }

    suspend fun verifyPin(pin: String): PinMatch = withContext(Dispatchers.Default) {
        val current = _settings.value
        val realMatch = current.pinHash != null && current.pinSalt != null &&
            PinHasher.verify(pin, current.pinSalt, current.pinHash)
        val fakeMatch = current.fakePinEnabled &&
            current.fakePinHash != null && current.fakePinSalt != null &&
            PinHasher.verify(pin, current.fakePinSalt, current.fakePinHash)
        when {
            realMatch -> PinMatch.REAL
            fakeMatch -> PinMatch.FAKE
            else -> PinMatch.NONE
        }
    }

    fun setPin(pin: String, length: Int = _settings.value.pinLength) {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        val current = _settings.value
        val lengthChanged = current.pinLength != length
        persist(
            current.copy(
                pinHash = hash,
                pinSalt = salt,
                pinLength = length.coerceIn(MIN_PIN_LENGTH, MAX_PIN_LENGTH),
                // A fake PIN tied to the previous length would no longer match the
                // configured keypad length, so require it to be set up again.
                fakePinEnabled = if (lengthChanged) false else current.fakePinEnabled,
                fakePinHash = if (lengthChanged) null else current.fakePinHash,
                fakePinSalt = if (lengthChanged) null else current.fakePinSalt
            )
        )
    }

    fun setFakePin(enabled: Boolean, pin: String?) {
        if (!enabled) {
            persist(
                _settings.value.copy(
                    fakePinEnabled = false,
                    fakePinHash = null,
                    fakePinSalt = null
                )
            )
            return
        }
        val newPin = pin ?: return
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(newPin, salt)
        persist(
            _settings.value.copy(
                fakePinEnabled = true,
                fakePinHash = hash,
                fakePinSalt = salt
            )
        )
    }

    fun setBiometricEnabled(enabled: Boolean) {
        persist(_settings.value.copy(biometricEnabled = enabled))
    }

    fun setBiometricAutoPrompt(enabled: Boolean) {
        persist(_settings.value.copy(biometricAutoPrompt = enabled))
    }

    fun setOnboardingCompleted(completed: Boolean) {
        persist(_settings.value.copy(onboardingCompleted = completed))
    }

    fun setScrambleKeypad(enabled: Boolean) {
        persist(_settings.value.copy(scrambleKeypad = enabled))
    }

    fun setKeypadLeftInLandscape(enabled: Boolean) {
        persist(_settings.value.copy(keypadLeftInLandscape = enabled))
    }

    fun setAppLockEnabled(enabled: Boolean) {
        persist(_settings.value.copy(appLockEnabled = enabled))
    }

    fun setThemeMode(mode: ThemeMode) {
        persist(_settings.value.copy(themeMode = mode))
    }

    fun setRelockBehavior(behavior: RelockBehavior) {
        persist(_settings.value.copy(relockBehavior = behavior))
    }

    fun setProtectedApps(packages: Set<String>) {
        persist(
            _settings.value.copy(
                protectedApps = packages,
                // A protected app can no longer serve as the decoy.
                decoyPackage = _settings.value.decoyPackage?.takeUnless { it in packages }
            )
        )
    }

    fun toggleProtectedApp(packageName: String) {
        val current = _settings.value.protectedApps
        setProtectedApps(
            if (packageName in current) current - packageName else current + packageName
        )
    }

    fun setDecoyPackage(packageName: String?) {
        persist(_settings.value.copy(decoyPackage = packageName))
    }

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }

        private const val PREFS_NAME = "app_lock_settings"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_FAKE_PIN_HASH = "fake_pin_hash"
        private const val KEY_FAKE_PIN_SALT = "fake_pin_salt"
        private const val KEY_FAKE_PIN_ENABLED = "fake_pin_enabled"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_BIOMETRIC_AUTO_PROMPT = "biometric_auto_prompt"
        private const val KEY_SCRAMBLE = "scramble_keypad"
        private const val KEY_KEYPAD_LEFT = "keypad_left_in_landscape"
        private const val KEY_APP_LOCK = "app_lock_enabled"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_RELOCK = "relock_behavior"
        private const val KEY_PROTECTED_APPS = "protected_apps"
        private const val KEY_DECOY_PACKAGE = "decoy_package"
    }
}

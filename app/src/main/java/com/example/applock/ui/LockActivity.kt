package com.example.applock.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.applock.data.SettingsRepository
import com.example.applock.service.LockSessionManager
import com.example.applock.ui.theme.AppLockTheme

/**
 * Full-screen gate shown by the accessibility service when a protected
 * application comes to the foreground.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var repository: SettingsRepository
    private var lockedPackage by mutableStateOf("")

    /**
     * Identifies the current lock-screen session. It changes every time the
     * service asks for a new lock, which resets the entered digits, error
     * message, biometric state and (with scramble enabled) the keypad order.
     * Saved across configuration changes so rotation preserves them.
     */
    private var keypadSession by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        keypadSession = savedInstanceState?.getInt(KEY_KEYPAD_SESSION, 0) ?: 0
        lockedPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        if (lockedPackage.isBlank()) {
            finish()
            return
        }

        enableEdgeToEdge()
        repository = SettingsRepository.getInstance(applicationContext)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = leaveLockScreen()
            }
        )

        setContent {
            val settings by repository.settings.collectAsStateWithLifecycle()
            var showDecoy by rememberSaveable(lockedPackage, keypadSession) {
                mutableStateOf(false)
            }

            fun allowThrough() {
                LockSessionManager.authenticate(lockedPackage)
                finish()
            }

            AppLockTheme(themeMode = settings.themeMode) {
                when {
                    showDecoy -> DecoyScreen(onExit = ::leaveLockScreen)

                    !settings.appLockEnabled ||
                        !settings.hasPin ||
                        lockedPackage !in settings.protectedApps -> {
                        LaunchedEffect(Unit) { finish() }
                    }

                    else -> LockScreen(
                        settings = settings,
                        repository = repository,
                        sessionKey = keypadSession,
                        onUnlocked = { allowThrough() },
                        onFakePin = {
                            val decoyPackage = settings.safeDecoyPackage(lockedPackage)
                            if (decoyPackage.isNullOrBlank()) {
                                showDecoy = true
                            } else {
                                // A fake PIN must never reveal the protected app.
                                val decoyIntent = packageManager
                                    .getLaunchIntentForPackage(decoyPackage)
                                if (decoyIntent != null) {
                                    decoyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { startActivity(decoyIntent) }
                                    finish()
                                } else {
                                    showDecoy = true
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (lockedPackage.isNotBlank()) {
            LockSessionManager.onLockScreenShown(lockedPackage)
        }
    }

    override fun onStop() {
        // Home, an incoming call or any other activity can push the lock
        // screen to the background. It is no longer protecting anything then,
        // so clear the overlay state and let the service lock again on return.
        LockSessionManager.onLockScreenHidden(lockedPackage)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val next = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        when {
            next.isBlank() -> finish()
            next != lockedPackage -> {
                LockSessionManager.onLockScreenHidden(lockedPackage)
                lockedPackage = next
                keypadSession++
                LockSessionManager.onLockScreenShown(next)
            }
            else -> {
                // The same app is being locked again (for example after
                // returning from another application). Start a fresh lock
                // screen so the keypad is reshuffled and no stale input
                // survives from the previous attempt.
                keypadSession++
                LockSessionManager.onLockScreenShown(next)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_KEYPAD_SESSION, keypadSession)
    }

    private fun leaveLockScreen() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "com.example.applock.extra.LOCKED_PACKAGE"
        private const val KEY_KEYPAD_SESSION = "keypad_session"
    }
}

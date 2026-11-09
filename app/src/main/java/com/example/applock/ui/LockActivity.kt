package com.example.applock.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            var showDecoy by remember(lockedPackage) { mutableStateOf(false) }

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
                        sessionKey = 0,
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
                LockSessionManager.onLockScreenShown(next)
            }
        }
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
    }
}

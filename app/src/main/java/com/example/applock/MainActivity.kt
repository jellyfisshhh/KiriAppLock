package com.example.applock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.applock.data.SettingsRepository
import com.example.applock.ui.AppRoot
import com.example.applock.ui.theme.AppLockTheme

class AppSession {
    var unlocked by mutableStateOf(false)
    var decoy by mutableStateOf(false)
    var keypadSession by mutableIntStateOf(0)

    fun unlock() {
        unlocked = true
        decoy = false
    }

    fun showDecoy() {
        decoy = true
        unlocked = false
    }

    fun lock() {
        unlocked = false
        decoy = false
        keypadSession++
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var repository: SettingsRepository
    private val session = AppSession()
    private var backgroundAt = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val settings = repository.settings.value
                if (!settings.appLockEnabled) return
                when (settings.relockBehavior) {
                    com.example.applock.data.RelockBehavior.IMMEDIATELY,
                    com.example.applock.data.RelockBehavior.SCREEN_OFF -> session.lock()

                    com.example.applock.data.RelockBehavior.AFTER_1_MINUTE,
                    com.example.applock.data.RelockBehavior.AFTER_5_MINUTES -> Unit
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = SettingsRepository.getInstance(applicationContext)

        setContent {
            val settings by repository.settings.collectAsStateWithLifecycle()
            AppLockTheme(themeMode = settings.themeMode) {
                AppRoot(
                    settings = settings,
                    repository = repository,
                    session = session,
                    onLaunchDecoy = ::launchDecoy,
                    onExitDecoy = ::finish
                )
            }
        }

        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStart() {
        super.onStart()
        if (backgroundAt > 0L) {
            val behavior = repository.settings.value.relockBehavior
            if (behavior.delayMillis > 0 &&
                SystemClock.elapsedRealtime() - backgroundAt >= behavior.delayMillis
            ) {
                session.lock()
            }
            backgroundAt = 0L
        }
    }

    override fun onStop() {
        super.onStop()
        val settings = repository.settings.value
        if (!settings.appLockEnabled || !session.unlocked) return

        when (settings.relockBehavior) {
            // Leaving the app for any other app is the relock condition for
            // this policy: lock again when the user comes back.
            com.example.applock.data.RelockBehavior.IMMEDIATELY -> session.lock()

            com.example.applock.data.RelockBehavior.SCREEN_OFF -> Unit

            com.example.applock.data.RelockBehavior.AFTER_1_MINUTE,
            com.example.applock.data.RelockBehavior.AFTER_5_MINUTES ->
                backgroundAt = SystemClock.elapsedRealtime()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    private fun launchDecoy(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(launchIntent) }
    }
}

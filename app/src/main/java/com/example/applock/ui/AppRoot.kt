package com.example.applock.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.applock.AppSession
import com.example.applock.data.AppSettings
import com.example.applock.data.SettingsRepository

@Composable
fun AppRoot(
    settings: AppSettings,
    repository: SettingsRepository,
    session: AppSession,
    onLaunchDecoy: (String?) -> Unit,
    onExitDecoy: () -> Unit
) {
    when {
        !settings.onboardingCompleted -> OnboardingScreen(
            settings = settings,
            repository = repository
        )

        !settings.hasPin -> SetupPinScreen(
            repository = repository,
            initialLength = settings.pinLength,
            onComplete = session::unlock
        )

        session.decoy -> DecoyScreen(onExit = onExitDecoy)

        settings.appLockEnabled && !session.unlocked -> LockScreen(
            settings = settings,
            repository = repository,
            sessionKey = session.keypadSession,
            onUnlocked = session::unlock,
            onFakePin = {
                session.showDecoy()
                onLaunchDecoy(settings.safeDecoyPackage())
            }
        )

        else -> MainScreen(
            settings = settings,
            repository = repository
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settings: AppSettings,
    repository: SettingsRepository
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (tab == 0) "Protected apps" else "Settings",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> AppsScreen(
                settings = settings,
                repository = repository,
                modifier = Modifier.padding(padding)
            )

            else -> SettingsScreen(
                settings = settings,
                repository = repository,
                onManageProtectedApps = { tab = 0 },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

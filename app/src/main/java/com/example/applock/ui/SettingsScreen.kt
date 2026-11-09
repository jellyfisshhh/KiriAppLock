package com.example.applock.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.applock.BuildConfig
import com.example.applock.data.AppSettings
import com.example.applock.data.RelockBehavior
import com.example.applock.data.SettingsRepository
import com.example.applock.data.ThemeMode
import com.example.applock.permissions.openAccessibilitySettings
import com.example.applock.permissions.openUsageAccessSettings
import com.example.applock.permissions.requestBatteryOptimizationExemption
import com.example.applock.security.canUseBiometrics
import com.example.applock.util.AppEntry
import com.example.applock.util.appLabel
import com.example.applock.util.loadLaunchableApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    settings: AppSettings,
    repository: SettingsRepository,
    onManageProtectedApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val permissions = rememberPermissionSnapshot()
    val decoyLabel = remember(settings.decoyPackage) {
        appLabel(context, settings.decoyPackage)
    }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: BuildConfig.VERSION_NAME
    }

    var showChangePin by remember { mutableStateOf(false) }
    var showFakePin by remember { mutableStateOf(false) }
    var showPinLength by remember { mutableStateOf(false) }
    var pendingNewLength by remember { mutableStateOf<Int?>(null) }
    var showRelock by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showDecoyPicker by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showBiometricUnavailable by remember { mutableStateOf(false) }
    var decoyApps by remember { mutableStateOf(emptyList<AppEntry>()) }

    LaunchedEffect(showDecoyPicker) {
        if (showDecoyPicker && decoyApps.isEmpty()) {
            decoyApps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SettingsSection(title = "Security") {
                SettingsRow(
                    title = "Change PIN",
                    subtitle = "Update the PIN used to unlock App Lock",
                    onClick = { showChangePin = true }
                )
                SectionDivider()
                SettingsRow(
                    title = "Biometric authentication",
                    subtitle = if (settings.biometricEnabled) {
                        "Biometric unlock is enabled"
                    } else {
                        "Use your fingerprint or face to unlock"
                    },
                    trailing = {
                        Switch(
                            checked = settings.biometricEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && (activity == null || !canUseBiometrics(activity))) {
                                    showBiometricUnavailable = true
                                } else {
                                    repository.setBiometricEnabled(enabled)
                                }
                            }
                        )
                    }
                )
                if (settings.biometricEnabled) {
                    SectionDivider()
                    SettingsRow(
                        title = "Launch biometric prompt automatically",
                        subtitle = "Show fingerprint or face unlock as soon as the " +
                            "lock screen opens",
                        trailing = {
                            Switch(
                                checked = settings.biometricAutoPrompt,
                                onCheckedChange = repository::setBiometricAutoPrompt
                            )
                        }
                    )
                }
                SectionDivider()
                SettingsRow(
                    title = "Fake PIN",
                    subtitle = if (settings.fakePinEnabled) {
                        "Fake PIN is enabled"
                    } else {
                        "Open a decoy interface with a second PIN"
                    },
                    trailing = {
                        Text(
                            text = if (settings.fakePinEnabled) "On" else "Off",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showFakePin = true }
                )
                SectionDivider()
                SettingsRow(
                    title = "Decoy application",
                    subtitle = decoyLabel ?: "No decoy application selected",
                    onClick = { showDecoyPicker = true }
                )
                SectionDivider()
                SettingsRow(
                    title = "Scramble Keypad",
                    subtitle = "Randomizes the number positions whenever the lock screen opens.",
                    trailing = {
                        Switch(
                            checked = settings.scrambleKeypad,
                            onCheckedChange = repository::setScrambleKeypad
                        )
                    }
                )
                SectionDivider()
                SettingsRow(
                    title = "PIN length",
                    subtitle = "Choose how many digits your PIN uses",
                    trailing = {
                        Text(
                            text = "${settings.pinLength} digits",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showPinLength = true }
                )
                SectionDivider()
                SettingsRow(
                    title = "Relock behavior",
                    subtitle = settings.relockBehavior.label,
                    onClick = { showRelock = true }
                )
            }
        }

        item {
            SettingsSection(title = "Permissions") {
                SettingsRow(
                    title = "Usage access",
                    subtitle = "Detect the application currently in the foreground",
                    trailing = {
                        PermissionStatus(permissions.usageAccess)
                    },
                    onClick = { openUsageAccessSettings(context) }
                )
                SectionDivider()
                SettingsRow(
                    title = "Background restrictions",
                    subtitle = "Keep App Lock running when the screen is off",
                    trailing = {
                        PermissionStatus(
                            granted = permissions.batteryUnrestricted,
                            grantedLabel = "Unrestricted",
                            missingLabel = "Restricted"
                        )
                    },
                    onClick = { requestBatteryOptimizationExemption(context) }
                )
                SectionDivider()
                SettingsRow(
                    title = "Accessibility service",
                    subtitle = "Detect protected application launches",
                    trailing = {
                        PermissionStatus(
                            granted = permissions.accessibilityEnabled,
                            grantedLabel = "Enabled",
                            missingLabel = "Disabled"
                        )
                    },
                    onClick = { openAccessibilitySettings(context) }
                )
            }
        }

        item {
            SettingsSection(title = "Appearance") {
                SettingsRow(
                    title = "Theme",
                    subtitle = "Follow the system or choose a fixed appearance",
                    trailing = {
                        Text(
                            text = settings.themeMode.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showTheme = true }
                )
                SectionDivider()
                SettingsRow(
                    title = "Left-handed landscape",
                    subtitle = "Place the keypad on the left side of the screen in landscape.",
                    trailing = {
                        Switch(
                            checked = settings.keypadLeftInLandscape,
                            onCheckedChange = repository::setKeypadLeftInLandscape
                        )
                    }
                )
            }
        }

        item {
            SettingsSection(title = "Application") {
                SettingsRow(
                    title = "Enable App Lock",
                    subtitle = "Require your PIN when App Lock opens",
                    trailing = {
                        Switch(
                            checked = settings.appLockEnabled,
                            onCheckedChange = repository::setAppLockEnabled
                        )
                    }
                )
                SectionDivider()
                SettingsRow(
                    title = "Manage protected applications",
                    subtitle = if (settings.protectedApps.isEmpty()) {
                        "No applications selected"
                    } else {
                        "${settings.protectedApps.size} applications selected"
                    },
                    onClick = onManageProtectedApps
                )
                SectionDivider()
                SettingsRow(
                    title = "Privacy policy",
                    onClick = { showPrivacy = true }
                )
                SectionDivider()
                SettingsRow(
                    title = "About application",
                    subtitle = "Version $versionName",
                    onClick = { showAbout = true }
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showChangePin) {
        ChangePinDialog(
            repository = repository,
            settings = settings,
            requireCurrent = true,
            onDismiss = { showChangePin = false },
            onSaved = { showChangePin = false }
        )
    }

    if (showFakePin) {
        FakePinDialog(
            repository = repository,
            settings = settings,
            onDismiss = { showFakePin = false },
            onSaved = { showFakePin = false }
        )
    }

    if (showPinLength) {
        ChoiceDialog(
            title = "PIN length",
            options = (SettingsRepository.MIN_PIN_LENGTH..SettingsRepository.MAX_PIN_LENGTH).toList(),
            selected = settings.pinLength,
            label = { "$it digits" },
            onDismiss = { showPinLength = false },
            onSelect = { length ->
                showPinLength = false
                if (length != settings.pinLength) {
                    pendingNewLength = length
                }
            }
        )
    }

    pendingNewLength?.let { length ->
        ChangePinDialog(
            repository = repository,
            settings = settings,
            requireCurrent = false,
            newLength = length,
            onDismiss = { pendingNewLength = null },
            onSaved = { pendingNewLength = null }
        )
    }

    if (showRelock) {
        ChoiceDialog(
            title = "Relock behavior",
            options = RelockBehavior.entries,
            selected = settings.relockBehavior,
            label = { it.label },
            onDismiss = { showRelock = false },
            onSelect = {
                repository.setRelockBehavior(it)
                showRelock = false
            }
        )
    }

    if (showTheme) {
        ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { it.label },
            onDismiss = { showTheme = false },
            onSelect = {
                repository.setThemeMode(it)
                showTheme = false
            }
        )
    }

    if (showDecoyPicker) {
        AppPickerDialog(
            title = "Decoy application",
            apps = decoyApps.filter { it.packageName !in settings.protectedApps },
            selected = setOfNotNull(settings.decoyPackage),
            multiSelect = false,
            allowNone = true,
            onDismiss = { showDecoyPicker = false },
            onSave = { selection ->
                repository.setDecoyPackage(selection.firstOrNull())
                showDecoyPicker = false
            }
        )
    }

    if (showBiometricUnavailable) {
        AlertDialog(
            onDismissRequest = { showBiometricUnavailable = false },
            title = { Text("Biometrics unavailable") },
            text = {
                Text(
                    "Set up a fingerprint or face unlock in your device settings, " +
                        "then try again."
                )
            },
            confirmButton = {
                TextButton(onClick = { showBiometricUnavailable = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showPrivacy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About App Lock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "App Lock",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Version $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "A private, on-device app lock with a customizable " +
                            "PIN keypad, biometric unlock, a fake PIN, and a decoy interface.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "No account, no ads, and no data collection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
        ) {
            Icon(
                imageVector = when (title) {
                    "Security" -> Icons.Filled.Security
                    "Appearance" -> Icons.Filled.Palette
                    else -> Icons.Filled.Lock
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

@Composable
private fun PermissionStatus(
    granted: Boolean,
    grantedLabel: String = "Granted",
    missingLabel: String = "Not granted"
) {
    Text(
        text = if (granted) grantedLabel else missingLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = if (granted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy policy") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Everything App Lock stores stays on this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Your PIN is saved only as a salted PBKDF2 hash. " +
                        "The plain PIN and keypad arrangements are never written to disk " +
                        "or included in logs.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Protected application choices, the decoy application, and " +
                        "appearance preferences are stored locally. App Lock has no network " +
                        "access, no analytics, no ads, and no third-party trackers.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Uninstalling the application removes all of its stored settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

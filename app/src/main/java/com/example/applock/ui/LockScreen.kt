package com.example.applock.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.applock.data.AppSettings
import com.example.applock.data.PinMatch
import com.example.applock.data.SettingsRepository
import com.example.applock.security.canUseBiometrics
import com.example.applock.security.showBiometricPrompt
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    settings: AppSettings,
    repository: SettingsRepository,
    sessionKey: Int,
    onUnlocked: () -> Unit,
    onFakePin: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val scope = rememberCoroutineScope()

    var entered by rememberSaveable(sessionKey) { mutableStateOf("") }
    var error by rememberSaveable(sessionKey) { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var biometricRequested by rememberSaveable(sessionKey) { mutableStateOf(false) }
    val keypadOrder = rememberSaveable(sessionKey, settings.scrambleKeypad) {
        ArrayList(buildKeypadOrder(settings.scrambleKeypad))
    }

    fun submit() {
        if (checking || entered.isEmpty()) return
        checking = true
        error = null
        scope.launch {
            when (repository.verifyPin(entered)) {
                PinMatch.REAL -> {
                    entered = ""
                    onUnlocked()
                }

                PinMatch.FAKE -> {
                    entered = ""
                    onFakePin()
                }

                PinMatch.NONE -> {
                    entered = ""
                    error = "Incorrect PIN. Try again."
                }
            }
            checking = false
        }
    }

    fun addDigit(digit: String) {
        if (checking) return
        if (entered.length < settings.pinLength) {
            entered += digit
            error = null
        }
    }

    fun deleteDigit() {
        if (entered.isNotEmpty()) {
            entered = entered.dropLast(1)
            error = null
        }
    }

    fun requestBiometrics() {
        if (activity != null && canUseBiometrics(activity)) {
            showBiometricPrompt(
                activity = activity,
                onSuccess = onUnlocked,
                onFailure = { error = "Biometric unlock cancelled. Use your PIN." }
            )
        } else {
            error = "Biometric authentication isn't available."
        }
    }

    LaunchedEffect(settings.biometricEnabled, settings.biometricAutoPrompt, sessionKey) {
        if (settings.biometricEnabled && settings.biometricAutoPrompt && !biometricRequested &&
            activity != null && canUseBiometrics(activity)
        ) {
            biometricRequested = true
            showBiometricPrompt(
                activity = activity,
                onSuccess = onUnlocked
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            val isLandscape = maxWidth > maxHeight
            val compact = maxHeight < 560.dp

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (settings.keypadLeftInLandscape) {
                        PinKeypad(
                            order = keypadOrder,
                            enabled = !checking,
                            onDigit = ::addDigit,
                            onDelete = ::deleteDigit,
                            onContinue = ::submit,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        LandscapeInfoColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            enteredCount = entered.length,
                            pinLength = settings.pinLength,
                            error = error,
                            enabled = !checking,
                            biometricEnabled = settings.biometricEnabled,
                            onBiometric = ::requestBiometrics
                        )
                    } else {
                        LandscapeInfoColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            enteredCount = entered.length,
                            pinLength = settings.pinLength,
                            error = error,
                            enabled = !checking,
                            biometricEnabled = settings.biometricEnabled,
                            onBiometric = ::requestBiometrics
                        )
                        PinKeypad(
                            order = keypadOrder,
                            enabled = !checking,
                            onDigit = ::addDigit,
                            onDelete = ::deleteDigit,
                            onContinue = ::submit,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            } else {
                // Keypad sits at the bottom of the screen for comfortable
                // thumb typing. On very short windows the content scrolls
                // instead of clipping, so every key stays reachable.
                val shortScreen = maxHeight < 520.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (shortScreen) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
                    LockHeader(compact = compact)
                    Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                    PinDots(enteredCount = entered.length, total = settings.pinLength)
                    Spacer(Modifier.height(10.dp))
                    ErrorText(error = error)
                    Spacer(
                        if (shortScreen) Modifier.height(16.dp) else Modifier.weight(1f)
                    )
                    PinKeypad(
                        order = keypadOrder,
                        enabled = !checking,
                        onDigit = ::addDigit,
                        onDelete = ::deleteDigit,
                        onContinue = ::submit,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
                    if (settings.biometricEnabled) {
                        BiometricButton(enabled = !checking, onClick = ::requestBiometrics)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun LandscapeInfoColumn(
    modifier: Modifier = Modifier,
    enteredCount: Int,
    pinLength: Int,
    error: String?,
    enabled: Boolean,
    biometricEnabled: Boolean,
    onBiometric: () -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LockHeader(compact = true)
        Spacer(Modifier.height(16.dp))
        PinDots(enteredCount = enteredCount, total = pinLength)
        Spacer(Modifier.height(8.dp))
        ErrorText(error = error)
        if (biometricEnabled) {
            Spacer(Modifier.height(8.dp))
            BiometricButton(enabled = enabled, onClick = onBiometric)
        }
    }
}

@Composable
private fun LockHeader(compact: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (compact) 48.dp else 64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (compact) 24.dp else 30.dp)
            )
        }

        Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
        Text(
            text = "Enter your PIN",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Unlock to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorText(error: String?) {
    // Fixed height so showing or clearing the message never reflows the
    // keypad, and the whole layout stays stable while typing.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = error ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BiometricButton(enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = Icons.Filled.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Use biometrics")
    }
}

@Composable
private fun PinDots(enteredCount: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.semantics {
            contentDescription = "$enteredCount of $total digits entered"
        }
    ) {
        repeat(total) { index ->
            val filled = index < enteredCount
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .then(
                        if (filled) Modifier
                        else Modifier.border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(
    order: List<String>,
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val spacing = 8.dp
        val keypadWidth = minOf(maxWidth, 340.dp)
        val availableHeight = if (maxHeight.value.isFinite()) maxHeight else 340.dp

        // Size keys from the smaller of the two axes so every row stays visible
        // in portrait, landscape, and on small displays.
        val byWidth = (keypadWidth - spacing * 2) / 3
        val byHeight = (availableHeight - spacing * 3) / 4
        val keyHeight = minOf(byWidth * 0.62f, byHeight).coerceIn(40.dp, 62.dp)
        val digitStyle = if (keyHeight < 50.dp) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.headlineSmall
        }

        Column(modifier = Modifier.width(keypadWidth)) {
            order.chunked(3).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    row.forEach { key ->
                        val isDelete = key == KEY_DELETE
                        val isContinue = key == KEY_CONTINUE
                        val label = when {
                            isDelete -> "Delete"
                            isContinue -> "Continue"
                            else -> key
                        }
                        val background = when {
                            isContinue -> MaterialTheme.colorScheme.primaryContainer
                            isDelete -> MaterialTheme.colorScheme.surfaceContainerHighest
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val foreground = when {
                            isContinue -> MaterialTheme.colorScheme.onPrimaryContainer
                            isDelete -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val contentAlpha = if (enabled) 1f else 0.45f

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(keyHeight)
                                .clip(RoundedCornerShape(22.dp))
                                .background(background)
                                .clickable(enabled = enabled) {
                                    when {
                                        isDelete -> onDelete()
                                        isContinue -> onContinue()
                                        else -> onDigit(key)
                                    }
                                }
                                .semantics {
                                    contentDescription = when {
                                        isDelete -> "Delete last digit"
                                        isContinue -> "Continue"
                                        else -> "Digit $key"
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = when {
                                    isDelete || isContinue -> MaterialTheme.typography.labelLarge
                                    else -> digitStyle
                                },
                                fontWeight = if (isDelete || isContinue) FontWeight.Medium
                                else FontWeight.Normal,
                                color = foreground.copy(alpha = contentAlpha),
                                maxLines = 1
                            )
                        }
                    }
                }
                if (rowIndex < 3) {
                    Spacer(Modifier.height(spacing))
                }
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

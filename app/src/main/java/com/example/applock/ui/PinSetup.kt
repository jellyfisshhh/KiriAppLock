package com.example.applock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.applock.data.AppSettings
import com.example.applock.data.PinMatch
import com.example.applock.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SetupPinScreen(
    repository: SettingsRepository,
    initialLength: Int,
    onComplete: () -> Unit
) {
    var length by rememberSaveable { mutableStateOf(initialLength) }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Create your PIN",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This PIN unlocks App Lock and your protected applications.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "PIN length",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in SettingsRepository.MIN_PIN_LENGTH..SettingsRepository.MAX_PIN_LENGTH) {
                        FilterChip(
                            selected = option == length,
                            onClick = {
                                length = option
                                pin = pin.take(option)
                                confirm = confirm.take(option)
                                error = null
                            },
                            label = { Text("$option") }
                        )
                    }
                }

                PinTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = "New PIN",
                    length = length
                )
                PinTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Confirm PIN",
                    length = length,
                    isError = error != null
                )

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (pin.length != length) {
                            error = "Enter a $length-digit PIN."
                        } else if (pin != confirm) {
                            error = "The PINs don't match."
                        } else {
                            saving = true
                            error = null
                            scope.launch {
                                withContext(Dispatchers.Default) { repository.setPin(pin, length) }
                                saving = false
                                onComplete()
                            }
                        }
                    },
                    enabled = !saving && pin.isNotEmpty() && confirm.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saving) "Saving…" else "Set PIN")
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                text = "Your PIN is stored as a salted hash on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    length: Int,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onValueChange(raw.filter { it.isDigit() }.take(length))
        },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ChangePinDialog(
    repository: SettingsRepository,
    settings: AppSettings,
    requireCurrent: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    newLength: Int = settings.pinLength
) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (requireCurrent) "Change PIN" else "Set new PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (requireCurrent) {
                    PinTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = "Current PIN",
                        length = settings.pinLength
                    )
                }
                PinTextField(
                    value = newPin,
                    onValueChange = { newPin = it },
                    label = "New $newLength-digit PIN",
                    length = newLength
                )
                PinTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Confirm new PIN",
                    length = newLength,
                    isError = error != null
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        if (requireCurrent && repository.verifyPin(current) != PinMatch.REAL) {
                            error = "Current PIN is incorrect."
                            saving = false
                        } else if (newPin.length != newLength) {
                            error = "Enter a $newLength-digit PIN."
                            saving = false
                        } else if (newPin != confirm) {
                            error = "The new PINs don't match."
                            saving = false
                        } else if (settings.fakePinEnabled &&
                            repository.verifyPin(newPin) == PinMatch.FAKE
                        ) {
                            error = "Choose a PIN different from your fake PIN."
                            saving = false
                        } else {
                            withContext(Dispatchers.Default) {
                                repository.setPin(newPin, newLength)
                            }
                            saving = false
                            onSaved()
                        }
                    }
                }
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FakePinDialog(
    repository: SettingsRepository,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var enabled by remember { mutableStateOf(settings.fakePinEnabled) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Fake PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable fake PIN",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Opens the decoy interface instead of your real apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it; error = null })
                }

                if (enabled) {
                    PinTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = "Fake ${settings.pinLength}-digit PIN",
                        length = settings.pinLength
                    )
                    PinTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = "Confirm fake PIN",
                        length = settings.pinLength,
                        isError = error != null
                    )
                }

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    if (!enabled) {
                        repository.setFakePin(false, null)
                        onSaved()
                        return@TextButton
                    }
                    scope.launch {
                        saving = true
                        error = null
                        when {
                            pin.length != settings.pinLength -> {
                                error = "Enter a ${settings.pinLength}-digit fake PIN."
                                saving = false
                            }

                            pin != confirm -> {
                                error = "The fake PINs don't match."
                                saving = false
                            }

                            repository.verifyPin(pin) == PinMatch.REAL -> {
                                error = "The fake PIN must be different from your real PIN."
                                saving = false
                            }

                            else -> {
                                withContext(Dispatchers.Default) {
                                    repository.setFakePin(true, pin)
                                }
                                saving = false
                                onSaved()
                            }
                        }
                    }
                }
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel")
            }
        }
    )
}

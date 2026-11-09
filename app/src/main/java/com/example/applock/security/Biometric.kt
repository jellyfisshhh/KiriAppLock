package com.example.applock.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun canUseBiometrics(context: Context): Boolean {
    return BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS
}

fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onFailure: () -> Unit = {}
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure()
            }

            override fun onAuthenticationFailed() {
                // Keep the prompt open so the user can try again.
            }
        }
    )

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock App Lock")
        .setSubtitle("Confirm your identity to continue")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .setNegativeButtonText("Use PIN")
        .build()

    prompt.authenticate(info)
}

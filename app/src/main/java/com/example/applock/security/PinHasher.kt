package com.example.applock.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores only a salted, stretched hash of a PIN. PINs and salts are never logged.
 */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            Base64.decode(salt, Base64.NO_WRAP),
            ITERATIONS,
            KEY_LENGTH_BITS
        )
        return try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            Base64.encodeToString(key, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean {
        val actual = hash(pin, salt)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }
}

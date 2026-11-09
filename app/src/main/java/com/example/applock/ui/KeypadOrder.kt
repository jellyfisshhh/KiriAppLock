package com.example.applock.ui

import java.security.SecureRandom
import java.util.Collections

const val KEY_DELETE = "DELETE"
const val KEY_CONTINUE = "CONTINUE"

private val secureRandom = SecureRandom()

/**
 * Builds the 12-cell keypad layout. Delete always stays in the bottom-left cell and
 * Continue always stays in the bottom-right cell. When scrambling is enabled all ten
 * digits are placed in a fresh permutation using SecureRandom.
 */
fun buildKeypadOrder(scramble: Boolean): List<String> {
    if (!scramble) {
        return listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", KEY_DELETE, "0", KEY_CONTINUE)
    }

    val digits = "0123456789".toMutableList()
    Collections.shuffle(digits, secureRandom)

    return MutableList(12) { "" }.apply {
        for (index in 0..8) {
            this[index] = digits[index].toString()
        }
        this[9] = KEY_DELETE
        this[10] = digits[9].toString()
        this[11] = KEY_CONTINUE
    }
}

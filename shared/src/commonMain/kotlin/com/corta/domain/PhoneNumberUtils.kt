package com.corta.domain

/**
 * Utilities to normalize phone numbers into a consistent format for matching rules.
 *
 * For now this focuses on Chilean numbers and normalizes to something close to E.164.
 * Examples that should end up equivalent:
 *  - +56912345678
 *  - 56912345678
 *  - 09 1234 5678
 *  - 9-1234-5678
 */
object PhoneNumberUtils {

    fun normalize(raw: String): String {
        if (raw.isBlank()) return raw

        // Trim and keep leading + if present, remove spaces and separators
        val trimmed = raw.trim()

        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = buildString {
            for (ch in trimmed) {
                if (ch.isDigit()) {
                    append(ch)
                }
            }
        }

        if (digitsOnly.isEmpty()) {
            return raw.trim()
        }

        // Basic heuristic for Chile:
        // - If starts with "56" -> assume already in international format without plus
        // - If starts with "0" -> drop leading zero
        // - If starts with "9" and length == 9 -> assume mobile, prefix country code
        val normalizedDigits = when {
            digitsOnly.startsWith("56") -> digitsOnly
            digitsOnly.startsWith("0") && digitsOnly.length > 1 -> digitsOnly.drop(1)
            digitsOnly.length == 9 && digitsOnly.startsWith("9") -> "56$digitsOnly"
            else -> digitsOnly
        }

        // Ensure we always return either a plain digit string or +E164-like format
        return when {
            hasPlus -> "+$normalizedDigits"
            normalizedDigits.startsWith("56") -> "+$normalizedDigits"
            else -> normalizedDigits
        }
    }
}


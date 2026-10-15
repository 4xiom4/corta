package com.corta.domain

/**
 * Utilities to normalize phone numbers into a consistent format for matching rules.
 *
 * Chilean symmetric normalization (+56):
 *  - Mobile (+56 9 XXXX XXXX): 9 digits starting with 9
 *  - Fixed line Santiago (+56 2 XXXX XXXX): 9 digits starting with 2
 *  - Regional fixed line (+56 3X/4X/5X/6X...): 9 digits
 *  - SUBTEL massive outbound telemarketing (+56 809 XXXXXX): 9 digits
 *  - Commercial special services (+56 600 XXX XXXX): 9 to 10 digits
 *  - Emergency & municipal security lines: 3 to 4 digits (130-149, 14xx, 4141)
 */
object PhoneNumberUtils {

    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""

        val trimmed = raw.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }

        if (digitsOnly.isEmpty()) return trimmed

        // Short emergency / municipal numbers (130-149, 14xx, 4141, etc.)
        if (digitsOnly.length in 3..4) {
            return digitsOnly
        }

        // Strip leading trunk zero (e.g. 09 1234 5678 -> 912345678)
        val stripped = if (digitsOnly.startsWith("0") && digitsOnly.length >= 9) {
            digitsOnly.drop(1)
        } else {
            digitsOnly
        }

        val hasPlus = trimmed.startsWith("+")

        // 600, 800 and 809 Chilean service numbers without '+' are kept in local national format
        if (!hasPlus && (stripped.startsWith("600") || stripped.startsWith("809") || stripped.startsWith("800"))) {
            return stripped
        }

        // Chilean numbers handling
        if (stripped.startsWith("56") && stripped.length in 11..12) {
            return "+$stripped"
        }

        // 9-digit Chilean national numbers (mobile, fixed)
        if (stripped.length == 9) {
            return "+56$stripped"
        }

        // 10-digit 600 services
        if (stripped.length == 10 && stripped.startsWith("600")) {
            return "+56$stripped"
        }

        // International numbers with explicit '+'
        if (trimmed.startsWith("+")) {
            return "+$stripped"
        }

        return stripped
    }

    /**
     * Extracts Chilean national digits (without +56 or leading 0).
     * For example:
     *  "+56809005687" -> "809005687"
     *  "+56912345678" -> "912345678"
     *  "809005687"    -> "809005687"
     */
    fun toChileanNational(raw: String): String? {
        val digitsOnly = raw.filter { it.isDigit() }
        val stripped = if (digitsOnly.startsWith("0") && digitsOnly.length >= 9) {
            digitsOnly.drop(1)
        } else {
            digitsOnly
        }

        return when {
            stripped.startsWith("56") && stripped.length in 11..12 -> stripped.drop(2)
            stripped.length in 9..10 -> stripped
            else -> null
        }
    }

    /**
     * Returns all symmetric variants of a number to guarantee infallible matching:
     * - Canonical E.164 (e.g. "+56809005687")
     * - Chilean national digits (e.g. "809005687")
     * - Digits with country code (e.g. "56809005687")
     * - Raw trimmed input
     */
    fun getSymmetricCandidates(raw: String): List<String> {
        val trimmed = raw.trim()
        val normalized = normalize(trimmed)
        val national = toChileanNational(trimmed)
        val digits = trimmed.filter { it.isDigit() }

        return buildList {
            if (normalized.isNotBlank()) add(normalized)
            if (national != null && national.isNotBlank()) add(national)
            if (digits.isNotBlank()) add(digits)
            if (trimmed.isNotBlank() && trimmed !in this) add(trimmed)
        }.distinct()
    }

    /**
     * Formats a phone number for clear visual display:
     * - Chilean Mobile: "+56 9 1234 5678"
     * - Fixed Santiago: "+56 2 2345 6789"
     * - Fixed Regional: "+56 41 234 5678"
     * - Special 600 / 800: "600 360 7777" / "800 123 456"
     * - SUBTEL 809: "809 005 687"
     * - Alphanumeric / Names: kept as is
     */
    fun formatDisplayNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.any { it.isLetter() }) return trimmed

        val digits = trimmed.filter { it.isDigit() }
        val national = toChileanNational(trimmed) ?: digits

        if (national.length == 9) {
            return if (national.startsWith("9")) {
                // Mobile: +56 9 XXXX XXXX
                if (trimmed.startsWith("+56") || trimmed.startsWith("+")) {
                    "+56 9 ${national.substring(1, 5)} ${national.substring(5)}"
                } else {
                    "9 ${national.substring(1, 5)} ${national.substring(5)}"
                }
            } else if (national.startsWith("2")) {
                // Santiago: +56 2 XXXX XXXX
                if (trimmed.startsWith("+56") || trimmed.startsWith("+")) {
                    "+56 2 ${national.substring(1, 5)} ${national.substring(5)}"
                } else {
                    "2 ${national.substring(1, 5)} ${national.substring(5)}"
                }
            } else if (national.startsWith("809") || national.startsWith("800")) {
                "${national.substring(0, 3)} ${national.substring(3, 6)} ${national.substring(6)}"
            } else {
                // Regional fixed (2-digit area code)
                if (trimmed.startsWith("+56") || trimmed.startsWith("+")) {
                    "+56 ${national.substring(0, 2)} ${national.substring(2, 5)} ${national.substring(5)}"
                } else {
                    "${national.substring(0, 2)} ${national.substring(2, 5)} ${national.substring(5)}"
                }
            }
        } else if (national.length == 10 && national.startsWith("600")) {
            return "600 ${national.substring(3, 6)} ${national.substring(6)}"
        }

        return trimmed
    }
}

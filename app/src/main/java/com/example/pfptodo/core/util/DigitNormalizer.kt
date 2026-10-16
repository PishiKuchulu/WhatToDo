package com.example.pfptodo.core.util

/**
 * Normalizes Eastern Arabic (Persian: ۰-۹) and Arabic-Indic (٠-٩) digits to standard ASCII (0-9).
 */
object DigitNormalizer {

    /**
     * Replaces all Persian and Arabic digits with standard ASCII 0-9.
     */
    fun toAsciiDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (char in input) {
            when (char) {
                // Persian / Eastern Arabic digits (۰-۹: U+06F0 - U+06F9)
                in '\u06F0'..'\u06F9' -> sb.append((char - '\u06F0' + '0'.code).toChar())
                // Arabic-Indic digits (٠-٩: U+0660 - U+0669)
                in '\u0660'..'\u0669' -> sb.append((char - '\u0660' + '0'.code).toChar())
                else -> sb.append(char)
            }
        }
        return sb.toString()
    }
}

package com.example.pfptodo.core.util

/**
 * Normalizes Arabic letter variants and Zero-Width Non-Joiners to their standard
 * Persian equivalents so search and NLP parsing match consistently across all keyboards:
 *  - Arabic ye (ي) → Persian ye (ی)
 *  - Arabic kaf (ك) → Persian kaf (ک)
 *  - Arabic alef variants (أ إ آ) → plain alef (ا)
 *  - Arabic te marbuta (ة) → heh (ه)
 *  - Zero-Width Non-Joiner (\u200C) / Zero-Width Space (\u200B) → standard space
 */
object PersianNormalizer {
    fun normalize(input: String): String {
        return input
            .replace('ي', 'ی') // ي → ی
            .replace('ك', 'ک') // ك → ک
            .replace('أ', 'ا') // أ → ا
            .replace('إ', 'ا') // إ → ا
            .replace('آ', 'ا') // آ → ا
            .replace('ة', 'ه') // ة → ه
            .replace('\u200C', ' ') // ZWNJ (نیم‌فاصله) → space
            .replace('\u200B', ' ') // Zero-width space → space
            .replace('\u200E', ' ') // LTR mark → space
            .replace('\u200F', ' ') // RTL mark → space
    }
}
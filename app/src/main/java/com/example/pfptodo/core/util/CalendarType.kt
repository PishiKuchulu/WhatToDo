package com.example.pfptodo.core.util

import java.util.Locale

/** Which calendar the user wants dates displayed (and entered) in. Stored data is never affected. */
enum class CalendarType {
    GREGORIAN,
    JALALI;

    companion object {
        /** Persian-language users get the Solar Hijri calendar by default. */
        fun defaultFor(locale: Locale): CalendarType =
            if (locale.language.equals("fa", ignoreCase = true)) JALALI else GREGORIAN

        fun fromName(name: String?): CalendarType? = entries.firstOrNull { it.name == name }
    }
}

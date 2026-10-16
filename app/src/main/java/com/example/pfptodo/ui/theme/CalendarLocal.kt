package com.example.pfptodo.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.example.pfptodo.core.util.CalendarType

/** The calendar the whole UI should render dates in; provided once in MainActivity. */
val LocalCalendarType = compositionLocalOf { CalendarType.GREGORIAN }

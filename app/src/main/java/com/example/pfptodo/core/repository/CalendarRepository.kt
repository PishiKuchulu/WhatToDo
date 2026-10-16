package com.example.pfptodo.core.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pfptodo.core.util.CalendarType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale

private val Context.calendarDataStore: DataStore<Preferences> by preferencesDataStore(name = "calendar_settings")

/** Persists the user's calendar choice (Gregorian / Jalali). Unset = follow the app language. */
class CalendarRepository(private val context: Context) {
    private val key = stringPreferencesKey("calendar_type")

    val calendarTypeFlow: Flow<CalendarType> = context.calendarDataStore.data
        // A corrupt/missing preferences file throws IOException from dataStore.data.
        // Emit empty prefs so collectors get the same locale-derived default the map
        // below would produce, instead of crashing.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> CalendarType.fromName(prefs[key]) ?: CalendarType.defaultFor(Locale.getDefault()) }
        .distinctUntilChanged()

    suspend fun setCalendarType(type: CalendarType) {
        context.calendarDataStore.edit { it[key] = type.name }
    }
}

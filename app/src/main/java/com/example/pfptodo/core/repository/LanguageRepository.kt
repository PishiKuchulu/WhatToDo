package com.example.pfptodo.core.repository

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(name = "language_settings")

class LanguageRepository(private val context: Context) {
    private val languageKey = stringPreferencesKey("app_language")

    val languageFlow: Flow<String> = context.languageDataStore.data
        // A corrupt/missing preferences file throws IOException from dataStore.data.
        // Emit empty prefs so collectors get the same "system" default the map below
        // would produce, instead of crashing.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { preferences ->
            preferences[languageKey] ?: "system"
        }

    suspend fun setLanguage(languageTag: String) {
        context.languageDataStore.edit { preferences ->
            preferences[languageKey] = languageTag
        }
        applyLocale(languageTag)
    }

    fun applyLocale(languageTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (languageTag == "system" || languageTag.isBlank()) {
                localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
            } else {
                localeManager?.applicationLocales = LocaleList.forLanguageTags(languageTag)
            }
        } else {
            val localeList = if (languageTag == "system" || languageTag.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }
}

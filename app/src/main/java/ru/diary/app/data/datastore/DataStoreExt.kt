package ru.diary.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val USER_PREFERENCES = "user_prefs"

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = USER_PREFERENCES)
package ru.diary.app.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object PrefKeys {
    val PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_accepted")
    val PRIVACY_VERSION_ACCEPTED = intPreferencesKey("privacy_version_accepted")
}

class UserPrefs(private val dataStore: DataStore<Preferences>) {
    val privacyAccepted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PrefKeys.PRIVACY_ACCEPTED] ?: false
    }

    val privacyVersionAccepted: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PrefKeys.PRIVACY_VERSION_ACCEPTED] ?: 0
    }

    suspend fun setPrivacyAccepted(accepted: Boolean, version: Int) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.PRIVACY_ACCEPTED] = accepted
            prefs[PrefKeys.PRIVACY_VERSION_ACCEPTED] = version
        }
    }
}
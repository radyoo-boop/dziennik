package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "drive_settings")

data class DrivePreferences(
    val rootFolderName: String = DEFAULT_ROOT_FOLDER,
    val optimizePhotoSize: Boolean = true,
    val connectedAccountEmail: String? = null
) {
    companion object {
        const val DEFAULT_ROOT_FOLDER = "Dziennik Zdarzeń"
    }
}

class SettingsDataStore(private val context: Context) {
    private val rootFolderKey = stringPreferencesKey("root_folder_name")
    private val optimizePhotoKey = booleanPreferencesKey("optimize_photo_size")
    private val connectedEmailKey = stringPreferencesKey("connected_account_email")

    val preferencesFlow: Flow<DrivePreferences> = context.dataStore.data.map { prefs ->
        DrivePreferences(
            rootFolderName = prefs[rootFolderKey]?.takeIf { it.isNotBlank() }
                ?: DrivePreferences.DEFAULT_ROOT_FOLDER,
            optimizePhotoSize = prefs[optimizePhotoKey] ?: true,
            connectedAccountEmail = prefs[connectedEmailKey]
        )
    }

    suspend fun updateRootFolderName(name: String) {
        val sanitized = name.trim().ifBlank { DrivePreferences.DEFAULT_ROOT_FOLDER }
        context.dataStore.edit { prefs ->
            prefs[rootFolderKey] = sanitized
        }
    }

    suspend fun updateOptimizePhotoSize(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[optimizePhotoKey] = enabled
        }
    }

    suspend fun updateConnectedAccountEmail(email: String?) {
        context.dataStore.edit { prefs ->
            if (email.isNullOrBlank()) {
                prefs.remove(connectedEmailKey)
            } else {
                prefs[connectedEmailKey] = email
            }
        }
    }
}

package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.familyGuardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "family_guard_prefs"
)

class PreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.familyGuardDataStore

    private val globalRoleKey = stringPreferencesKey("selected_device_role")
    private fun pairingCodeKey(uid: String) = stringPreferencesKey("child_pairing_code_$uid")

    fun observeDeviceRole(): Flow<DeviceRole> =
        dataStore.data.map { prefs -> DeviceRole.fromKey(prefs[globalRoleKey]) }

    suspend fun setDeviceRole(role: DeviceRole) {
        dataStore.edit { prefs ->
            prefs[globalRoleKey] = role.storageKey
        }
    }

    fun observeSavedPairingCode(uid: String): Flow<String?> =
        dataStore.data.map { prefs -> prefs[pairingCodeKey(uid)] }

    suspend fun setSavedPairingCode(uid: String, code: String?) {
        dataStore.edit { prefs ->
            if (code == null) {
                prefs.remove(pairingCodeKey(uid))
            } else {
                prefs[pairingCodeKey(uid)] = code
            }
        }
    }
}

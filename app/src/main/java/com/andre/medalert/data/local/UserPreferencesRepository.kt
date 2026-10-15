package com.andre.medalert.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Deve ficar no nível do arquivo (uma única instância por processo).
private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.userDataStore

    private object Keys {
        val USER_ID = intPreferencesKey("user_id")
        val TOKEN = stringPreferencesKey("token")
    }

    /** null = nenhum usuário logado. */
    val userId: Flow<Int?> = dataStore.data.map { it[Keys.USER_ID] }

    /** null = sem token (o backend pode não enviar). */
    val token: Flow<String?> = dataStore.data.map { it[Keys.TOKEN] }

    val isLoggedIn: Flow<Boolean> = userId.map { it != null }

    suspend fun saveSession(userId: Int, token: String?) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = userId
            if (token != null) {
                prefs[Keys.TOKEN] = token
            } else {
                prefs.remove(Keys.TOKEN)
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
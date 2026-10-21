package com.andre.medalert.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extensão para criar o DataStore
private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val USER_ID = intPreferencesKey("user_id")
        val USER_TOKEN = stringPreferencesKey("user_token")
    }

    // Salva o ID do usuário após o login
    suspend fun saveUserSession(userId: Int, token: String?) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID] = userId
            preferences[USER_TOKEN] = token ?: ""
        }
    }

    // Lê o ID do usuário (retorna -1 se não houver ninguém logado)
    val userId: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[USER_ID] ?: -1
    }

    // Limpa os dados (Logout)
    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
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
// ATENÇÃO: não declare outro preferencesDataStore(name = "user_prefs") em nenhum outro arquivo
// (apague o stub de data/repository), senão o app quebra com "multiple DataStores active".
private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.userDataStore

    private object Keys {
        val USER_ID = intPreferencesKey("user_id")
        val TOKEN = stringPreferencesKey("token")
        val USER_TYPE = stringPreferencesKey("user_type")
        val PACIENTE_ID = intPreferencesKey("paciente_id")
        val USER_NAME = stringPreferencesKey("user_name")
    }

    /** ID real devolvido pelo login. null = nenhum usuário logado. */
    val userId: Flow<Int?> = dataStore.data.map { it[Keys.USER_ID] }

    /** null = sem token (o backend pode não enviar). */
    val token: Flow<String?> = dataStore.data.map { it[Keys.TOKEN] }

    /** "paciente" ou "cuidador". Sessões antigas (sem tipo) são tratadas como paciente. */
    val userType: Flow<String> = dataStore.data.map { it[Keys.USER_TYPE] ?: "paciente" }

    /** Só para cuidador: ID do paciente acompanhado. */
    val pacienteId: Flow<Int?> = dataStore.data.map { it[Keys.PACIENTE_ID] }

    /** Nome do usuário, quando o login o devolve. null = desconhecido (nunca inventado). */
    val userName: Flow<String?> = dataStore.data.map { it[Keys.USER_NAME]?.takeIf { n -> n.isNotBlank() } }

    val isLoggedIn: Flow<Boolean> = userId.map { it != null }

    suspend fun saveSession(
        userId: Int,
        token: String?,
        tipo: String? = null,
        pacienteId: Int? = null,
        nome: String? = null
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = userId
            if (token != null) prefs[Keys.TOKEN] = token else prefs.remove(Keys.TOKEN)
            if (tipo != null) prefs[Keys.USER_TYPE] = tipo else prefs.remove(Keys.USER_TYPE)
            if (pacienteId != null) prefs[Keys.PACIENTE_ID] = pacienteId else prefs.remove(Keys.PACIENTE_ID)
            if (nome != null) prefs[Keys.USER_NAME] = nome else prefs.remove(Keys.USER_NAME)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
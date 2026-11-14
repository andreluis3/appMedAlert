package com.andre.medalert.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.andre.medalert.data.model.PendingInvite
import com.andre.medalert.data.model.PendingInviteCodec
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.data.session.LinkedPatientProfile
import com.andre.medalert.data.session.SessionInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.userDataStore

    private object Keys {
        val USER_ID = intPreferencesKey("user_id")
        val TOKEN = stringPreferencesKey("token")
        val USER_TYPE = stringPreferencesKey("user_type")
        val PACIENTE_ID = intPreferencesKey("paciente_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val FCM_REGISTERED = stringPreferencesKey("fcm_registered")
        val PENDING_INVITES = stringPreferencesKey("pending_invites")
        val LINKED_PATIENT_ID = intPreferencesKey("linked_patient_id")
        val LINKED_PATIENT_NAME = stringPreferencesKey("linked_patient_name")
    }

    val userId: Flow<Int?> = dataStore.data.map { it[Keys.USER_ID] }

    val token: Flow<String?> = dataStore.data.map { it[Keys.TOKEN] }

    val userType: Flow<String> =
        dataStore.data.map { it[Keys.USER_TYPE] ?: "paciente" }

    val pacienteId: Flow<Int?> = dataStore.data.map { it[Keys.PACIENTE_ID] }

    val userName: Flow<String?> =
        dataStore.data.map {
            it[Keys.USER_NAME]?.takeIf { nome -> nome.isNotBlank() }
        }

    val userEmail: Flow<String?> =
        dataStore.data.map {
            it[Keys.USER_EMAIL]?.takeIf { email -> email.isNotBlank() }
        }

    val isLoggedIn: Flow<Boolean> =
        userId.map { it != null }

    val session: Flow<SessionInfo> =
        dataStore.data.map {
            SessionInfo(
                userId = it[Keys.USER_ID],
                tipo = it[Keys.USER_TYPE] ?: "paciente",
                pacienteId = it[Keys.PACIENTE_ID]
            )
        }

    /**
     * Nome do paciente vinculado ao cuidador (cache local). Só vale junto com o id a que pertence:
     * quem lê deve comparar com o vínculo atual (ver PatientContext.from).
     */
    val linkedPatientProfile: Flow<LinkedPatientProfile?> =
        dataStore.data.map {
            val id = it[Keys.LINKED_PATIENT_ID]
            val name = it[Keys.LINKED_PATIENT_NAME]?.takeIf { nome -> nome.isNotBlank() }
            if (id != null && id > 0 && name != null) LinkedPatientProfile(id, name) else null
        }

    /**
     * Guarda o nome do paciente acompanhado. Só grava se a sessão é de cuidador, o [patientId] é
     * o paciente vinculado agora e o nome não está em branco. Não mexe na sessão nem no vínculo.
     * Some no logout ([clear]).
     */
    suspend fun saveLinkedPatientName(patientId: Int, name: String): Boolean {
        val clean = name.trim()
        if (patientId <= 0 || clean.isEmpty()) return false
        var saved = false
        dataStore.edit { prefs ->
            val caregiverSession = (prefs[Keys.USER_ID] ?: 0) > 0 &&
                    AccessPolicy.isCaregiver(prefs[Keys.USER_TYPE])
            if (!caregiverSession || prefs[Keys.PACIENTE_ID] != patientId) return@edit
            prefs[Keys.LINKED_PATIENT_ID] = patientId
            prefs[Keys.LINKED_PATIENT_NAME] = clean
            saved = true
        }
        return saved
    }

    suspend fun saveSession(
        userId: Int,
        token: String?,
        tipo: String? = null,
        pacienteId: Int? = null,
        nome: String? = null,
        email: String? = null
    ) {
        dataStore.edit { prefs ->

            prefs[Keys.USER_ID] = userId

            if (token != null) {
                prefs[Keys.TOKEN] = token
            } else {
                prefs.remove(Keys.TOKEN)
            }

            if (tipo != null) {
                prefs[Keys.USER_TYPE] = tipo
            } else {
                prefs.remove(Keys.USER_TYPE)
            }

            if (pacienteId != null) {
                prefs[Keys.PACIENTE_ID] = pacienteId
            } else {
                prefs.remove(Keys.PACIENTE_ID)
            }

            if (nome != null) {
                prefs[Keys.USER_NAME] = nome
            } else {
                prefs.remove(Keys.USER_NAME)
            }

            if (email != null) {
                prefs[Keys.USER_EMAIL] = email
            } else {
                prefs.remove(Keys.USER_EMAIL)
            }
        }
    }

    /** "<cuidadorId>:<token>" já enviado à API; evita reenviar o mesmo token. Some no logout (clear). */
    val fcmRegistered: Flow<String?> = dataStore.data.map { it[Keys.FCM_REGISTERED] }

    suspend fun saveFcmRegistered(marker: String) {
        dataStore.edit { prefs -> prefs[Keys.FCM_REGISTERED] = marker }
    }

    /**
     * Convites de vínculo recebidos pelo cuidador e ainda não tratados (mais recente primeiro).
     * Chave própria: NÃO mexe na sessão ([saveSession] não toca nela) e some no logout ([clear]).
     */
    val pendingInvites: Flow<List<PendingInvite>> =
        dataStore.data.map { PendingInviteCodec.decode(it[Keys.PENDING_INVITES]) }

    /** Guarda o convite sem duplicar o mesmo paciente. Devolve true se era novo. */
    suspend fun addPendingInvite(pacienteId: Int, receivedAtMillis: Long): Boolean {
        var inserted = false
        dataStore.edit { prefs ->
            val current = PendingInviteCodec.decode(prefs[Keys.PENDING_INVITES])
            if (current.none { it.pacienteId == pacienteId }) {
                inserted = true
                prefs[Keys.PENDING_INVITES] = PendingInviteCodec.encode(
                    PendingInviteCodec.add(current, PendingInvite(pacienteId, receivedAtMillis))
                )
            }
        }
        return inserted
    }

    /** Remove o convite do paciente (para a etapa do aceite/recusa). */
    suspend fun removePendingInvite(pacienteId: Int) {
        dataStore.edit { prefs ->
            val remaining = PendingInviteCodec.remove(
                PendingInviteCodec.decode(prefs[Keys.PENDING_INVITES]),
                pacienteId
            )
            if (remaining.isEmpty()) {
                prefs.remove(Keys.PENDING_INVITES)
            } else {
                prefs[Keys.PENDING_INVITES] = PendingInviteCodec.encode(remaining)
            }
        }
    }

    /**
     * Vínculo do CUIDADOR com o paciente (depois que a API confirmou o aceite).
     *
     * Grava SÓ o `paciente_id`: não toca em user_id, token, user_type, user_name, user_email nem
     * no marcador do FCM, e não faz logout. Na MESMA transação remove o convite pendente desse
     * paciente, então nunca existe um estado intermediário (vinculado, mas com o convite ainda lá).
     *
     * Devolve false, sem gravar nada, se o id for inválido ou a sessão não for de cuidador.
     * Quem observa a sessão (Home, Medicamentos, SessionLifecycle) reage sozinho à mudança.
     */
    suspend fun updateLinkedPatientId(pacienteId: Int): Boolean {
        if (pacienteId <= 0) return false
        var updated = false
        dataStore.edit { prefs ->
            val caregiverSession = (prefs[Keys.USER_ID] ?: 0) > 0 &&
                    AccessPolicy.isCaregiver(prefs[Keys.USER_TYPE])
            if (!caregiverSession) return@edit

            prefs[Keys.PACIENTE_ID] = pacienteId

            val remaining = PendingInviteCodec.remove(
                PendingInviteCodec.decode(prefs[Keys.PENDING_INVITES]),
                pacienteId
            )
            if (remaining.isEmpty()) {
                prefs.remove(Keys.PENDING_INVITES)
            } else {
                prefs[Keys.PENDING_INVITES] = PendingInviteCodec.encode(remaining)
            }
            updated = true
        }
        return updated
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
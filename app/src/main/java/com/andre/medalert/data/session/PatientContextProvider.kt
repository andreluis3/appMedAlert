package com.andre.medalert.data.session

import com.andre.medalert.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * Observa o DataStore e entrega o [PatientContext] sempre atualizado: login, logout, sessão
 * expirada e aceite de vínculo (que grava o paciente_id) refletem aqui sem recriar telas nem ViewModels.
 *
 * Não usa nenhum endpoint. Quando o backend disponibilizar o perfil do paciente, quem buscar o
 * nome só precisa chamar [savePatientName]; todas as telas que observam [context] atualizam sozinhas.
 */
class PatientContextProvider(private val prefs: UserPreferencesRepository) {

    val context: Flow<PatientContext> = combine(
        prefs.session,
        prefs.userName,
        prefs.linkedPatientProfile
    ) { session, ownName, cachedPatient ->
        PatientContext.from(session, ownName, cachedPatient)
    }.distinctUntilChanged()

    suspend fun current(): PatientContext = context.first()

    /**
     * Guarda o nome do paciente vinculado ao cuidador. Devolve false (sem gravar) se o nome está
     * em branco, se a sessão não é de cuidador ou se [patientId] não é o paciente vinculado agora.
     */
    suspend fun savePatientName(patientId: Int, name: String): Boolean =
        prefs.saveLinkedPatientName(patientId, name)
}
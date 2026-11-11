package com.andre.medalert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.session.AccessPolicy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Dados do Perfil, todos lidos do DataStore.
 *
 * [nome] é null quando a sessão não tem nome: o login da API devolve só id, tipo e pacienteId
 * (sem nome), então o app só conhece o nome se a API passar a enviá-lo. Nada é inventado.
 */
data class ProfileUiState(
    val nome: String? = null,
    val email: String = "",
    val tipoUsuario: String = AccessPolicy.TIPO_PACIENTE,
    /** Cuidador com vínculo ativo: ID do paciente acompanhado. Null para paciente ou sem vínculo. */
    val pacienteVinculadoId: Int? = null,
    val isLoading: Boolean = true
) {
    val isCaregiver: Boolean get() = AccessPolicy.isCaregiver(tipoUsuario)
}

class ProfileViewModel(
    repository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> =
        combine(
            repository.userName,
            repository.userEmail,
            repository.userType,
            repository.pacienteId
        ) { nome, email, tipo, pacienteId ->

            ProfileUiState(
                nome = nome,
                email = email ?: "",
                tipoUsuario = tipo,
                pacienteVinculadoId =
                    if (AccessPolicy.canViewLinkedPatient(tipo, pacienteId)) pacienteId else null,
                isLoading = false
            )

        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProfileUiState()
        )
}
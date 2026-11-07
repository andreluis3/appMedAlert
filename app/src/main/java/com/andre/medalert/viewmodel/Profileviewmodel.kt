package com.andre.medalert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProfileUiState(
    val nome: String = "Usuário",
    val email: String = "",
    val tipoUsuario: String = "paciente",
    val isLoading: Boolean = true
)

class ProfileViewModel(
    repository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> =
        combine(
            repository.userName,
            repository.userEmail,
            repository.userType
        ) { nome, email, tipo ->

            ProfileUiState(
                nome = nome ?: "Usuário",
                email = email ?: "",
                tipoUsuario = tipo,
                isLoading = false
            )

        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProfileUiState()
        )
}
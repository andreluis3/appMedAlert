package com.andre.medalert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andre.medalert.data.remote.dto.ChangePasswordRequest
import com.andre.medalert.data.local.UserPreferencesRepository // Ajustado conforme seu UserRepository
import com.andre.medalert.data.repository.UserRepository
import com.andre.medalert.state.ChangePasswordUiState
import com.andre.medalert.data.remote.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChangePasswordViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun changePassword(
        senhaAtual: String,
        novaSenha: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            runCatching {
                // No futuro, busque o ID real do DataStore antes de chamar aqui
                userRepository.changePassword(
                    ChangePasswordRequest(
                        userId = 0,
                        senhaAtual = senhaAtual,
                        novaSenha = novaSenha
                    )
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false)
                onSuccess()
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false)
                onError()
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val context = this[APPLICATION_KEY]!!

                // 1. Instancia o repositório de preferências
                val prefs = UserPreferencesRepository(context)

                // 2. Obtém a instância da API (ajustado para RetrofitClient.instance conforme seu repo)
                val apiService = RetrofitClient.instance

                // 3. Instancia o UserRepository com os nomes de parâmetros corretos
                val repository = UserRepository(
                    api = apiService,
                    userPreferences = prefs
                )

                ChangePasswordViewModel(repository)
            }
        }
    }
}
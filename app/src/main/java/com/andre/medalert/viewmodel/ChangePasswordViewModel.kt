package com.andre.medalert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.remote.dto.ChangePasswordRequest
import com.andre.medalert.data.repository.UserRepository
import com.andre.medalert.state.ChangePasswordUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Segue o padrão Screen -> ViewModel -> Repository -> ApiService.
 *
 * IMPORTANTE: como o endpoint de troca de senha ainda não existe no backend
 * (ver TODO em UserRepository.changePassword), a chamada abaixo sempre
 * resulta em falha por enquanto — isso é esperado e serve pra validar o
 * fluxo de erro/loading da tela até a API estar pronta.
 */
class ChangePasswordViewModel(
    private val userRepository: UserRepository = UserRepository()
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
                userRepository.changePassword(
                    ChangePasswordRequest(senhaAtual = senhaAtual, novaSenha = novaSenha)
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
}

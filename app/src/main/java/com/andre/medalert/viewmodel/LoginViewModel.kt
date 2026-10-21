package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.model.User
import com.andre.medalert.data.repository.InvalidCredentialsException
import com.andre.medalert.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val user: User) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = UserRepository(
        userPreferences = UserPreferencesRepository(application)
    )

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, senha: String) {
        if (_uiState.value is LoginUiState.Loading) return

        if (email.isBlank() || senha.isBlank()) {
            _uiState.value = LoginUiState.Error("Preencha e-mail e senha.")
            return
        }

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            // O UserRepository.login já salva userId e token no DataStore quando dá certo.
            userRepository.login(email.trim(), senha)
                .onSuccess { user ->
                    _uiState.value = LoginUiState.Success(user)
                }
                .onFailure { error ->
                    _uiState.value = LoginUiState.Error(error.toFriendlyMessage())
                }
        }
    }

    /** Chame depois de mostrar o erro (ou de navegar no sucesso) para voltar ao estado inicial. */
    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }

    private fun Throwable.toFriendlyMessage(): String = when (this) {
        // O backend não diferencia "senha errada" de "e-mail não existe" (de propósito,
        // por segurança), então a mensagem cobre os dois casos.
        is InvalidCredentialsException ->
            "E-mail ou senha incorretos, ou a conta não existe. Confira os dados ou crie uma conta."
        is HttpException -> when (code()) {
            400, 401, 403, 404 -> "E-mail ou senha incorretos."
            in 500..599 -> "O servidor está com problemas no momento. Tente novamente em instantes."
            else -> "Não foi possível fazer login (erro ${code()})."
        }
        is IOException -> "Sem conexão com o servidor. Verifique sua internet."
        else -> "Ocorreu um erro inesperado. Tente novamente."
    }
}
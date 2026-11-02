package com.andre.medalert.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.CreateCuidadorRequest
import com.andre.medalert.data.remote.dto.CreateUserRequest
import com.andre.medalert.data.remote.dto.UserData
import com.andre.medalert.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** Tipo de conta escolhido na tela de cadastro. */
enum class RegisterAccountType { PACIENTE, CUIDADOR }

sealed interface RegisterUiState {
    data object Idle : RegisterUiState
    data object Loading : RegisterUiState
    data class Success(val userId: Int, val tipo: RegisterAccountType) : RegisterUiState
    data class Error(val message: String) : RegisterUiState
}

class RegisterViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    /**
     * [telefone] e [parentesco] só são usados quando [tipo] é CUIDADOR (a API de paciente não
     * tem esses campos). O usuarioId do cuidador nunca é enviado: o vínculo vem depois,
     * pelo convite do paciente e pelo aceite.
     */
    fun register(
        tipo: RegisterAccountType,
        nome: String,
        email: String,
        senha: String,
        confirmarSenha: String,
        telefone: String = "",
        parentesco: String? = null
    ) {
        if (_uiState.value is RegisterUiState.Loading) return

        val nomeLimpo = nome.trim()
        val emailLimpo = email.trim()
        val telefoneLimpo = telefone.filter { it.isDigit() }

        if (nomeLimpo.isBlank() || emailLimpo.isBlank() || senha.isBlank()) {
            _uiState.value = RegisterUiState.Error("Preencha nome, e-mail e senha.")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(emailLimpo).matches()) {
            _uiState.value = RegisterUiState.Error("Digite um e-mail válido.")
            return
        }

        if (senha != confirmarSenha) {
            _uiState.value = RegisterUiState.Error("As senhas não coincidem.")
            return
        }

        if (tipo == RegisterAccountType.CUIDADOR &&
            telefoneLimpo.isNotEmpty() &&
            telefoneLimpo.length !in 10..11
        ) {
            _uiState.value = RegisterUiState.Error("Digite um telefone com DDD (10 ou 11 dígitos) ou deixe em branco.")
            return
        }

        // Regras de senha (tamanho mínimo etc.) ficam por conta do backend por enquanto:
        // se ele recusar, o erro volta como HttpException e vira mensagem amigável abaixo.

        _uiState.value = RegisterUiState.Loading

        viewModelScope.launch {
            try {
                val response = when (tipo) {
                    RegisterAccountType.PACIENTE -> userRepository.createUser(
                        CreateUserRequest(
                            data = UserData(
                                email = emailLimpo,
                                nome = nomeLimpo,
                                senha = senha
                            )
                        )
                    )

                    RegisterAccountType.CUIDADOR -> userRepository.createCuidador(
                        CreateCuidadorRequest(
                            nome = nomeLimpo,
                            email = emailLimpo,
                            senha = senha,
                            // usuarioId fica null (omitido): o vínculo é feito depois.
                            telefone = telefoneLimpo.ifEmpty { null },
                            parentesco = parentesco?.takeIf { it.isNotBlank() }
                        )
                    )
                }
                _uiState.value = RegisterUiState.Success(response.userId, tipo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(e.toFriendlyMessage())
            }
        }
    }

    /** Chame depois de mostrar o erro (ou de navegar no sucesso) para voltar ao estado inicial. */
    fun resetState() {
        _uiState.value = RegisterUiState.Idle
    }

    private fun Throwable.toFriendlyMessage(): String = when (this) {
        is HttpException -> when (code()) {
            409 -> "Este e-mail já está cadastrado. Entre na sua conta ou use outro e-mail."
            in 400..499 -> "Não foi possível criar a conta. Verifique os dados ou tente outro e-mail."
            in 500..599 -> "Não foi possível criar a conta agora. Se o problema continuar, tente outro e-mail ou tente novamente mais tarde."
            else -> "Não foi possível criar a conta (erro ${code()})."
        }
        is IOException -> "Sem conexão com o servidor. Verifique sua internet."
        else -> "Ocorreu um erro inesperado. Tente novamente."
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY]!!

                // 1. Preferências (DataStore)
                val prefs = UserPreferencesRepository(application)

                // 2. API (Retrofit)
                val apiService = RetrofitClient.instance

                // 3. Repositório real, mesmo padrão do ChangePasswordViewModel
                val repository = UserRepository(
                    api = apiService,
                    userPreferences = prefs
                )

                RegisterViewModel(repository)
            }
        }
    }
}
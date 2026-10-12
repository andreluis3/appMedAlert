package com.andre.medalert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.state.ProfileUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Segue o padrão Screen -> ViewModel -> Repository -> ApiService.
 *
 * IMPORTANTE: hoje o backend não expõe um GET /api/users/{id} (só existem
 * POST /api/users e POST /api/users/login na doc atual). Por isso este
 * ViewModel ainda não injeta UserRepository/ApiService — os dados vêm do que
 * já foi capturado no cadastro/login (local), igual ao padrão de "simulação"
 * usado em outras telas do projeto (ex: addMedication).
 *
 * Quando o endpoint de busca de usuário existir, troque o loadProfile() por
 * uma chamada real, por exemplo:
 *
 *   class ProfileViewModel(
 *       private val userRepository: UserRepository = UserRepository()
 *   ) : ViewModel() {
 *       private fun loadProfile() {
 *           viewModelScope.launch {
 *               val user = userRepository.getUser(userId)
 *               _uiState.value = ProfileUiState(nome = user.nome, email = user.email, ...)
 *           }
 *       }
 *   }
 */
class ProfileViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            // TODO: substituir pelos dados reais de sessão (resultado do login/cadastro)
            // assim que existir um local central de sessão (SharedPreferences/DataStore).
            _uiState.value = ProfileUiState(
                nome = "André Luis",
                email = "andre@email.com",
                idade = 22, // TODO: Campo aguardando definição do contrato da API
                isLoading = false
            )
        }
    }

    fun onLogout() {
        // TODO: limpar sessão local (token/SharedPreferences/DataStore) quando esse
        // mecanismo existir no projeto. A navegação de volta para Onboarding/Login
        // é responsabilidade do NavHost, via callback onLogoutClick passado à tela.
    }
}
package com.andre.medalert.viewmodel

import androidx.lifecycle.ViewModel
import com.andre.medalert.data.repository.UserRepository

class LoginViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {
    // TODO: estado de UI (loading/sucesso/erro) e chamadas de login/cadastro
    // entram aqui quando a tela de Login/Cadastro for conectada à API.
}
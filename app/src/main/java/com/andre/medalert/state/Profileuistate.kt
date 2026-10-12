package com.andre.medalert.state

data class ProfileUiState(
    val nome: String = "",
    val email: String = "",
    // Campo ainda sem contrato definido no backend (não existe em UserData/CreateUserRequest).
    // Mantido como mock/local até a API expor esse dado.
    // TODO: Campo aguardando definição do contrato da API
    val idade: Int? = null,
    val isLoading: Boolean = false
)
package com.andre.medalert.data.remote.dto

data class UserData(
    val email: String,
    val nome: String,
    val senha: String
)

data class CreateUserRequest(
    val data: UserData
)

data class CreateUserResponse(
    val userId: Int
)

data class LoginRequest(
    val email: String,
    val senha: String
)

// O doc não mostra o formato exato da resposta do login ainda.
// Deixei genérico por enquanto - ajustar quando o backend confirmar.
data class LoginResponse(
    val userId: Int? = null
)

// TODO: Endpoint de alterar senha ainda não existe no ApiService/backend.
// Modelo preparado para quando POST /api/users/change-password (ou similar) existir.
data class ChangePasswordRequest(
    val senhaAtual: String,
    val novaSenha: String
)

// TODO: Formato de resposta é uma suposição até o backend confirmar o contrato real.
data class ChangePasswordResponse(
    val sucesso: Boolean = true
)
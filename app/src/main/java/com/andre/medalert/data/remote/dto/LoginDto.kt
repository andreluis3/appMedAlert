package com.andre.medalert.data.remote.dto

import com.andre.medalert.data.model.User
import com.google.gson.annotations.SerializedName

// O que você ENVIA para o servidor
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("senha") val senha: String
)

// O que o servidor DEVOLVE para você.
// Todos os campos são opcionais porque o backend responde HTTP 200 também quando o
// login falha; nesse caso o corpo traz só {"message": "..."} (sem userId).
// Quem decide se foi sucesso é o UserRepository, olhando o userId.
data class LoginResponse(
    @SerializedName("userId") val userId: Int? = null,
    @SerializedName("nome") val nome: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("message") val message: String? = null
)

// Mapper DTO -> domínio (só chame depois de validar que userId existe e é > 0)
fun LoginResponse.toDomain(): User = User(
    id = userId ?: 0,
    nome = nome.orEmpty(),
    token = token
)
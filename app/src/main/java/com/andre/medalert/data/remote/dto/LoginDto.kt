package com.andre.medalert.data.remote.dto

import com.andre.medalert.data.model.User
import com.google.gson.annotations.SerializedName

// O que você ENVIA para o servidor
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("senha") val senha: String
)

// O que o servidor DEVOLVE para você
data class LoginResponse(
    @SerializedName("userId") val userId: Int,
    @SerializedName("nome") val nome: String,
    @SerializedName("token") val token: String? = null
)

// Mapper DTO -> domínio
fun LoginResponse.toDomain(): User = User(
    id = userId,
    nome = nome,
    token = token
)
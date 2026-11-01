package com.andre.medalert.data.remote.dto

import com.andre.medalert.data.model.User
import com.google.gson.annotations.SerializedName

// O que você ENVIA para o servidor
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("senha") val senha: String
)

/**
 * O que o servidor DEVOLVE (README): {"id":1,"tipo":"paciente"}
 * ou {"id":3,"tipo":"cuidador","pacienteId":1}.
 * Todos opcionais: versões antigas devolviam só um número ou {"message": "..."}.
 * Quem decide se foi sucesso é o UserRepository, olhando o id.
 */
data class LoginResponse(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName(value = "pacienteId", alternate = ["paciente_id", "pacientId"])
    val pacienteId: Int? = null,
    @SerializedName("nome") val nome: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("message") val message: String? = null
)

// Só chame depois de validar que id existe e é > 0.
fun LoginResponse.toDomain(): User = User(
    id = id ?: 0,
    nome = nome.orEmpty(),
    token = token,
    tipo = tipo ?: "paciente",
    pacienteId = pacienteId
)
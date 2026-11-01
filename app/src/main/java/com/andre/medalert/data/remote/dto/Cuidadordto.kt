package com.andre.medalert.data.remote.dto

/** POST /api/users/cuidador (README). usuarioId, telefone e parentesco são opcionais. */
data class CreateCuidadorRequest(
    val nome: String,
    val email: String,
    val senha: String,
    val usuarioId: Int? = null,
    val telefone: String? = null,
    val parentesco: String? = null
)

/** POST /api/users/token */
data class SaveFcmTokenRequest(
    val cuidadorId: Int,
    val token: String
)

/** POST /api/users/requestCuidador: email do CUIDADOR, nome e id do PACIENTE. */
data class RequestCuidadorRequest(
    val email: String,
    val nome: String,
    val userId: Int
)

/** POST /api/users/accept. A grafia "pacientId" é a da README; não renomear sem confirmar no backend. */
data class AcceptPacienteRequest(
    val cuidadorId: Int,
    val pacientId: Int
)
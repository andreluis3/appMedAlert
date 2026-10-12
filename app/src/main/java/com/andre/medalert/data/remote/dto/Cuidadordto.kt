package com.andre.medalert.data.remote.dto

/**
 * Corpo de POST /api/users/cuid, com os únicos campos definidos na
 * documentação. Não adicionar campos aqui sem confirmação do backend
 * (ex.: relação, status, data de vínculo, permissões).
 */
data class CreateCuidadorRequest(
    val email: String,
    val nome: String,
    val userId: Int
)
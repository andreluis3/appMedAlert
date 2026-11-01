package com.andre.medalert.data.model

/**
 * Usuário autenticado.
 * - [tipo]: "paciente" ou "cuidador" (vem do login).
 * - [pacienteId]: só para cuidador; é o ID do paciente cujos medicamentos ele acompanha.
 * Os campos novos têm valor padrão para não quebrar quem já cria User(id, nome, token).
 */
data class User(
    val id: Int,
    val nome: String,
    val token: String?,
    val tipo: String = "paciente",
    val pacienteId: Int? = null
)
package com.andre.medalert.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Corpo de POST /api/users (FLAT, sem wrapper "data").
 * Confirmado no Postman: {"email","nome","senha"} -> 201 com o id em número puro.
 */
data class UserData(
    val email: String,
    val nome: String,
    val senha: String,
    @SerializedName("precisa_cuidador") val precisaCuidador: Boolean = false
)

/**
 * Mantido só para não quebrar quem já monta CreateUserRequest(UserData(...)).
 * O UserRepository envia apenas o [data] para a API.
 */
data class CreateUserRequest(
    val data: UserData
)

data class CreateUserResponse(
    val userId: Int
)
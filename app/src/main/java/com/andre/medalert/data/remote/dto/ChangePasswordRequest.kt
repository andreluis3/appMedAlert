package com.andre.medalert.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChangePasswordRequest(
    @SerializedName("userId") val userId: Int,
    @SerializedName("senhaAtual") val senhaAtual: String,
    @SerializedName("novaSenha") val novaSenha: String
)

data class ChangePasswordResponse(
    @SerializedName("message") val message: String,
    @SerializedName("success") val success: Boolean
)
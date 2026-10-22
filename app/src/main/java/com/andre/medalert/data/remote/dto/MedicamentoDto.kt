package com.andre.medalert.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Corpo do POST /api/meds (JSON FLAT, sem wrapper "data").
 * Datas no formato "yyyy-MM-dd HH:mm:ss-03:00".
 */
data class MedicamentoData(
    @SerializedName("nome") val nome: String,
    @SerializedName("dataInicio") val dataInicio: String,
    @SerializedName("dataFim") val dataFim: String,
    @SerializedName("dosagem") val dosagem: Int,
    @SerializedName("frequencia") val frequencia: Int,
    @SerializedName("quantidade") val quantidade: Int,
    @SerializedName("unidadeDosagem") val unidadeDosagem: String,
    @SerializedName("unidadeQuantidade") val unidadeQuantidade: String,
    @SerializedName("userId") val userId: Int
)

data class MedicamentoResponse(
    val nome: String,
    val dosagem: String,
    val doseDiaria: List<String>
)
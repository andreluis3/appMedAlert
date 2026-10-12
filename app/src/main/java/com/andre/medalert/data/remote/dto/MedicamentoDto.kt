package com.andre.medalert.data.remote.dto

data class MedicamentoData(
    val nome: String,
    val dataInicio: String,
    val dataFim: String,
    val dosagem: Int,
    val frequencia: Int,
    val quantidade: Int,
    val unidadeDosagem: String,
    val unidadeQuantidade: String,
    val userId: Int
)

data class CreateMedicamentoRequest(
    val data: MedicamentoData
)

data class MedicamentoResponse(
    val nome: String,
    val dosagem: String,
    val doseDiaria: List<String>
)
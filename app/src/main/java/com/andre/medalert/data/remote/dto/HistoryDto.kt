package com.andre.medalert.data.remote.dto

data class HistoryData(
    val medicamentoId: Int,
    val userId: Int,
    val data: String? = null
)

data class CreateHistoryRequest(
    val data: HistoryData
)

// Usado no corpo do GET /api/historys
data class HistoryQueryRequest(
    val id: Int
)

data class DoseDto(
    val horario: String,
    val tomado: String?,
    val status: String // "tomado" | "atrasado" | "não tomado" | "pendente"
)

data class HistoryRecordDto(
    val nome: String,
    val dosagem: String,
    val doses: List<DoseDto>
)
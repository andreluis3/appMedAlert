package com.andre.medalert.data.model

/**
 * Estados possíveis de uma dose, conforme os valores literais que vêm de
 * HistoryRecordDto/DoseDto.status: "tomado" | "atrasado" | "não tomado" | "pendente".
 */
enum class HistoryDoseStatus { TOMADO, ATRASADO, NAO_TOMADO, PENDENTE }

/**
 * Uma dose específica de um medicamento em um dia, já achatada para a UI
 * (a API devolve agrupado por medicamento, com uma lista de doses dentro).
 */
data class HistoryUiModel(
    val medicationName: String,
    val dosage: String,
    val scheduledTime: String,
    val takenAt: String?,
    val status: HistoryDoseStatus
)
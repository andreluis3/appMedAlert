package com.andre.medalert.data.model

enum class DoseHistoryStatus { TOMADO, ATRASADO, NAO_TOMADO }

data class DoseHistoryRecord(
    val id: String,
    val medicationName: String,
    val scheduledTime: String,
    val dosage: String,
    val actualTime: String? = null,
    val status: DoseHistoryStatus
)
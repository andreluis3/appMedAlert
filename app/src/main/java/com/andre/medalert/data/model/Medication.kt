package com.andre.medalert.data.model

/**
 * Representa um medicamento cadastrado.
 * Pensado já com os campos que o backend/Room vai precisar futuramente.
 */
data class Medication(
    val id: String,
    val name: String,
    val dosage: String,
    val quantity: String,
    val schedules: List<String>, // ex: ["08:00", "12:00", "18:00"]
    val frequency: String,       // ex: "Todos os dias"
    val startDate: String? = null,
    val endDate: String? = null,
    val notes: String? = null,
    val active: Boolean = true
)

enum class DoseStatus {
    TAKEN,
    PENDING,
    MISSED
}

/**
 * Representa uma dose específica de um medicamento em um horário do dia.
 * Separado de Medication porque, no futuro, o histórico será uma tabela própria.
 */
data class MedicationDose(
    val id: String,
    val medicationId: String,
    val medicationName: String,
    val dosage: String,
    val scheduledTime: String,
    val takenAt: String? = null,
    val status: DoseStatus
)
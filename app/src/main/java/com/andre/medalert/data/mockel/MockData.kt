package com.andre.medalert.data.mock

import com.andre.medalert.data.model.DoseStatus
import com.andre.medalert.data.model.Medication
import com.andre.medalert.data.model.MedicationDose

/**
 * Dados falsos centralizados. Quando tivermos Repository/Room,
 * este objeto é o único lugar que precisa ser substituído.
 */
object MockData {

    val medications = listOf(
        Medication(
            id = "1",
            name = "Vitamina D",
            dosage = "1000 UI",
            quantity = "1 cápsula",
            schedules = listOf("08:00"),
            frequency = "Todos os dias"
        ),
        Medication(
            id = "2",
            name = "Losartana",
            dosage = "50 mg",
            quantity = "1 comprimido",
            schedules = listOf("08:00", "12:00", "18:00"),
            frequency = "Todos os dias"
        ),
        Medication(
            id = "3",
            name = "Omeprazol",
            dosage = "20 mg",
            quantity = "1 cápsula",
            schedules = listOf("22:00"),
            frequency = "Todos os dias"
        )
    )

    val medicationsToday = listOf(
        MedicationDose("d1", "1", "Vitamina D", "1000 UI", "08:00", takenAt = "08:03", status = DoseStatus.TAKEN),
        MedicationDose("d2", "2", "Losartana", "50 mg", "12:00", takenAt = "12:10", status = DoseStatus.TAKEN),
        MedicationDose("d3", "2", "Losartana", "50 mg", "18:00", takenAt = null, status = DoseStatus.PENDING),
        MedicationDose("d4", "3", "Omeprazol", "20 mg", "22:00", takenAt = null, status = DoseStatus.PENDING)
    )

    const val adherencePercentage: Int = 92
    const val responsibleCount: Int = 2
}
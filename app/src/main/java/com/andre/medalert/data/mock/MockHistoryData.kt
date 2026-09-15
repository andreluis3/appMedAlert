package com.andre.medalert.data.mock

import com.andre.medalert.data.model.DoseHistoryRecord
import com.andre.medalert.data.model.DoseHistoryStatus

val mockHistoryMedicationFilters = listOf("Todos", "Losartana", "Omeprazol", "Vitamina D")

val mockHistoryRecords = listOf(
    DoseHistoryRecord(
        id = "1",
        medicationName = "Vitamina D",
        scheduledTime = "08:00",
        dosage = "1000 UI",
        actualTime = "08:03",
        status = DoseHistoryStatus.TOMADO
    ),
    DoseHistoryRecord(
        id = "2",
        medicationName = "Losartana",
        scheduledTime = "12:00",
        dosage = "50 mg",
        actualTime = "12:10",
        status = DoseHistoryStatus.TOMADO
    ),
    DoseHistoryRecord(
        id = "3",
        medicationName = "Losartana",
        scheduledTime = "18:00",
        dosage = "50 mg",
        actualTime = "18:52",
        status = DoseHistoryStatus.ATRASADO
    ),
    DoseHistoryRecord(
        id = "4",
        medicationName = "Omeprazol",
        scheduledTime = "22:00",
        dosage = "20 mg",
        actualTime = null,
        status = DoseHistoryStatus.NAO_TOMADO
    )
)
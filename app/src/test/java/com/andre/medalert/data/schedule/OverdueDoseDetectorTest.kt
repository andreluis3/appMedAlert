package com.andre.medalert.data.schedule

import com.andre.medalert.data.model.HistoryDoseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OverdueDoseDetectorTest {

    private val day: LocalDate = LocalDate.of(2026, 11, 20)

    private fun event(
        status: HistoryDoseStatus,
        localId: Long? = 10,
        removed: Boolean = false,
        takenAt: LocalDateTime? = null,
        hour: Int = 8
    ) = DoseEvent(
        medicationLocalId = localId,
        medicationName = "Losartana",
        dosage = "50 mg",
        scheduledAt = day.atTime(hour, 0),
        takenAt = takenAt,
        status = status,
        medicationRemoved = removed
    )

    @Test
    fun `atrasado e nao tomado sem registro entram`() {
        val result = OverdueDoseDetector.overdue(
            listOf(
                event(HistoryDoseStatus.ATRASADO, hour = 8),
                event(HistoryDoseStatus.NAO_TOMADO, hour = 12)
            )
        )

        assertEquals(2, result.size)
        assertEquals(
            listOf(HistoryDoseStatus.ATRASADO, HistoryDoseStatus.NAO_TOMADO),
            result.map { it.status }
        )
    }

    @Test
    fun `pendente tomado e tomado com atraso nao entram`() {
        val taken = day.atTime(8, 50)
        val result = OverdueDoseDetector.overdue(
            listOf(
                event(HistoryDoseStatus.PENDENTE),
                event(HistoryDoseStatus.TOMADO, takenAt = taken),
                event(HistoryDoseStatus.TOMADO_COM_ATRASO, takenAt = taken)
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `dose com horario de tomada nunca entra mesmo com status de atraso`() {
        val result = OverdueDoseDetector.overdue(
            listOf(event(HistoryDoseStatus.ATRASADO, takenAt = day.atTime(9, 0)))
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `medicamento excluido ou sem id local nao entra`() {
        val result = OverdueDoseDetector.overdue(
            listOf(
                event(HistoryDoseStatus.NAO_TOMADO, removed = true),
                event(HistoryDoseStatus.ATRASADO, localId = null)
            )
        )

        assertTrue(result.isEmpty())
    }
}
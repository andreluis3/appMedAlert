package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.model.HistoryDoseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class CalendarSummaryTest {

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val month: YearMonth = YearMonth.of(2026, 11)

    private fun event(day: Int, hour: Int, status: HistoryDoseStatus) = DoseEvent(
        medicationLocalId = 10,
        medicationName = "Losartana",
        dosage = "50 mg",
        scheduledAt = LocalDateTime.of(2026, 11, day, hour, 0),
        takenAt = null,
        status = status
    )

    // ------------------------------------------------------------ prioridade

    @Test
    fun `dia sem doses fica NONE`() {
        assertEquals(CalendarDayStatus.NONE, CalendarSummary.statusOf(emptyList()))
    }

    @Test
    fun `todas tomadas no prazo fica ALL_TAKEN`() {
        val events = listOf(
            event(6, 8, HistoryDoseStatus.TOMADO),
            event(6, 20, HistoryDoseStatus.TOMADO)
        )
        assertEquals(CalendarDayStatus.ALL_TAKEN, CalendarSummary.statusOf(events))
    }

    @Test
    fun `dose pendente fica PENDING`() {
        val events = listOf(
            event(6, 8, HistoryDoseStatus.TOMADO),
            event(6, 20, HistoryDoseStatus.PENDENTE)
        )
        assertEquals(CalendarDayStatus.PENDING, CalendarSummary.statusOf(events))
    }

    @Test
    fun `tomada com atraso ou atrasada fica LATE`() {
        assertEquals(
            CalendarDayStatus.LATE,
            CalendarSummary.statusOf(
                listOf(event(6, 8, HistoryDoseStatus.TOMADO_COM_ATRASO), event(6, 20, HistoryDoseStatus.PENDENTE))
            )
        )
        assertEquals(
            CalendarDayStatus.LATE,
            CalendarSummary.statusOf(listOf(event(6, 8, HistoryDoseStatus.ATRASADO)))
        )
    }

    @Test
    fun `qualquer dose nao tomada deixa o dia MISSED`() {
        val events = listOf(
            event(6, 8, HistoryDoseStatus.NAO_TOMADO),
            event(6, 14, HistoryDoseStatus.ATRASADO),
            event(6, 20, HistoryDoseStatus.TOMADO)
        )
        assertEquals(CalendarDayStatus.MISSED, CalendarSummary.statusOf(events))
    }

    // ------------------------------------------------------------ grade

    @Test
    fun `grade fecha semanas completas e marca hoje`() {
        // 01/11/2026 e domingo: nenhum espaco antes do dia 1.
        val cells = CalendarSummary.cellsFor(month, emptyList(), today = LocalDate.of(2026, 11, 6))

        assertEquals(0, cells.size % 7)
        assertEquals(1, cells.first().day)
        assertEquals(30, cells.count { it.day != null })
        assertTrue(cells.single { it.day == 6 }.isToday)
        assertFalse(cells.single { it.day == 7 }.isToday)
        assertTrue(cells.none { it.hasDoses })
    }

    @Test
    fun `grade deixa espacos antes do primeiro dia quando o mes nao comeca no domingo`() {
        // 01/12/2026 e terca-feira: duas celulas vazias antes.
        val cells = CalendarSummary.cellsFor(YearMonth.of(2026, 12), emptyList(), LocalDate.of(2026, 11, 6))

        assertEquals(null, cells[0].day)
        assertEquals(null, cells[1].day)
        assertEquals(1, cells[2].day)
    }

    @Test
    fun `cada dia recebe o status das proprias doses`() {
        val events = listOf(
            event(5, 8, HistoryDoseStatus.TOMADO),
            event(6, 8, HistoryDoseStatus.NAO_TOMADO),
            event(7, 8, HistoryDoseStatus.PENDENTE)
        )

        val cells = CalendarSummary.cellsFor(month, events, LocalDate.of(2026, 11, 6))

        assertEquals(CalendarDayStatus.ALL_TAKEN, cells.single { it.day == 5 }.status)
        assertEquals(CalendarDayStatus.MISSED, cells.single { it.day == 6 }.status)
        assertEquals(CalendarDayStatus.PENDING, cells.single { it.day == 7 }.status)
        assertEquals(CalendarDayStatus.NONE, cells.single { it.day == 8 }.status)
    }

    // ------------------------------------------------------------ ponta a ponta

    @Test
    fun `calendario mostra doses previstas mesmo sem nenhum registro`() {
        val medication = MedicationEntity(
            id = 10,
            userId = 1,
            nome = "Losartana",
            dosagem = "50 mg",
            horarioDose = "08:00",
            dataInicio = "2026-11-01",
            dataFim = "2026-11-30",
            frequencia = 12,
            remoteId = 1
        )
        val now = LocalDateTime.of(2026, 11, 6, 7, 0)

        val events = DoseTimelineBuilder.build(
            medications = listOf(medication),
            records = emptyList(),
            from = month.atDay(1).atStartOfDay(),
            toExclusive = month.plusMonths(1).atDay(1).atStartOfDay(),
            now = now,
            zone = zone
        )
        val cells = CalendarSummary.cellsFor(month, events, now.toLocalDate())

        // Dias passados sem registro: perdidos. Hoje e futuros: pendentes.
        assertEquals(CalendarDayStatus.MISSED, cells.single { it.day == 3 }.status)
        assertEquals(CalendarDayStatus.PENDING, cells.single { it.day == 6 }.status)
        assertEquals(CalendarDayStatus.PENDING, cells.single { it.day == 30 }.status)
    }

    @Test
    fun `calendario preserva dia com historico de medicamento excluido`() {
        val scheduled = LocalDateTime.of(2026, 11, 4, 8, 0)
        val record = DoseHistoryEntity(
            userId = 1,
            medicamentoNome = "Losartana",
            dosagem = "50 mg",
            horario = "08:00",
            tomado = "08:05",
            status = "tomado",
            isSynced = false,
            medicationLocalId = 10,
            previstoEm = scheduled.atZone(zone).toInstant().toEpochMilli(),
            tomadoEm = scheduled.plusMinutes(5).atZone(zone).toInstant().toEpochMilli(),
            dia = "2026-11-04",
            medicamentoRemovido = true
        )
        val now = LocalDateTime.of(2026, 11, 6, 12, 0)

        val events = DoseTimelineBuilder.build(
            medications = emptyList(),
            records = listOf(record),
            from = month.atDay(1).atStartOfDay(),
            toExclusive = month.plusMonths(1).atDay(1).atStartOfDay(),
            now = now,
            zone = zone
        )
        val cells = CalendarSummary.cellsFor(month, events, now.toLocalDate())

        assertEquals(CalendarDayStatus.ALL_TAKEN, cells.single { it.day == 4 }.status)
        assertEquals(CalendarDayStatus.NONE, cells.single { it.day == 5 }.status)
    }
}

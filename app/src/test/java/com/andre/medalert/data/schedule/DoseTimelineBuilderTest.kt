package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.model.HistoryDoseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DoseTimelineBuilderTest {

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val day: LocalDate = LocalDate.of(2026, 11, 6)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day): LocalDateTime =
        date.atTime(hour, minute)

    private fun millis(time: LocalDateTime): Long =
        time.atZone(zone).toInstant().toEpochMilli()

    /** Losartana de 12 em 12 horas a partir das 08:00 (doses às 08:00 e 20:00). */
    private fun medication(
        id: Long = 10,
        start: String = "2026-11-01",
        end: String? = null,
        since: Long? = null
    ) = MedicationEntity(
        id = id,
        userId = 1,
        nome = "Losartana",
        dosagem = "50 mg",
        horarioDose = "08:00",
        dataInicio = start,
        dataFim = end,
        frequencia = 12,
        remoteId = 1,
        acompanhadoDesde = since
    )

    private fun taken(
        scheduled: LocalDateTime,
        takenAt: LocalDateTime,
        medicationId: Long = 10,
        removed: Boolean = false
    ) = DoseHistoryEntity(
        userId = 1,
        medicamentoNome = "Losartana",
        dosagem = "50 mg",
        horario = "%02d:%02d".format(scheduled.hour, scheduled.minute),
        tomado = "%02d:%02d".format(takenAt.hour, takenAt.minute),
        status = "tomado",
        isSynced = false,
        medicationLocalId = medicationId,
        previstoEm = millis(scheduled),
        tomadoEm = millis(takenAt),
        dia = scheduled.toLocalDate().toString(),
        medicamentoRemovido = removed
    )

    private fun build(
        medications: List<MedicationEntity>,
        records: List<DoseHistoryEntity>,
        now: LocalDateTime
    ) = DoseTimelineBuilder.build(
        medications = medications,
        records = records,
        from = day.atStartOfDay(),
        toExclusive = day.plusDays(1).atStartOfDay(),
        now = now,
        zone = zone
    )

    // ------------------------------------------------------------ status puro

    @Test
    fun `dose tomada dentro da tolerancia e Tomado`() {
        val status = DoseStatusRules.classify(at(8), at(8, 30), at(12))
        assertEquals(HistoryDoseStatus.TOMADO, status)
    }

    @Test
    fun `dose tomada antes do horario e Tomado`() {
        val status = DoseStatusRules.classify(at(8), at(7, 50), at(12))
        assertEquals(HistoryDoseStatus.TOMADO, status)
    }

    @Test
    fun `dose tomada depois da tolerancia e Tomado com atraso`() {
        val status = DoseStatusRules.classify(at(8), at(8, 31), at(12))
        assertEquals(HistoryDoseStatus.TOMADO_COM_ATRASO, status)
    }

    @Test
    fun `dose futura e Pendente`() {
        assertEquals(HistoryDoseStatus.PENDENTE, DoseStatusRules.classify(at(20), null, at(7)))
    }

    @Test
    fun `sem registro dentro da tolerancia continua Pendente`() {
        assertEquals(HistoryDoseStatus.PENDENTE, DoseStatusRules.classify(at(8), null, at(8, 30)))
    }

    @Test
    fun `sem registro depois da tolerancia e Atrasado`() {
        assertEquals(HistoryDoseStatus.ATRASADO, DoseStatusRules.classify(at(8), null, at(8, 31)))
        assertEquals(HistoryDoseStatus.ATRASADO, DoseStatusRules.classify(at(8), null, at(10, 0)))
    }

    @Test
    fun `sem registro depois do limite e Nao tomado`() {
        assertEquals(HistoryDoseStatus.NAO_TOMADO, DoseStatusRules.classify(at(8), null, at(10, 1)))
    }

    // ------------------------------------------------------------- cruzamento

    @Test
    fun `medicamento sem registros mostra as doses previstas`() {
        val events = build(listOf(medication()), emptyList(), now = at(7))

        assertEquals(2, events.size)
        assertEquals(listOf(at(8), at(20)), events.map { it.scheduledAt })
        assertTrue(events.all { it.status == HistoryDoseStatus.PENDENTE })
    }

    @Test
    fun `dose registrada vira Tomado e a outra continua Pendente`() {
        val events = build(
            medications = listOf(medication()),
            records = listOf(taken(at(8), at(8, 10))),
            now = at(12)
        )

        assertEquals(2, events.size)
        assertEquals(HistoryDoseStatus.TOMADO, events[0].status)
        assertEquals(at(8, 10), events[0].takenAt)
        assertEquals(HistoryDoseStatus.PENDENTE, events[1].status)
        assertNull(events[1].takenAt)
    }

    @Test
    fun `dose registrada com atraso vira Tomado com atraso`() {
        val events = build(
            medications = listOf(medication()),
            records = listOf(taken(at(8), at(8, 52))),
            now = at(12)
        )

        assertEquals(HistoryDoseStatus.TOMADO_COM_ATRASO, events[0].status)
    }

    @Test
    fun `registro nao duplica a dose prevista`() {
        val events = build(
            medications = listOf(medication()),
            records = listOf(taken(at(8), at(8, 5)), taken(at(20), at(20, 5))),
            now = at(21)
        )

        assertEquals(2, events.size)
        assertTrue(events.all { it.status == HistoryDoseStatus.TOMADO })
    }

    @Test
    fun `dose perdida do dia aparece como Nao tomado`() {
        // 21:00: a dose das 08:00 passou de 120 min; a das 20:00 está a 60 min.
        val events = build(listOf(medication()), emptyList(), now = at(21))

        assertEquals(HistoryDoseStatus.NAO_TOMADO, events[0].status)
        assertEquals(HistoryDoseStatus.ATRASADO, events[1].status)
    }

    @Test
    fun `data de inicio futura nao gera doses`() {
        val events = build(listOf(medication(start = "2026-12-01")), emptyList(), now = at(12))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `depois da data de termino nao gera doses`() {
        val events = build(listOf(medication(end = "2026-11-05")), emptyList(), now = at(12))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `no ultimo dia do tratamento as doses ainda aparecem`() {
        val events = build(listOf(medication(end = "2026-11-06")), emptyList(), now = at(7))
        assertEquals(2, events.size)
    }

    @Test
    fun `medicamento excluido preserva o historico e nao gera doses novas`() {
        val events = build(
            medications = emptyList(),
            records = listOf(taken(at(8), at(8, 5), removed = true)),
            now = at(12)
        )

        assertEquals(1, events.size)
        assertEquals(HistoryDoseStatus.TOMADO, events[0].status)
        assertTrue(events[0].medicationRemoved)
    }

    @Test
    fun `doses de antes do acompanhamento sem registro nao sao cobradas`() {
        val events = build(
            medications = listOf(medication(since = millis(at(9)))),
            records = emptyList(),
            now = at(12)
        )

        assertEquals(1, events.size)
        assertEquals(at(20), events[0].scheduledAt)
    }

    @Test
    fun `dose registrada antes do acompanhamento continua aparecendo`() {
        val events = build(
            medications = listOf(medication(since = millis(at(9)))),
            records = listOf(taken(at(8), at(8, 5))),
            now = at(12)
        )

        assertEquals(2, events.size)
        assertEquals(HistoryDoseStatus.TOMADO, events[0].status)
    }

    @Test
    fun `registro vindo da API sem horario interpretavel conta como tomado`() {
        val remoteRow = DoseHistoryEntity(
            userId = 1,
            medicamentoId = 1,
            medicamentoNome = "Losartana",
            dosagem = "50 mg",
            horario = "08:00",
            tomado = "valor-desconhecido",
            status = "tomado",
            isSynced = true,
            medicationLocalId = 10,
            previstoEm = millis(at(8)),
            tomadoEm = null,
            dia = day.toString()
        )

        val events = build(listOf(medication()), listOf(remoteRow), now = at(12))

        assertEquals(HistoryDoseStatus.TOMADO, events[0].status)
        assertNull(events[0].takenAt)
    }

    @Test
    fun `registro sem dia nem horario previsto e ignorado`() {
        val unplaceable = DoseHistoryEntity(
            userId = 1,
            medicamentoNome = "Antigo",
            dosagem = "10 mg",
            horario = "",
            tomado = "08:00",
            status = "tomado"
        )

        val events = build(emptyList(), listOf(unplaceable), now = at(12))
        assertTrue(events.isEmpty())
    }
}

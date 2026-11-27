package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.MedicationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AdherenceCalculatorTest {

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val agora: LocalDateTime = LocalDateTime.of(2026, 11, 25, 12, 0)
    private val min = 60_000L
    private val hora = 60 * min

    private fun millis(dia: Int, h: Int, m: Int = 0) =
        LocalDateTime.of(2026, 11, dia, h, m).atZone(zone).toInstant().toEpochMilli()

    private fun med(
        id: Long = 1,
        nome: String = "Losartana",
        horarioDose: String? = "08:00",
        frequencia: Int? = 12,
        dataInicio: String? = "2026-11-01",
        acompanhadoDesde: Long? = null
    ) = MedicationEntity(
        id = id, userId = 1, nome = nome, dosagem = "1 mg", horarios = "",
        horarioDose = horarioDose, dataInicio = dataInicio, frequencia = frequencia,
        ativo = true, remoteId = id.toInt(), isSynced = true, acompanhadoDesde = acompanhadoDesde
    )

    /** Janela de 1 dia: doses 24/11 20:00 e 25/11 08:00 para o medicamento padrão (12 em 12 h). */
    private fun calc(meds: List<MedicationEntity>, taken: List<TakenDose>) =
        AdherenceCalculator.calculate(meds, taken, agora, windowDays = 1, zone = zone)

    @Test
    fun semHistorico_todasAsDosesPrevistasViramNaoRegistradas() {
        val s = calc(listOf(med()), emptyList())
        assertEquals(2, s.expected)
        assertEquals(0, s.taken)
        assertEquals(2, s.notRegistered)
        assertEquals(0, s.percent)
        assertEquals(mapOf("20:00" to 1, "08:00" to 1), s.missedByTime)
    }

    @Test
    fun semMedicamentos_naoHaPercentual() {
        val s = calc(emptyList(), emptyList())
        assertEquals(0, s.expected)
        assertNull(s.percent)
    }

    @Test
    fun doseTomadaNoHorarioExato_contaComoTomadaNoHorario() {
        val s = calc(
            listOf(med()),
            listOf(TakenDose(1, millis(25, 8), millis(25, 8, 5)), TakenDose(1, millis(24, 20), millis(24, 20, 1)))
        )
        assertEquals(2, s.takenOnTime)
        assertEquals(0, s.takenLate)
        assertEquals(100, s.percent)
    }

    @Test
    fun doseTomadaCom90MinutosDeAtraso_contaComoAtrasada() {
        val s = calc(listOf(med()), listOf(TakenDose(1, millis(25, 8), millis(25, 9, 30))))
        assertEquals(1, s.takenLate)
        assertEquals(0, s.takenOnTime)
        assertEquals(1, s.notRegistered)
        assertEquals(50, s.percent)
    }

    @Test
    fun registroComHorarioPrevistoDeslocadoEm3Horas_UTCxLocal_naoViraNaoTomada() {
        // Antes: a chave era o milissegundo exato, e esta dose tomada virava "não tomada".
        val deslocado = TakenDose(1, millis(25, 11), millis(25, 8, 10)) // previsto gravado como 11:00 (UTC)
        val s = calc(listOf(med()), listOf(deslocado))
        assertEquals(1, s.taken)
        assertEquals(1, s.notRegistered) // a dose das 20:00 de ontem continua sem registro
    }

    @Test
    fun registroComMinutosDeDiferenca_horarioEditado_casaComADoseMaisProxima() {
        val s = calc(listOf(med()), listOf(TakenDose(1, millis(25, 8, 20), millis(25, 8, 25))))
        assertEquals(1, s.taken)
    }

    @Test
    fun umRegistroNuncaCobreDuasDoses() {
        // 14:00 está entre 08:00 e 20:00, mas a tolerância (metade do intervalo = 6 h) casa com UMA só.
        val s = calc(listOf(med()), listOf(TakenDose(1, millis(25, 14), millis(25, 14, 5))))
        assertEquals(1, s.taken)
        assertEquals(1, s.notRegistered)
    }

    @Test
    fun registroDeOutroMedicamento_naoCobreDose() {
        val s = calc(listOf(med(id = 1)), listOf(TakenDose(2, millis(25, 8), millis(25, 8, 5))))
        assertEquals(0, s.taken)
    }

    @Test
    fun acompanhadoDesde_limitaAsDosesCobradas() {
        // O aparelho só passou a acompanhar às 06:00 de hoje: a dose de ontem às 20:00 não é cobrada.
        val s = calc(listOf(med(acompanhadoDesde = millis(25, 6))), emptyList())
        assertEquals(1, s.expected)
    }

    @Test
    fun medicamentoSemDataInicio_regraDoServidor_tambemEntraNaAdesao() {
        val s = calc(listOf(med(dataInicio = null)), listOf(TakenDose(1, millis(25, 8), millis(25, 8, 3))))
        assertEquals(2, s.expected)
        assertEquals(1, s.taken)
    }

    @Test
    fun porMedicamentoEPorDiaDaSemana_somamComOTotal() {
        val s = calc(
            listOf(med(id = 1, nome = "A"), med(id = 2, nome = "B", horarioDose = "09:00", frequencia = 24)),
            listOf(TakenDose(1, millis(25, 8), millis(25, 8, 1)))
        )
        assertEquals(s.expected, s.byMedication.sumOf { it.expected })
        assertEquals(s.taken, s.byMedication.sumOf { it.taken })
        assertEquals(s.expected, s.byWeekday.values.sumOf { it.first })
        assertEquals(s.taken, s.byWeekday.values.sumOf { it.second })
    }

    // ---------- fallback offline (mesmo formato do dashboard)

    @Test
    fun fallbackOffline_geraDashboardComAsMesmasContas() {
        val s = calc(
            listOf(med()),
            listOf(TakenDose(1, millis(25, 8), millis(25, 9, 30))) // atrasada
        )
        val r = s.toDashboardResponse()
        assertEquals("50", r.adesao)
        assertEquals(2, r.dosesEsp)
        // No cálculo local, Tomadas = no horário; Atrasadas e Não tomadas são separadas: soma = esperadas.
        assertEquals(r.dosesEsp, r.dosesTom + r.dosesAtr + r.dosesNT)
        assertEquals(1, r.dosesAtr)
        assertEquals(1, r.dosesNT)
        assertEquals("Losartana", r.adesaoR.single().remedio)
        assertEquals(1, r.adesaoR.single().dosesTom) // por medicamento: todas as tomadas (no horário + atrasadas)
        assertTrue(r.horariosEsq.isNotEmpty())
    }
}

package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.MedicationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Regras do cálculo de doses do PACIENTE, com destaque para o caso real: medicamentos que a tela
 * Remédios lista (horários vindos do servidor) mas que não tinham dataInicio e sumiam da Home.
 */
class DoseScheduleCalculatorTest {

    private val hoje: LocalDate = LocalDate.of(2026, 11, 25)

    private fun med(
        id: Long,
        nome: String,
        horarioDose: String? = null,
        frequencia: Int? = null,
        dataInicio: String? = null,
        dataFim: String? = null,
        horarios: String = "",
        ativo: Boolean = true
    ) = MedicationEntity(
        id = id,
        userId = 1,
        nome = nome,
        dosagem = "1 mg",
        horarios = horarios,
        horarioDose = horarioDose,
        dataInicio = dataInicio,
        dataFim = dataFim,
        frequencia = frequencia,
        ativo = ativo,
        remoteId = id.toInt(),
        isSynced = true
    )

    private fun horas(medications: List<MedicationEntity>, dia: LocalDate = hoje) =
        DoseScheduleCalculator.dosesOnDay(medications, dia)
            .map { "%02d:%02d".format(it.at.hour, it.at.minute) }

    // ---------------------------------------------- comportamento que NÃO pode mudar

    @Test
    fun comDataInicio_frequencia12_geraDuasDosesPorDia() {
        val m = med(1, "Losartana", "08:00", 12, "2026-11-01")
        assertEquals(listOf("08:00", "20:00"), horas(listOf(m)))
        assertEquals(DoseScheduleCalculator.PLANO_SERIE, DoseScheduleCalculator.diagnose(m).plano)
    }

    @Test
    fun comDataInicio_somenteListaDeHorarios_repeteTodoDia() {
        val m = med(2, "Amoxicilina", horarios = "11:00,23:00", dataInicio = "2026-11-20")
        assertEquals(listOf("11:00", "23:00"), horas(listOf(m)))
        assertEquals(DoseScheduleCalculator.PLANO_HORARIOS_DIARIOS, DoseScheduleCalculator.diagnose(m).plano)
    }

    @Test
    fun inativo_naoGeraDoses() {
        val m = med(3, "X", "08:00", 12, "2026-11-01", ativo = false)
        assertTrue(horas(listOf(m)).isEmpty())
        assertNotNull(DoseScheduleCalculator.diagnose(m).motivo)
    }

    @Test
    fun comecaHoje_naoGeraDoseAntesDoInicio_mas_geraHoje() {
        val m = med(4, "X", "08:00", 12, "2026-11-25")
        assertTrue(horas(listOf(m), hoje.minusDays(1)).isEmpty())
        assertEquals(listOf("08:00", "20:00"), horas(listOf(m)))
    }

    @Test
    fun comecaAmanha_naoGeraHoje() {
        val m = med(5, "X", "08:00", 12, "2026-11-26")
        assertTrue(horas(listOf(m)).isEmpty())
    }

    @Test
    fun encerradoOntem_naoGeraHoje_comOuSemDataInicio() {
        val comInicio = med(6, "X", "08:00", 12, "2026-11-01", dataFim = "2026-11-24")
        val semInicio = med(7, "Y", "08:00", 12, dataInicio = null, dataFim = "2026-11-24")
        assertTrue(horas(listOf(comInicio)).isEmpty())
        assertTrue(horas(listOf(semInicio)).isEmpty())
    }

    @Test
    fun terminaHoje_aindaGeraAsDosesDeHoje() {
        val m = med(8, "X", "08:00", 12, "2026-11-01", dataFim = "2026-11-25")
        assertEquals(listOf("08:00", "20:00"), horas(listOf(m)))
    }

    // ---------------------------------------------- o bug: sem dataInicio

    @Test
    fun semDataInicio_comHorarioEFrequencia_usaARegraDoServidor_dipirona() {
        val m = med(10, "Dipirona", "06:20", 8, dataInicio = null)
        assertEquals(listOf("06:20", "14:20", "22:20"), horas(listOf(m)))
        assertEquals(DoseScheduleCalculator.PLANO_REGRA_DO_SERVIDOR, DoseScheduleCalculator.diagnose(m).plano)
    }

    @Test
    fun semDataInicio_comSomenteListaDeHorarios_amoxicilina() {
        val m = med(11, "amoxicilina", horarios = "11:00,23:00", dataInicio = null)
        assertEquals(listOf("11:00", "23:00"), horas(listOf(m)))
    }

    @Test
    fun semDataInicio_frequencia24_umaDosePorDia_levofloxacino() {
        val m = med(12, "levofloxacino", "12:00", 24, dataInicio = null)
        assertEquals(listOf("12:00"), horas(listOf(m)))
    }

    @Test
    fun dataInicioInvalida_equivaleAAusente_masFicaRegistradaNasObservacoes() {
        val m = med(13, "X", "08:00", 12, dataInicio = "abc")
        assertEquals(listOf("08:00", "20:00"), horas(listOf(m)))
        assertTrue(DoseScheduleCalculator.diagnose(m).observacoes.any { it.contains("dataInicio inválida") })
    }

    @Test
    fun semDataInicio_atravessaMeiaNoite_frequencia8_primeiraDose2325() {
        val m = med(14, "X", "23:25", 8, dataInicio = null)
        assertEquals(listOf("07:25", "15:25", "23:25"), horas(listOf(m)))
    }

    @Test
    fun semDataInicio_semHorarioNenhum_naoInventaNada() {
        val m = med(15, "X", dataInicio = null)
        assertTrue(horas(listOf(m)).isEmpty())
        val d = DoseScheduleCalculator.diagnose(m)
        assertEquals(DoseScheduleCalculator.PLANO_NENHUM, d.plano)
        assertNotNull(d.motivo)
    }

    @Test
    fun frequenciaMaiorQue24_semListaDeHorarios_naoGeraDoses_comOuSemDataInicio() {
        val sem = med(16, "X", "08:00", 48, dataInicio = null)
        val com = med(17, "Y", "08:00", 48, dataInicio = "2026-11-01")
        assertTrue(horas(listOf(sem)).isEmpty())
        assertTrue(horas(listOf(com)).isEmpty())
        assertTrue(DoseScheduleCalculator.diagnose(com).observacoes.any { it.contains("fora de 1..24") })
    }

    // ---------------------------------------------- o caso do print (Home às 11:43)

    @Test
    fun home_proximaDose_consideraOsMedicamentosAntigos() {
        val teste = med(1, "medicamento teste", "01:35", 8, "2026-11-24") // 01:35, 09:35, 17:35
        val amox = med(2, "amoxicilina", horarios = "11:00,23:00", dataInicio = null)
        val dipirona = med(3, "Dipirona", "06:20", 8, dataInicio = null)
        val levo = med(4, "levoflaxico", "12:00", 24, dataInicio = null)
        val todos = listOf(teste, amox, dipirona, levo)

        val agora = LocalDateTime.of(2026, 11, 25, 11, 43)
        val proxima = DoseScheduleCalculator.nextDoseOverall(todos, agora)
        assertNotNull(proxima)
        assertEquals("levoflaxico", proxima!!.medicationName)
        assertEquals(LocalDateTime.of(2026, 11, 25, 12, 0), proxima.at)

        // 3 (teste) + 2 (amoxicilina) + 3 (dipirona) + 1 (levo) = 9 doses hoje, 4 medicamentos
        val hojeDoses = DoseScheduleCalculator.dosesOnDay(todos, hoje)
        assertEquals(9, hojeDoses.size)
        assertEquals(4, hojeDoses.map { it.medicationLocalId }.distinct().size)
    }

    @Test
    fun nextDose_semDataInicio_vaiParaAmanhaQuandoJaPassouTudoHoje() {
        val m = med(20, "X", "08:00", 24, dataInicio = null)
        val agora = LocalDateTime.of(2026, 11, 25, 9, 0)
        assertEquals(LocalDateTime.of(2026, 11, 26, 8, 0), DoseScheduleCalculator.nextDose(m, agora))
    }

    @Test
    fun nextDose_encerrado_devolveNull() {
        val m = med(21, "X", "08:00", 24, dataInicio = null, dataFim = "2026-11-24")
        assertNull(DoseScheduleCalculator.nextDose(m, LocalDateTime.of(2026, 11, 25, 7, 0)))
    }

    // ---------------------------------------------- frequência que NÃO divide 24 (pendente com o backend)

    @Test
    fun frequencia5h_contagemDeDosesDifereEntreSerieLocalERegraDoServidor() {
        // DIVERGÊNCIA CONHECIDA, A CONFIRMAR COM O KAYKY: com data de início o app gera série contínua
        // (a cada 5 h, atravessando os dias); a regra do servidor (freq x i < 24) repete 5 horários fixos por dia.
        val comInicio = med(30, "Z", "08:00", 5, "2026-11-20")
        val semInicio = med(31, "Z", "08:00", 5, dataInicio = null)
        val dias = (20..24).map { LocalDate.of(2026, 11, it) }
        val serie = dias.sumOf { DoseScheduleCalculator.dosesOnDay(listOf(comInicio), it).size }
        val servidor = dias.sumOf { DoseScheduleCalculator.dosesOnDay(listOf(semInicio), it).size }
        assertEquals(25, servidor) // 5 por dia (04:00, 08:00, 13:00, 18:00, 23:00) x 5 dias
        assertEquals(23, serie)    // 112 h / 5 h
    }

    @Test
    fun frequenciasQueDividem24_serieEServidorCoincidem() {
        listOf(1, 2, 3, 4, 6, 8, 12, 24).forEach { f ->
            val comInicio = med(40, "Z", "08:00", f, "2026-11-20")
            val semInicio = med(41, "Z", "08:00", f, dataInicio = null)
            (21..23).forEach { d ->
                val dia = LocalDate.of(2026, 11, d)
                assertEquals("freq $f dia $d", horas(listOf(semInicio), dia), horas(listOf(comInicio), dia))
            }
        }
    }
}

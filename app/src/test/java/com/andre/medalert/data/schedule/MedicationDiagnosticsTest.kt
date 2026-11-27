package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.MedicationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedicationDiagnosticsTest {

    private val hoje: LocalDate = LocalDate.of(2026, 11, 25)

    private fun med(
        id: Long,
        nome: String,
        userId: Int = 1,
        horarioDose: String? = null,
        frequencia: Int? = null,
        dataInicio: String? = null,
        dataFim: String? = null,
        horarios: String = "",
        ativo: Boolean = true
    ) = MedicationEntity(
        id = id, userId = userId, nome = nome, dosagem = "1 mg", horarios = horarios,
        horarioDose = horarioDose, dataInicio = dataInicio, dataFim = dataFim,
        frequencia = frequencia, ativo = ativo, remoteId = id.toInt(), isSynced = true
    )

    @Test
    fun medicamentoQueGeraDoses_naoTemMotivoDeDescarte() {
        val d = MedicationDiagnostics.inspect(med(1, "Losartana", horarioDose = "08:00", frequencia = 12, dataInicio = "2026-11-01"), hoje)
        assertEquals(2, d.dosesPrevistasNoDia)
        assertEquals(listOf("08:00", "20:00"), d.horariosCalculados)
        assertNull(d.motivoDescartado)
    }

    @Test
    fun encerrado_explicaQueDataFimJaPassou() {
        val d = MedicationDiagnostics.inspect(
            med(2, "Amoxicilina", horarioDose = "11:00", frequencia = 12, dataInicio = "2026-11-01", dataFim = "2026-11-20"), hoje
        )
        assertEquals(0, d.dosesPrevistasNoDia)
        assertTrue(d.motivoDescartado!!.contains("encerrado"))
    }

    @Test
    fun aindaNaoComecou_explicaDataDeInicio() {
        val d = MedicationDiagnostics.inspect(med(3, "X", horarioDose = "08:00", frequencia = 12, dataInicio = "2026-12-01"), hoje)
        assertTrue(d.motivoDescartado!!.contains("só começa em 2026-12-01"))
    }

    @Test
    fun inativo_explicaOMotivo() {
        val d = MedicationDiagnostics.inspect(med(4, "X", horarioDose = "08:00", frequencia = 12, dataInicio = "2026-11-01", ativo = false), hoje)
        assertTrue(d.motivoDescartado!!.contains("inativo"))
    }

    @Test
    fun semHorarioNenhum_explicaOMotivo() {
        val d = MedicationDiagnostics.inspect(med(5, "X"), hoje)
        assertNotNull(d.motivoDescartado)
        assertEquals(DoseScheduleCalculator.PLANO_NENHUM, d.plano)
    }

    @Test
    fun telaRemedios_podeMostrarHorariosQueSoExistemComoPropriedadeCalculada() {
        // horarios vazio no Room; a tela Remédios monta 06:20/14:20/22:20 a partir de horarioDose + frequência.
        val d = MedicationDiagnostics.inspect(med(6, "Dipirona", horarioDose = "06:20", frequencia = 8, dataInicio = null), hoje)
        assertTrue(d.horariosArmazenados.isEmpty())
        assertEquals(listOf("06:20", "14:20", "22:20"), d.horariosExibidosNaTelaRemedios)
        assertEquals(listOf("06:20", "14:20", "22:20"), d.horariosCalculados)
    }

    @Test
    fun relatorio_comparaPacientesIdsEContagens() {
        val remedios = listOf(med(1, "A", horarioDose = "08:00", frequencia = 24, dataInicio = "2026-11-01"), med(2, "B", horarioDose = "09:00", frequencia = 24, dataInicio = "2026-11-01"))
        val home = listOf(remedios[0])
        val texto = MedicationDiagnostics.report(
            day = hoje,
            patientIdRemedios = 1,
            patientIdHome = 1,
            roomPorUsuario = mapOf(1 to 2, 7 to 1),
            medicamentosRemedios = remedios,
            medicamentosHome = home
        )
        assertTrue(texto.contains("PATIENT ID DA TELA REMÉDIOS: 1"))
        assertTrue(texto.contains("PATIENT ID DA HOME: 1"))
        assertTrue(texto.contains("QUANTIDADE DE MEDICAMENTOS NO ROOM (todos os usuários): 3"))
        assertTrue(texto.contains("Ids só na Remédios: [2]"))
        assertTrue(texto.contains("QUANTIDADE DE MEDICAMENTOS QUE GERAM DOSES HOJE: 1"))
    }
}

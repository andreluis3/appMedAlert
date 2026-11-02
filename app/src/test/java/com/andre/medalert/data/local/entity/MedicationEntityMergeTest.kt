package com.andre.medalert.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Testes puros (sem Android) da regra que protege os dados locais no refresh. */
class MedicationEntityMergeTest {

    private val local = MedicationEntity(
        id = 7,
        userId = 3,
        nome = "Losartana",
        dosagem = "50 mg",
        horarios = "08:00,16:00",
        dataInicio = "2026-10-30 08:00:00-03:00",
        dataFim = "2026-11-30 08:00:00-03:00",
        dosagemValor = 50,
        frequencia = 8,
        quantidade = 1,
        unidadeDosagem = "mg",
        unidadeQuantidade = "comprimido",
        isSynced = true,
        remoteId = 3,
        estoque = 20,
        estoqueMinimo = 5,
        observacoes = "após o café",
        ativo = true
    )

    /** Resposta resumida (servidor antigo): só nome, dosagem texto e horários. */
    private fun resumo(dosagem: String = "50 mg") = MedicationEntity(
        userId = 3, nome = "Losartana", dosagem = dosagem,
        horarios = "05:00,13:00,21:00", isSynced = true, remoteId = 3
    )

    /** Resposta completa (GET novo): traz frequencia, datas só com o dia, etc. */
    private fun completa(
        dataInicio: String? = "2026-10-30T00:00:00.000Z",
        estoque: Int? = 20
    ) = MedicationEntity(
        userId = 3, nome = "Losartana", dosagem = "50 mg", horarios = "05:00,13:00,21:00",
        dataInicio = dataInicio, dataFim = "2026-11-30T00:00:00.000Z",
        dosagemValor = 50, frequencia = 8, quantidade = 1,
        unidadeDosagem = "mg", unidadeQuantidade = "comprimido",
        estoque = estoque, estoqueMinimo = 5, observacoes = null, ativo = true,
        isSynced = true, remoteId = 3
    )

    @Test
    fun semRegistroAnterior_devolveOProprioRegistro() {
        val novo = completa()
        assertSame(novo, novo.preservingDetailsFrom(null))
    }

    @Test
    fun respostaResumida_naoApagaDetalhesLocais() {
        val r = resumo().preservingDetailsFrom(local)
        assertEquals(7L, r.id)
        assertEquals("05:00,13:00,21:00", r.horarios) // vem da API
        assertEquals(8, r.frequencia)
        assertEquals(1, r.quantidade)
        assertEquals("2026-10-30 08:00:00-03:00", r.dataInicio)
        assertEquals(20, r.estoque)
        assertEquals("após o café", r.observacoes)
        assertEquals(50, r.dosagemValor)
        assertEquals("mg", r.unidadeDosagem)
    }

    @Test
    fun respostaResumida_comDosagemDiferente_descartaValorDesatualizado() {
        val r = resumo(dosagem = "100 mg").preservingDetailsFrom(local)
        assertNull(r.dosagemValor)
        assertNull(r.unidadeDosagem)
        assertEquals(8, r.frequencia) // o resto continua
    }

    @Test
    fun respostaCompleta_mantemIdLocalEHoraDaPrimeiraDose_quandoODiaEIgual() {
        val r = completa().preservingDetailsFrom(local)
        assertEquals(7L, r.id)
        assertEquals("2026-10-30 08:00:00-03:00", r.dataInicio) // hora local preservada
        assertEquals("2026-11-30 08:00:00-03:00", r.dataFim)
    }

    @Test
    fun respostaCompleta_usaADataDaApi_quandoODiaMudou() {
        val r = completa(dataInicio = "2026-11-02T00:00:00.000Z").preservingDetailsFrom(local)
        assertEquals("2026-11-02T00:00:00.000Z", r.dataInicio)
    }

    @Test
    fun respostaCompleta_eFonteDaVerdade_inclusiveParaNulos() {
        val r = completa(estoque = null).preservingDetailsFrom(local)
        assertNull(r.estoque)          // apagado em outro aparelho: não ressuscita o valor antigo
        assertNull(r.observacoes)
    }
}

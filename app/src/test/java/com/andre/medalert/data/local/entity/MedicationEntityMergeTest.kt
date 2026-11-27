package com.andre.medalert.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Testes puros (sem Android) da regra que protege os dados locais no refresh.
 */
class MedicationEntityMergeTest {

    private val local = MedicationEntity(
        id = 7,
        userId = 3,
        nome = "Losartana",
        dosagem = "50 mg",
        horarios = "08:00,16:00",
        dataInicio = "2026-10-30 08:00:00-03:00",
        dataFim = "2026-11-30 08:00:00-03:00",
        dosagemValor = 50.0,
        frequencia = 8,
        quantidade = 1,
        unidadeDosagem = "mg",
        unidadeQuantidade = "comprimido",
        isSynced = true,
        remoteId = 3,
        observacoes = "após o café",
        ativo = true
    )

    /**
     * Resposta resumida (servidor antigo): só nome, dosagem texto e horários.
     */
    private fun resumo(dosagem: String = "50 mg") = MedicationEntity(
        userId = 3,
        nome = "Losartana",
        dosagem = dosagem,
        horarios = "05:00,13:00,21:00",
        isSynced = true,
        remoteId = 3
    )

    /**
     * Resposta completa (GET novo): traz frequência, datas e demais detalhes.
     */
    private fun completa(
        dataInicio: String? = "2026-10-30T00:00:00.000Z"
    ) = MedicationEntity(
        userId = 3,
        nome = "Losartana",
        dosagem = "50 mg",
        horarios = "05:00,13:00,21:00",
        dataInicio = dataInicio,
        dataFim = "2026-11-30T00:00:00.000Z",
        dosagemValor = 50.0,
        frequencia = 8,
        quantidade = 1,
        unidadeDosagem = "mg",
        unidadeQuantidade = "comprimido",
        observacoes = null,
        ativo = true,
        isSynced = true,
        remoteId = 3
    )

    @Test
    fun semRegistroAnterior_mantemTodosOsCamposEIniciaOAcompanhamentoAgora() {
        // Antes este teste exigia a MESMA instância, mas o código devolve uma cópia que ganha
        // acompanhadoDesde (o acompanhamento da adesão começa quando o aparelho conhece o remédio).
        val novo = completa()
        val agora = 1_700_000_000_000L
        val resultado = novo.preservingDetailsFrom(null, nowMillis = agora)
        assertEquals(agora, resultado.acompanhadoDesde)
        assertEquals(novo.copy(acompanhadoDesde = agora), resultado)
    }

    @Test
    fun respostaResumida_naoApagaDetalhesLocais() {
        val r = resumo().preservingDetailsFrom(local)

        assertEquals(7L, r.id)
        assertEquals("05:00,13:00,21:00", r.horarios)
        assertEquals(8, r.frequencia)
        assertEquals(1, r.quantidade)
        assertEquals("2026-10-30 08:00:00-03:00", r.dataInicio)
        assertEquals("após o café", r.observacoes)
        assertEquals(50.0, r.dosagemValor!!, 0.0)
        assertEquals("mg", r.unidadeDosagem)
        assertEquals("comprimido", r.unidadeQuantidade)
    }

    @Test
    fun respostaResumida_comDosagemDiferente_descartaValorDesatualizado() {
        val r = resumo(dosagem = "100 mg").preservingDetailsFrom(local)

        assertNull(r.dosagemValor)
        assertNull(r.unidadeDosagem)
        assertEquals(8, r.frequencia)
    }

    @Test
    fun respostaCompleta_mantemIdLocalEHoraDaPrimeiraDose_quandoODiaEIgual() {
        val r = completa().preservingDetailsFrom(local)

        assertEquals(7L, r.id)
        assertEquals("2026-10-30 08:00:00-03:00", r.dataInicio)
        assertEquals("2026-11-30 08:00:00-03:00", r.dataFim)
    }

    @Test
    fun respostaCompleta_usaADataDaApi_quandoODiaMudou() {
        val r = completa(
            dataInicio = "2026-11-02T00:00:00.000Z"
        ).preservingDetailsFrom(local)

        assertEquals("2026-11-02T00:00:00.000Z", r.dataInicio)
    }

    @Test
    fun respostaCompleta_comValoresNulos_mantemObservacoesLocais() {
        val r = completa().preservingDetailsFrom(local)

        assertEquals("após o café", r.observacoes)
        assertEquals(50.0, r.dosagemValor!!, 0.0)
        assertEquals("mg", r.unidadeDosagem)
    }
}
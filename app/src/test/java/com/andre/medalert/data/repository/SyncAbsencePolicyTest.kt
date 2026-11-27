package com.andre.medalert.data.repository

import com.andre.medalert.data.repository.SyncAbsencePolicy.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAbsencePolicyTest {

    private val hora = 60L * 60 * 1000
    private val t0 = 1_000_000_000_000L

    private fun rodada(
        existentes: List<Int>,
        incoming: Set<Int>,
        estado: Map<Int, Entry>,
        agora: Long
    ) = SyncAbsencePolicy.decide(existentes, incoming, estado, agora)

    @Test
    fun listaCompleta_naoAbreQuarentena() {
        val d = rodada(listOf(1, 2, 3), setOf(1, 2, 3), emptyMap(), t0)
        assertTrue(d.confirmedAbsentRemoteIds.isEmpty())
        assertTrue(d.nextState.isEmpty())
    }

    @Test
    fun listaParcial_primeiraVez_entraEmQuarentenaESoApagaDepoisDeVariasConsultasEDeHoras() {
        val existentes = listOf(1, 2, 3)
        // Consulta 1: só o 1 voltou.
        var d = rodada(existentes, setOf(1), emptyMap(), t0)
        assertTrue(d.confirmedAbsentRemoteIds.isEmpty())
        assertEquals(setOf(2, 3), d.nextState.keys)
        // Consulta 2 e 3 logo em seguida: 3 ausências, mas ainda dentro da carência de 6 h.
        d = rodada(existentes, setOf(1), d.nextState, t0 + 1 * hora)
        d = rodada(existentes, setOf(1), d.nextState, t0 + 2 * hora)
        assertTrue("ainda dentro da carência", d.confirmedAbsentRemoteIds.isEmpty())
        // Passadas 6 h e com 3+ ausências seguidas: confirmado.
        d = rodada(existentes, setOf(1), d.nextState, t0 + 6 * hora)
        assertEquals(setOf(2, 3), d.confirmedAbsentRemoteIds)
        assertTrue(d.nextState.isEmpty())
    }

    @Test
    fun exclusaoLegitima_naoFicaBloqueadaParaSempre() {
        // Excluído em outro aparelho: some da lista em todas as consultas seguintes.
        val existentes = listOf(1, 2)
        var estado = emptyMap<Int, Entry>()
        var confirmado = emptySet<Int>()
        var agora = t0
        repeat(40) {
            val d = rodada(existentes, setOf(1), estado, agora)
            estado = d.nextState
            confirmado = confirmado + d.confirmedAbsentRemoteIds
            agora += 30 * 60 * 1000 // uma consulta a cada 30 min
        }
        assertEquals(setOf(2), confirmado)
    }

    @Test
    fun voltouAAparecer_zeraAContagem() {
        val existentes = listOf(1, 2)
        var d = rodada(existentes, setOf(1), emptyMap(), t0)
        d = rodada(existentes, setOf(1), d.nextState, t0 + hora)
        assertEquals(2, d.nextState[2]!!.misses)
        // O 2 volta a vir na API.
        d = rodada(existentes, setOf(1, 2), d.nextState, t0 + 2 * hora)
        assertTrue(d.nextState.isEmpty())
        // Some de novo: recomeça do 1.
        d = rodada(existentes, setOf(1), d.nextState, t0 + 3 * hora)
        assertEquals(1, d.nextState[2]!!.misses)
        assertEquals(t0 + 3 * hora, d.nextState[2]!!.firstMissingAtMillis)
    }

    @Test
    fun respostaVazia_exigeCarenciaMaiorDe24h() {
        val existentes = listOf(1, 2, 3)
        var d = rodada(existentes, emptySet(), emptyMap(), t0)
        d = rodada(existentes, emptySet(), d.nextState, t0 + 7 * hora)
        d = rodada(existentes, emptySet(), d.nextState, t0 + 8 * hora)
        assertTrue("6 h não bastam para lista vazia", d.confirmedAbsentRemoteIds.isEmpty())
        d = rodada(existentes, emptySet(), d.nextState, t0 + 24 * hora)
        assertEquals(setOf(1, 2, 3), d.confirmedAbsentRemoteIds)
    }

    @Test
    fun apiIndisponivel_naoChegaAquiEnadaMuda() {
        // Sem resposta não há decisão: o repositório nem chama decide(). O estado só muda com resposta.
        val estado = mapOf(2 to Entry(2, t0))
        val d = rodada(emptyList(), emptySet(), estado, t0 + hora)
        assertTrue(d.confirmedAbsentRemoteIds.isEmpty())
        assertTrue("estado de quem já nem existe no Room é descartado", d.nextState.isEmpty())
    }

    @Test
    fun medicamentoOffline_semRemoteId_nemEntraNaConta() {
        // existingRemoteIds só traz sincronizados com remoteId; os offline não existem para a política.
        val d = rodada(emptyList(), setOf(5), emptyMap(), t0)
        assertTrue(d.confirmedAbsentRemoteIds.isEmpty())
    }

    @Test
    fun codificacao_idaEVolta_eTextoRuimNaoQuebra() {
        val estado = mapOf(2 to Entry(2, 123L), 3 to Entry(1, 456L))
        assertEquals(estado, SyncAbsencePolicy.decode(SyncAbsencePolicy.encode(estado)))
        assertTrue(SyncAbsencePolicy.decode(null).isEmpty())
        assertTrue(SyncAbsencePolicy.decode("lixo,::,1:x:y").isEmpty())
    }
}

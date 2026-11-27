package com.andre.medalert.data.local.dao

import com.andre.medalert.data.local.dao.ReplaceSyncedRules.ExistingRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaceSyncedRulesTest {

    private val amox = ExistingRow(id = 1, remoteId = 101)
    private val dipirona = ExistingRow(id = 2, remoteId = 102)
    private val levo = ExistingRow(id = 3, remoteId = 103)
    private val todos = listOf(amox, dipirona, levo)

    @Test
    fun respostaVazia_semConfirmacao_naoApagaNada() {
        assertTrue(
            ReplaceSyncedRules.idsToDelete(todos, keepIds = emptySet(), incomingRemoteIds = emptySet(), confirmedAbsentRemoteIds = emptySet()).isEmpty()
        )
    }

    @Test
    fun respostaParcial_semConfirmacao_mantemOsDoisQueSumiram() {
        // Room: Amoxicilina, Dipirona, Levofloxacino. API: só Amoxicilina.
        val apagar = ReplaceSyncedRules.idsToDelete(
            todos, keepIds = setOf(1L), incomingRemoteIds = setOf(101), confirmedAbsentRemoteIds = emptySet()
        )
        assertTrue(apagar.isEmpty())
    }

    @Test
    fun ausenciaConfirmada_apagaSoOsConfirmados() {
        val apagar = ReplaceSyncedRules.idsToDelete(
            todos, keepIds = setOf(1L), incomingRemoteIds = setOf(101), confirmedAbsentRemoteIds = setOf(103)
        )
        assertEquals(listOf(3L), apagar)
    }

    @Test
    fun respostaCompleta_naoRemoveNada() {
        assertTrue(
            ReplaceSyncedRules.idsToDelete(todos, keepIds = setOf(1L, 2L, 3L), incomingRemoteIds = setOf(101, 102, 103), confirmedAbsentRemoteIds = emptySet()).isEmpty()
        )
    }

    @Test
    fun sobraDuplicadaDeUmRemoteIdQueAApiDevolveu_continuaSendoLimpa() {
        val duplicada = ExistingRow(id = 9, remoteId = 101)
        val apagar = ReplaceSyncedRules.idsToDelete(
            listOf(amox, duplicada), keepIds = setOf(1L), incomingRemoteIds = setOf(101), confirmedAbsentRemoteIds = emptySet()
        )
        assertEquals(listOf(9L), apagar)
    }

    @Test
    fun linhaSincronizadaSemRemoteId_nuncaEApagadaPorAusencia() {
        val semRemoto = ExistingRow(id = 7, remoteId = null)
        assertTrue(
            ReplaceSyncedRules.idsToDelete(listOf(semRemoto), keepIds = emptySet(), incomingRemoteIds = setOf(101), confirmedAbsentRemoteIds = setOf(101)).isEmpty()
        )
    }
}

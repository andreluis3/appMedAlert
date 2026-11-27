package com.andre.medalert.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseMatcherTest {

    private val h = 60L * 60 * 1000
    private fun dose(scheduled: Long, taken: Long = scheduled + 1) = TakenDose(1, scheduled, taken)

    @Test
    fun toleranciaEMetadeDoMenorIntervalo_comTeto() {
        assertEquals(4 * h, DoseMatcher.toleranceFor(listOf(0, 8 * h, 16 * h)))
        assertEquals(6 * h, DoseMatcher.toleranceFor(listOf(0, 12 * h)))
        assertEquals(6 * h, DoseMatcher.toleranceFor(listOf(0, 24 * h)))   // teto
        assertEquals(h / 2, DoseMatcher.toleranceFor(listOf(0, h, 2 * h))) // 1 em 1 hora
        assertEquals(6 * h, DoseMatcher.toleranceFor(listOf(5 * h)))       // uma dose só
    }

    @Test
    fun horarioIdentico_casaDiretoESemRegistroNaoAparece() {
        val m = DoseMatcher.match(listOf(0, 8 * h), listOf(dose(8 * h)))
        assertEquals(setOf(8 * h), m.keys)
    }

    @Test
    fun exatoTemPrioridadeSobreProximidade() {
        // O registro de 8 h é exato para a dose de 8 h; a dose de 0 h NÃO pode roubá-lo por estar a 8 h... nem a 4 h.
        val m = DoseMatcher.match(listOf(0, 8 * h), listOf(dose(8 * h)))
        assertTrue(0L !in m.keys)
    }

    @Test
    fun foraDaTolerancia_naoCasa() {
        val m = DoseMatcher.match(listOf(0, 8 * h, 16 * h), listOf(dose(4 * h + 1)))
        // 4 h + 1 ms está a mais de 4 h de 0 e a menos de 4 h de 8 h => casa com a de 8 h.
        assertEquals(setOf(8 * h), m.keys)
        val longe = DoseMatcher.match(listOf(0, 8 * h), listOf(dose(40 * h)))
        assertTrue(longe.isEmpty())
    }

    @Test
    fun doisRegistrosDeCadaDose_cadaUmCasaUmaVez() {
        val m = DoseMatcher.match(listOf(0, 8 * h), listOf(dose(h), dose(9 * h)))
        assertEquals(2, m.size)
        assertEquals(h, m[0L]!!.scheduledAtMillis)
        assertEquals(9 * h, m[8 * h]!!.scheduledAtMillis)
    }

    @Test
    fun listasVazias_semErro() {
        assertTrue(DoseMatcher.match(emptyList(), listOf(dose(0))).isEmpty())
        assertTrue(DoseMatcher.match(listOf(0), emptyList()).isEmpty())
    }
}

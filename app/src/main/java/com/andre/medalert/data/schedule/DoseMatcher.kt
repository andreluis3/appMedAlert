package com.andre.medalert.data.schedule

import kotlin.math.abs

/**
 * Casa as doses PREVISTAS de UM medicamento com os registros de dose tomada.
 *
 * Antes a chave era (medicamento, milissegundo exato). Qualquer diferença entre o horário
 * gravado no histórico e o horário que o cálculo gera hoje (hora editada, fuso, registro vindo
 * do servidor em UTC) fazia uma dose tomada virar "não tomada". Agora:
 *
 * 1. Primeiro casa os horários idênticos (comportamento anterior).
 * 2. Depois, cada dose sem par recebe o registro livre MAIS PRÓXIMO, desde que a distância seja
 *    menor que a tolerância. Um registro nunca casa com duas doses.
 *
 * A tolerância é metade do menor intervalo entre doses (para nunca confundir duas doses
 * vizinhas), limitada a [MAX_TOLERANCE_MILLIS]. Com uma dose só na janela, vale o limite.
 */
object DoseMatcher {

    const val MAX_TOLERANCE_MILLIS: Long = 6L * 60 * 60 * 1000

    fun toleranceFor(scheduledMillis: List<Long>): Long {
        if (scheduledMillis.size < 2) return MAX_TOLERANCE_MILLIS
        val minGap = scheduledMillis.sorted().zipWithNext { a, b -> b - a }.filter { it > 0 }.minOrNull()
            ?: return MAX_TOLERANCE_MILLIS
        return minOf(minGap / 2, MAX_TOLERANCE_MILLIS)
    }

    /** dose prevista (millis) -> registro que a cobre. Doses sem registro não aparecem no mapa. */
    fun match(scheduledMillis: List<Long>, records: List<TakenDose>): Map<Long, TakenDose> {
        if (scheduledMillis.isEmpty() || records.isEmpty()) return emptyMap()

        val result = LinkedHashMap<Long, TakenDose>()
        val free = records.toMutableList()

        // 1) horário idêntico
        scheduledMillis.forEach { scheduled ->
            val exact = free.indexOfFirst { it.scheduledAtMillis == scheduled }
            if (exact >= 0) result[scheduled] = free.removeAt(exact)
        }

        // 2) o registro livre mais próximo, dentro da tolerância
        val tolerance = toleranceFor(scheduledMillis)
        scheduledMillis.filter { it !in result }.forEach { scheduled ->
            val best = free.minByOrNull { abs(it.scheduledAtMillis - scheduled) } ?: return@forEach
            if (abs(best.scheduledAtMillis - scheduled) <= tolerance) {
                result[scheduled] = best
                free.remove(best)
            }
        }
        return result
    }
}

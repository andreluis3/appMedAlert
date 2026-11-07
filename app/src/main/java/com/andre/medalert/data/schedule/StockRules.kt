package com.andre.medalert.data.schedule

/** Regras de estoque (puras, testáveis em JVM). */
object StockRules {

    /** Estoque exibido: base menos consumo local, nunca negativo; null se não há controle. */
    fun effective(base: Int?, consumed: Int): Int? =
        base?.let { (it - consumed).coerceAtLeast(0) }

    /**
     * Avisa uma única vez por "descida": quando o estoque atravessa o mínimo (estava acima e
     * ficou igual ou abaixo) ou quando acaba (chegou a 0). Sem controle de estoque, não avisa.
     */
    fun shouldAlert(before: Int?, after: Int?, minimum: Int?): Boolean {
        if (before == null || after == null || after >= before) return false
        val acabou = after == 0 && before > 0
        val cruzouMinimo = minimum != null && before > minimum && after <= minimum
        return acabou || cruzouMinimo
    }
}

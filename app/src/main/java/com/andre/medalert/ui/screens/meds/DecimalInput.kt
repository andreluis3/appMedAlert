package com.andre.medalert.ui.screens.meds

// ---------------------------------------------------------------------------
// Entrada de números decimais (dosagem). Compartilhado entre cadastro e edição:
// ambos devem usar estas duas funções para aceitar 0,5 / 2.5 sem perder decimais.
// ---------------------------------------------------------------------------

/**
 * Filtra o texto digitado para um decimal válido:
 * - só dígitos 0-9 e UM separador (aceita "." ou ","; é exibido sempre como ",");
 * - até [maxInteiros] dígitos antes e [maxCasas] depois do separador;
 * - separador digitado primeiro vira "0,".
 * Ex.: "2.5" -> "2,5"; "abc1,2,3" -> "1,2"; "," -> "0,"; "1234567" -> "123456".
 */
internal fun filtrarDecimal(
    entrada: String,
    maxInteiros: Int = 6,
    maxCasas: Int = 3
): String {
    val inteiros = StringBuilder()
    val decimais = StringBuilder()
    var temSeparador = false

    for (c in entrada) {
        when {
            c in '0'..'9' && !temSeparador ->
                if (inteiros.length < maxInteiros) inteiros.append(c)

            c in '0'..'9' ->
                if (decimais.length < maxCasas) decimais.append(c)

            (c == ',' || c == '.') && !temSeparador ->
                temSeparador = true
        }
    }

    if (!temSeparador) return inteiros.toString()

    val parteInteira = if (inteiros.isEmpty()) "0" else inteiros.toString()
    return "$parteInteira,$decimais"
}

/**
 * Converte o texto do campo ("2,5", "2.5", "50") em Double.
 * Devolve null se estiver vazio, inválido ou não finito. Não arredonda nem converte para Int.
 */
internal fun parseDecimalOrNull(texto: String): Double? {
    val limpo = texto.trim().replace(',', '.')
    if (limpo.isEmpty()) return null
    val valor = limpo.toDoubleOrNull() ?: return null
    return valor.takeIf { it.isFinite() }
}
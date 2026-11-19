package com.andre.medalert.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AdesaoDiaDto(
    val adesao: String,
    val diaSem: String
)

data class AdesaoRemedioDto(
    val remedio: String,
    val dosesEsp: Int,
    val dosesTom: Int,
    val adesao: String
)

/**
 * O backend atual escreve "porcentgem" (sem o "a"). Aceitamos as duas grafias: se o backend
 * corrigir o nome no futuro, o app continua funcionando sem nova versão.
 */
data class HorarioEsquecidoDto(
    val horario: String,
    val qtd: Int,
    @SerializedName(value = "porcentagem", alternate = ["porcentgem"])
    val porcentagem: String
)

data class DashboardResponse(
    val adesao: String,
    val dosesEsp: Int,
    val dosesTom: Int,
    val dosesAtr: Int,
    val dosesNT: Int,
    val adesaoD: List<AdesaoDiaDto>,
    val adesaoR: List<AdesaoRemedioDto>,
    val horariosEsq: List<HorarioEsquecidoDto>
)

/**
 * Converte o percentual que veio da API ("23.6", "50.0", "22", "22%") em número.
 * Devolve null se estiver ausente ou ilegível: quem chama decide o que mostrar, e nunca
 * deve tratar null como 0%.
 */
fun parsePercentOrNull(raw: String?): Float? =
    raw
        ?.trim()
        ?.removeSuffix("%")
        ?.trim()
        ?.replace(',', '.')
        ?.toFloatOrNull()
        ?.takeIf { it.isFinite() }

/** O Gson ignora a nulidade do Kotlin: campo ausente chega como null mesmo em "String". */
private fun isMissing(value: Any?): Boolean = value == null

/**
 * Confere se a resposta do servidor tem o mínimo para ser mostrada. Devolve o motivo (texto
 * curto, sem dados clínicos) ou null se está válida.
 *
 * - Sem doses esperadas (dosesEsp == 0) a tela mostra "sem dados" e não usa o restante: não valida.
 * - Com doses esperadas, a adesão geral precisa ser um número e as listas precisam existir.
 * - Percentual ausente/ilegível DENTRO de um item não invalida a resposta: a tela mostra "—".
 *
 * Limite: dosesEsp, dosesTom, dosesAtr e dosesNT são Int; se o servidor omitir um deles, o Gson
 * entrega 0 e isso não dá para distinguir de um 0 verdadeiro.
 */
fun DashboardResponse.validationProblem(): String? {
    if (dosesEsp <= 0) return null
    if (isMissing(adesaoD)) return "adesaoD ausente"
    if (isMissing(adesaoR)) return "adesaoR ausente"
    if (isMissing(horariosEsq)) return "horariosEsq ausente"
    if (adesaoD.any { isMissing(it) || isMissing(it.diaSem) }) return "item de adesaoD sem diaSem"
    if (adesaoR.any { isMissing(it) || isMissing(it.remedio) }) return "item de adesaoR sem remedio"
    if (horariosEsq.any { isMissing(it) || isMissing(it.horario) }) return "item de horariosEsq sem horario"
    if (parsePercentOrNull(adesao) == null) return "adesao geral ausente ou ilegível"
    return null
}
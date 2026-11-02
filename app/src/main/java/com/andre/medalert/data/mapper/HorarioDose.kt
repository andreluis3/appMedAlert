package com.andre.medalert.data.mapper

import java.util.Locale

/**
 * Conversão ÚNICA do horário da dose entre o app e a API.
 *
 * Contrato observado no Postman:
 * - ENVIO:   horario_dose = "08:00"                      (HH:mm, sem fuso)
 * - RESPOSTA: horario_dose = "1970-01-01T11:00:00.000Z"  (coluna TIME do banco, em UTC)
 *
 * Regra adotada:
 * 1. No app e no Room o horário é SEMPRE "HH:mm" (hora de parede do paciente).
 * 2. Se a API devolver o ISO acima, pegamos HH:mm (UTC) e somamos [OFFSET_BACKEND_MINUTOS]
 *    para voltar ao horário do paciente. NUNCA usamos take(16) nem o fuso do aparelho:
 *    o app não sabe em que fuso o backend gravou, então o offset fica isolado nesta constante.
 * 3. Valor que já está em "HH:mm" passa sem alteração (conversão idempotente, então
 *    registros antigos e novos convivem no Room).
 * 4. Valor ausente ou em formato desconhecido devolve null: quem chama mantém o horário local.
 *
 * ATENÇÃO (validar no Postman): -180 vem de UMA amostra (08:00 -> 11:00Z). Antes de considerar
 * definitivo, teste 22:00 (esperado 01:00Z) e 02:00 (esperado 05:00Z). Se o backend usar outro
 * fuso, só esta constante muda.
 */
object HorarioDose {

    /** Diferença (minutos) entre o horário do paciente e o UTC gravado pelo backend: -03:00. */
    const val OFFSET_BACKEND_MINUTOS = -180

    private const val MINUTOS_DIA = 24 * 60

    private val HORA_LOCAL = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")

    private val HORA_ISO_UTC =
        Regex("""^\d{4}-\d{2}-\d{2}T([01]\d|2[0-3]):([0-5]\d)(?::[0-5]\d(?:\.\d+)?)?Z$""")

    /** true se [valor] está exatamente no formato "HH:mm" (00:00 a 23:59). */
    fun ehHoraLocal(valor: String?): Boolean = valor != null && HORA_LOCAL.matches(valor)

    /**
     * Devolve "HH:mm" a partir de "HH:mm" ou do ISO UTC da API; null se não reconhecer o formato.
     */
    fun normalizar(valor: String?): String? {
        val texto = valor?.trim()
        if (texto.isNullOrEmpty()) return null

        if (HORA_LOCAL.matches(texto)) return texto

        val partes = HORA_ISO_UTC.matchEntire(texto) ?: return null
        val utc = partes.groupValues[1].toInt() * 60 + partes.groupValues[2].toInt()
        val local = ((utc + OFFSET_BACKEND_MINUTOS) % MINUTOS_DIA + MINUTOS_DIA) % MINUTOS_DIA

        return String.format(Locale.US, "%02d:%02d", local / 60, local % 60)
    }
}
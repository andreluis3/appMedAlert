package com.andre.medalert.data.repository

/**
 * Quando um medicamento SINCRONIZADO que sumiu da resposta da API pode ser apagado do Room.
 *
 * Limite do contrato atual: GET /api/meds/{id} devolve só a lista. Não há total, página, ETag
 * nem versão, então uma lista parcial (bug do servidor, filtro, falha no meio) é indistinguível
 * de "o medicamento foi excluído em outro aparelho". O app NÃO inventa uma confirmação de
 * integridade. Em vez disso, só apaga o que continua ausente de forma CONSISTENTE:
 *
 * - ausente em pelo menos [MIN_MISSES] consultas seguidas, E
 * - ausente há pelo menos [GRACE_PARTIAL_MILLIS] (resposta com os demais itens), ou
 *   [GRACE_EMPTY_MILLIS] (resposta totalmente vazia, que é o caso mais suspeito).
 *
 * Voltou a aparecer na API: a contagem zera. Enquanto está "em quarentena" o medicamento
 * continua no Room e continua gerando alarme: perder uma dose é pior que tocar a mais.
 * Excluir pelo próprio aparelho não passa por aqui (remove a linha direto).
 *
 * Quando o servidor passar a informar integridade (total, versão), esta regra pode ser trocada
 * por uma confirmação direta.
 */
object SyncAbsencePolicy {

    const val MIN_MISSES = 3
    const val GRACE_PARTIAL_MILLIS: Long = 6L * 60 * 60 * 1000
    const val GRACE_EMPTY_MILLIS: Long = 24L * 60 * 60 * 1000

    data class Entry(val misses: Int, val firstMissingAtMillis: Long)

    data class Decision(
        /** remoteIds cuja ausência já está confirmada: podem ser apagados agora. */
        val confirmedAbsentRemoteIds: Set<Int>,
        /** Estado a guardar: só quem continua ausente e AINDA em quarentena. */
        val nextState: Map<Int, Entry>
    )

    /**
     * @param existingRemoteIds remoteIds dos medicamentos sincronizados que o Room tem hoje
     * @param incomingRemoteIds remoteIds que a API acabou de devolver
     * @param previous estado guardado na consulta anterior
     */
    fun decide(
        existingRemoteIds: Collection<Int>,
        incomingRemoteIds: Set<Int>,
        previous: Map<Int, Entry>,
        nowMillis: Long
    ): Decision {
        val emptyResponse = incomingRemoteIds.isEmpty()
        val grace = if (emptyResponse) GRACE_EMPTY_MILLIS else GRACE_PARTIAL_MILLIS

        val confirmed = mutableSetOf<Int>()
        val next = mutableMapOf<Int, Entry>()

        existingRemoteIds.toSet().filter { it !in incomingRemoteIds }.forEach { remoteId ->
            val before = previous[remoteId]
            val entry = if (before == null) {
                Entry(misses = 1, firstMissingAtMillis = nowMillis)
            } else {
                Entry(misses = before.misses + 1, firstMissingAtMillis = before.firstMissingAtMillis)
            }
            if (entry.misses >= MIN_MISSES && nowMillis - entry.firstMissingAtMillis >= grace) {
                confirmed += remoteId
            } else {
                next[remoteId] = entry
            }
        }
        return Decision(confirmed, next)
    }

    // ------------------------------------------------------------------ persistência (texto)

    /** "remoteId:misses:primeiraAusenciaMillis" separados por vírgula. */
    fun encode(state: Map<Int, Entry>): String =
        state.entries.joinToString(",") { (id, e) -> "$id:${e.misses}:${e.firstMissingAtMillis}" }

    /** Texto ilegível vira "sem estado" (recomeça a contagem: nunca apaga por causa disso). */
    fun decode(raw: String?): Map<Int, Entry> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<Int, Entry>()
        raw.split(",").forEach { part ->
            val pieces = part.split(":")
            val id = pieces.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val misses = pieces.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val first = pieces.getOrNull(2)?.toLongOrNull() ?: return@forEach
            result[id] = Entry(misses, first)
        }
        return result
    }
}

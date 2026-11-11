package com.andre.medalert.data.model

/**
 * Convite de vínculo recebido pelo CUIDADOR (push disparado por POST /api/users/requestCuidador).
 *
 * O backend só envia `data = { pacientId }`. Por isso só o ID do paciente é guardado: nome e
 * e-mail NÃO estão no payload e não são inventados. `pacienteId` é o ID do PACIENTE (tabela
 * usuarios), nunca o do cuidador (tabela cuidadores): são tabelas diferentes e os números podem
 * coincidir sem que isso seja um erro.
 */
data class PendingInvite(
    val pacienteId: Int,
    val receivedAtMillis: Long
)

/**
 * Codificação/decodificação da lista de convites pendentes e regras de duplicidade.
 * Sem dependência de Android (testável em JVM).
 *
 * Formato salvo no DataStore: "pacienteId:millis;pacienteId:millis" (mais recente primeiro).
 */
object PendingInviteCodec {

    /** Limite de convites guardados (evita crescer sem fim se alguém reenviar convites). */
    const val MAX_INVITES = 10

    /** Valor de `pacientId` vindo do FCM (sempre texto). Só aceita inteiro positivo. */
    fun parsePacienteId(raw: String?): Int? =
        raw?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    fun encode(invites: List<PendingInvite>): String =
        invites.joinToString(";") { "${it.pacienteId}:${it.receivedAtMillis}" }

    fun decode(raw: String?): List<PendingInvite> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";")
            .mapNotNull { part ->
                val pieces = part.split(":")
                if (pieces.size != 2) return@mapNotNull null
                val id = pieces[0].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val at = pieces[1].toLongOrNull() ?: return@mapNotNull null
                PendingInvite(pacienteId = id, receivedAtMillis = at)
            }
            .distinctBy { it.pacienteId }
    }

    /** Lista sem o convite desse paciente (aceito ou recusado). */
    fun remove(current: List<PendingInvite>, pacienteId: Int): List<PendingInvite> =
        current.filterNot { it.pacienteId == pacienteId }

    /** Coloca o convite no topo. Se o paciente já tem convite pendente, a lista não muda. */
    fun add(current: List<PendingInvite>, invite: PendingInvite): List<PendingInvite> =
        if (current.any { it.pacienteId == invite.pacienteId }) {
            current
        } else {
            (listOf(invite) + current).take(MAX_INVITES)
        }
}
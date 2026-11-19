package com.andre.medalert.data.model

/**
 * Convite que o PACIENTE enviou a um cuidador (POST /api/users/requestCuidador), guardado só
 * neste aparelho. A API não devolve nada sobre o convite e não avisa o paciente quando o cuidador
 * aceita, então isto é um REGISTRO DE ENVIO: nunca prova que existe vínculo.
 */
data class SentInvite(
    val nome: String,
    val email: String,
    val sentAtMillis: Long
)

/**
 * Texto <-> lista de convites. Uma linha por convite, campos separados por TAB
 * ("nome \t email \t millis"). Tab e quebra de linha são trocados por espaço ao gravar, então não
 * dá para quebrar o formato. Linhas inválidas são ignoradas (nunca derrubam a tela).
 * Sem dependência de Android (testável em JVM).
 */
object SentInviteCodec {

    private const val FIELD = '\t'
    private const val LINE = '\n'

    fun encode(list: List<SentInvite>): String =
        list.joinToString(LINE.toString()) {
            "${clean(it.nome)}$FIELD${clean(it.email)}$FIELD${it.sentAtMillis}"
        }

    fun decode(raw: String?): List<SentInvite> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(LINE).mapNotNull { line ->
            val parts = line.split(FIELD)
            if (parts.size != 3) return@mapNotNull null
            val email = parts[1].trim()
            val millis = parts[2].trim().toLongOrNull() ?: return@mapNotNull null
            if (email.isEmpty()) null else SentInvite(parts[0].trim(), email, millis)
        }
    }

    /** Convite novo no topo; o mesmo e-mail (sem distinguir maiúsculas) substitui o antigo. */
    fun add(current: List<SentInvite>, invite: SentInvite): List<SentInvite> =
        listOf(invite) + current.filterNot { it.email.equals(invite.email, ignoreCase = true) }

    fun remove(current: List<SentInvite>, email: String): List<SentInvite> =
        current.filterNot { it.email.equals(email.trim(), ignoreCase = true) }

    private fun clean(text: String): String =
        text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
}
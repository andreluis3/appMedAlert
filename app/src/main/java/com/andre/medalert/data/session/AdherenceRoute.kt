package com.andre.medalert.data.session

/**
 * Qual endpoint de adesão a sessão deve usar (e se deve usar algum).
 *
 * - Paciente: GET api/historys/dashboard/{usuarioId}, com o id do PRÓPRIO paciente.
 * - Cuidador COM vínculo confirmado: GET api/historys/dashboard/cuid/{cuidadorId}, com o id do
 *   PRÓPRIO CUIDADOR (nunca o do paciente).
 * - Cuidador sem vínculo, sem sessão ou com vínculo inconsistente: nenhuma consulta.
 */
sealed interface AdherenceRoute {

    /** Rotas que consultam o dashboard remoto. */
    sealed interface Remote : AdherenceRoute {
        /** Paciente dono dos dados mostrados (o próprio, ou o vinculado ao cuidador). */
        val patientId: Int

        /** Id que vai NA URL do dashboard: do paciente (paciente) ou do cuidador (cuidador). */
        val requestId: Int

        /** Caminho relativo à base da API (só para log e testes). */
        val path: String
    }

    data class Patient(override val patientId: Int) : Remote {
        override val requestId: Int get() = patientId
        override val path: String get() = "api/historys/dashboard/$requestId"
    }

    /**
     * [caregiverId] = id do cuidador autenticado: é o que vai na URL. [patientId] = paciente
     * vinculado confirmado na sessão: serve só para saber de quem são os dados (logs e Room);
     * NUNCA vai na URL.
     */
    data class Caregiver(val caregiverId: Int, override val patientId: Int) : Remote {
        override val requestId: Int get() = caregiverId
        override val path: String get() = "api/historys/dashboard/cuid/$requestId"
    }

    /** Cuidador logado, ainda sem paciente vinculado: não consulta. */
    object AwaitingLink : AdherenceRoute

    /** Sem sessão válida: não consulta. */
    object NoSession : AdherenceRoute

    /** Cuidador cujo "paciente" é ele mesmo: dado inconsistente, nunca consulta. */
    object InvalidLink : AdherenceRoute
}

object AdherenceRouting {

    /**
     * O id da URL do cuidador é SEMPRE o [SessionIdentity.userId] (o usuário autenticado). O
     * vínculo ([SessionIdentity.patientId]) só existe com vínculo confirmado (login ou aceite
     * aprovado pelo servidor) e continua sendo exigido: cuidador sem vínculo não consulta. Ele
     * nunca é fixo, nunca é o id do cuidador e nunca vem de argumento de tela.
     */
    fun route(identity: SessionIdentity): AdherenceRoute {
        val userId = identity.userId ?: return AdherenceRoute.NoSession
        val patientId = identity.patientId

        if (identity.isCaregiver) {
            return when {
                patientId == null -> AdherenceRoute.AwaitingLink
                patientId == userId -> AdherenceRoute.InvalidLink
                else -> AdherenceRoute.Caregiver(caregiverId = userId, patientId = patientId)
            }
        }
        return if (patientId == null) AdherenceRoute.NoSession else AdherenceRoute.Patient(patientId)
    }
}
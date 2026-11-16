package com.andre.medalert.data.session

/**
 * "Quem está logado e de quem são os dados", sem os campos que não mudam essa identidade
 * (nome, e-mail, token). Duas sessões com o mesmo [SessionIdentity] mostram os mesmos dados.
 *
 * - [userId]: usuário autenticado (> 0) ou null sem sessão válida.
 * - [isCaregiver]: sessão de cuidador.
 * - [patientId]: paciente dono dos dados (ver [AccessPolicy.patientIdFor]); null sem sessão
 *   ou com cuidador ainda sem vínculo.
 */
data class SessionIdentity(
    val userId: Int?,
    val isCaregiver: Boolean,
    val patientId: Int?
) {
    val hasSession: Boolean get() = userId != null

    /** Cuidador logado que ainda não tem paciente vinculado. */
    val awaitingLink: Boolean get() = userId != null && isCaregiver && patientId == null
}

/**
 * Identidade da sessão. Estado inconsistente (sem userId válido) vira "sem sessão" e nunca
 * expõe um paciente-alvo.
 */
fun SessionInfo.identity(): SessionIdentity {
    val caregiver = AccessPolicy.isCaregiver(tipo)
    val uid = userId?.takeIf { it > 0 }
        ?: return SessionIdentity(userId = null, isCaregiver = caregiver, patientId = null)
    return SessionIdentity(
        userId = uid,
        isCaregiver = caregiver,
        patientId = AccessPolicy.patientIdFor(tipo, uid, pacienteId)
    )
}
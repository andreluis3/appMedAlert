package com.andre.medalert.data.session

/** Situação do usuário da sessão em relação ao paciente dono dos dados. */
enum class LinkState {
    /** Sessão de paciente: ele é o dono dos próprios dados. */
    PATIENT,

    /** Cuidador logado COM paciente vinculado (pacienteId > 0). */
    CAREGIVER_LINKED,

    /** Cuidador logado, ainda sem paciente vinculado: não vê nem altera nada. */
    CAREGIVER_AWAITING_LINK,

    /** Sem sessão válida (logout, sessão expirada ou estado inconsistente). */
    NO_SESSION
}

/**
 * Nome do paciente acompanhado, guardado neste aparelho (só para cuidador).
 * Guarda também o [patientId] a que o nome pertence: se o vínculo mudar, o nome antigo não é
 * mostrado para o paciente novo.
 */
data class LinkedPatientProfile(
    val patientId: Int,
    val name: String
)

/**
 * Fonte central de "de quem são os dados e quem está mexendo neles". Sem dependência de Android
 * (testável em JVM). É calculado a partir da sessão do DataStore ([SessionInfo]) e do
 * [AccessPolicy]: não existe regra duplicada aqui.
 *
 * - [userId]: usuário autenticado (paciente ou cuidador).
 * - [patientId]: paciente DONO dos dados clínicos. Paciente = ele mesmo; cuidador = o paciente
 *   vinculado. null sem sessão ou com cuidador sem vínculo. Nunca é 0 nem negativo.
 * - [caregiverId]: id do cuidador logado; null quando a sessão é de paciente.
 * - [patientName]: nome do paciente quando conhecido; null caso contrário. NUNCA é o nome do
 *   cuidador. Para cuidador só existe depois que o backend disponibilizar o perfil do paciente e
 *   o nome for salvo (ver [PatientContextProvider.savePatientName]).
 */
data class PatientContext(
    val userId: Int?,
    val tipo: String,
    val patientId: Int?,
    val caregiverId: Int?,
    val linkState: LinkState,
    val patientName: String?
) {
    val isCaregiver: Boolean get() = AccessPolicy.isCaregiver(tipo)

    /** Cadastrar medicamento novo: só o paciente. Igual a [AccessPolicy.canAddMedications]. */
    val canAddMedications: Boolean
        get() = linkState == LinkState.PATIENT

    /**
     * Editar e excluir medicamentos do [patientId]: paciente e cuidador vinculado.
     * Igual a [AccessPolicy.canManageMedications]. NÃO inclui cadastrar.
     */
    val canManageMedications: Boolean
        get() = linkState == LinkState.PATIENT || linkState == LinkState.CAREGIVER_LINKED

    val isAwaitingLink: Boolean get() = linkState == LinkState.CAREGIVER_AWAITING_LINK

    companion object {

        val NONE = PatientContext(
            userId = null,
            tipo = AccessPolicy.TIPO_PACIENTE,
            patientId = null,
            caregiverId = null,
            linkState = LinkState.NO_SESSION,
            patientName = null
        )

        /**
         * @param ownName nome do próprio usuário logado (DataStore). Só é usado como nome do
         *   paciente quando o usuário É o paciente.
         * @param cachedPatient nome salvo do paciente vinculado ao cuidador; só vale se o
         *   [LinkedPatientProfile.patientId] for o do vínculo atual.
         */
        fun from(
            session: SessionInfo,
            ownName: String?,
            cachedPatient: LinkedPatientProfile?
        ): PatientContext {
            val userId = session.userId?.takeIf { it > 0 }
                ?: return NONE.copy(tipo = session.tipo)

            val caregiver = AccessPolicy.isCaregiver(session.tipo)
            val patientId = AccessPolicy.patientIdFor(session.tipo, userId, session.pacienteId)

            val linkState = when {
                !caregiver -> LinkState.PATIENT
                patientId != null -> LinkState.CAREGIVER_LINKED
                else -> LinkState.CAREGIVER_AWAITING_LINK
            }

            val patientName = when (linkState) {
                LinkState.PATIENT -> ownName
                LinkState.CAREGIVER_LINKED ->
                    cachedPatient?.takeIf { it.patientId == patientId }?.name
                else -> null
            }?.trim()?.takeIf { it.isNotEmpty() }

            return PatientContext(
                userId = userId,
                tipo = session.tipo,
                patientId = patientId,
                caregiverId = if (caregiver) userId else null,
                linkState = linkState,
                patientName = patientName
            )
        }
    }
}
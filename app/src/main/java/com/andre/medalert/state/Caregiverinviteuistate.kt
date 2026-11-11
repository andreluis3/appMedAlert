package com.andre.medalert.state

import com.andre.medalert.data.model.PendingInvite

/** Estados da tela em que o cuidador vê um convite pendente guardado no aparelho. */
sealed interface CaregiverInviteUiState {

    /** Lendo o DataStore. */
    object Loading : CaregiverInviteUiState

    /** Não há convite pendente para esse paciente (já tratado, apagado ou nunca recebido). */
    object NotFound : CaregiverInviteUiState

    data class Found(val invite: PendingInvite) : CaregiverInviteUiState
}
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

/** Andamento do aceite (POST /users/accept). Separado do convite, que some quando o aceite conclui. */
sealed interface CaregiverInviteAction {

    /** Nada em andamento. */
    object Idle : CaregiverInviteAction

    /** Request em andamento: bloqueia os botões e impede chamadas duplicadas. */
    object Accepting : CaregiverInviteAction

    /** A API confirmou e a sessão já foi atualizada. */
    data class Accepted(val pacienteId: Int) : CaregiverInviteAction

    /** Falha: mensagem amigável; os botões voltam a funcionar para tentar de novo. */
    data class Failed(val message: String) : CaregiverInviteAction
}
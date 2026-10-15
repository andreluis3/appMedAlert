package com.andre.medalert.state

import com.andre.medalert.data.model.CaregiverDecision
import com.andre.medalert.data.model.CaregiverRequestUiModel

/** Estados da tela de confirmação da solicitação de cuidador. */
sealed interface CaregiverRequestUiState {

    /** Buscando a solicitação. */
    object Loading : CaregiverRequestUiState

    /** Não foi possível carregar a solicitação. */
    data class Error(
        val message: String = "Não foi possível carregar a solicitação."
    ) : CaregiverRequestUiState

    /**
     * Solicitação carregada.
     * - submitting: decisão sendo enviada agora (bloqueia os botões); null quando parado.
     * - actionError: erro ao enviar a resposta (os botões voltam a ficar ativos).
     * - respondedNow: true só quando o cuidador respondeu nesta tela (mostra o sucesso).
     */
    data class Content(
        val request: CaregiverRequestUiModel,
        val submitting: CaregiverDecision? = null,
        val actionError: String? = null,
        val respondedNow: Boolean = false
    ) : CaregiverRequestUiState
}
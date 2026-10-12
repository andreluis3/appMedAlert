package com.andre.medalert.state

import com.andre.medalert.data.remote.dto.DashboardResponse

/**
 * Estados possíveis da tela "Adesão ao tratamento".
 *
 * Quando a integração chegar, o ViewModel só precisa emitir estes estados
 * (Loading -> Success/Error) e a tela continua igual.
 */
sealed interface AdherenceUiState {

    /** Aguardando a resposta de GET /api/historys/dashboard. */
    object Loading : AdherenceUiState

    /** A chamada falhou. */
    data class Error(
        val message: String = "Não foi possível carregar os dados."
    ) : AdherenceUiState

    /** Resposta recebida (a tela trata sozinha o caso de dosesEsp == 0). */
    data class Success(val data: DashboardResponse) : AdherenceUiState
}
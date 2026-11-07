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

    /**
     * Cuidador logado que ainda não tem paciente vinculado: não há dados para mostrar
     * (e a tela não deve fingir que há nem tratar como erro).
     */
    object AwaitingLink : AdherenceUiState

    /**
     * Resposta recebida (a tela trata sozinha o caso de dosesEsp == 0).
     * [isLocal] = true: dados calculados no aparelho (Room) porque o servidor não respondeu.
     */
    data class Success(
        val data: DashboardResponse,
        val isLocal: Boolean = false
    ) : AdherenceUiState
}
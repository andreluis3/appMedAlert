package com.andre.medalert.data.repository

import androidx.compose.runtime.mutableStateListOf
import com.andre.medalert.data.mock.mockNotificacoesIniciais
import com.andre.medalert.data.model.NotificationUiModel

/**
 * Não existe endpoint de notificações na API ainda. Interface pronta pra
 * quando existir: NotificationScreen -> NotificationViewModel ->
 * NotificationRepository -> hoje MockNotificationRepository, depois uma
 * implementação real chamando o endpoint, sem mudar a tela nem o ViewModel.
 */
interface NotificationRepository {
    suspend fun listar(): List<NotificationUiModel>
    suspend fun marcarComoLida(id: Int)

    /**
     * A interação mais importante da tela: confirma a dose de um
     * medicamento a partir de uma notificação.
     * TODO: quando existir, isso deve chamar o endpoint de histórico
     * (POST /api/historys) pra registrar a dose como tomada de verdade.
     */
    suspend fun marcarMedicamentoComoTomado(id: Int)

    /**
     * TODO: reagendar o lembrete local (o projeto já tem AlarmScheduler)
     * pra daqui a alguns minutos. Por enquanto só marca a notificação
     * como lida.
     */
    suspend fun lembrarMaisTarde(id: Int)
}

object MockNotificationRepository : NotificationRepository {

    private val notificacoes = mutableStateListOf<NotificationUiModel>().apply {
        addAll(mockNotificacoesIniciais)
    }

    override suspend fun listar(): List<NotificationUiModel> =
        notificacoes.sortedBy { if (it.lida) 1 else 0 }

    override suspend fun marcarComoLida(id: Int) {
        atualizar(id) { it.copy(lida = true) }
    }

    override suspend fun marcarMedicamentoComoTomado(id: Int) {
        atualizar(id) { it.copy(lida = true) }
    }

    override suspend fun lembrarMaisTarde(id: Int) {
        atualizar(id) { it.copy(lida = true) }
    }

    private fun atualizar(id: Int, transform: (NotificationUiModel) -> NotificationUiModel) {
        val index = notificacoes.indexOfFirst { it.id == id }
        if (index != -1) {
            notificacoes[index] = transform(notificacoes[index])
        }
    }
}
package com.andre.medalert.data.repository

import com.andre.medalert.data.model.NotificationUiModel

/**
 * Não existe endpoint de notificações na API. Os lembretes de dose são notificações do sistema
 * (AlarmManager) e não são guardados aqui; a lista interna fica vazia até haver contrato.
 */
interface NotificationRepository {
    suspend fun listar(): List<NotificationUiModel>
    suspend fun marcarComoLida(id: Int)

    /** TODO: chamar o histórico (POST /api/historys) quando o contrato estiver definido. */
    suspend fun marcarMedicamentoComoTomado(id: Int)

    /** TODO: reagendar um lembrete curto via AlarmScheduler. */
    suspend fun lembrarMaisTarde(id: Int)
}

object LocalNotificationRepository : NotificationRepository {
    override suspend fun listar(): List<NotificationUiModel> = emptyList()
    override suspend fun marcarComoLida(id: Int) = Unit
    override suspend fun marcarMedicamentoComoTomado(id: Int) = Unit
    override suspend fun lembrarMaisTarde(id: Int) = Unit
}
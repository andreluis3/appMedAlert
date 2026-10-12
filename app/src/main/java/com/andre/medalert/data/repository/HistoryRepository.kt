package com.andre.medalert.data.repository

import com.andre.medalert.data.local.dao.DoseHistoryDao
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.mapper.toEntities
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.remote.dto.HistoryQueryRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class HistoryRepository(
    private val dao: DoseHistoryDao,
    private val api: ApiService = RetrofitClient.instance
) {

    /** A tela de Histórico observa isto. Funciona sem internet. */
    fun observeHistory(userId: Int): Flow<List<DoseHistoryEntity>> =
        dao.observeByUser(userId)

    /** Busca o histórico na API e atualiza o Room. */
    suspend fun refresh(userId: Int): Result<Unit> =
        try {
            val remote = api.getHistory(HistoryQueryRequest(id = userId))
            dao.replaceSynced(userId, remote.flatMap { it.toEntities(userId) })
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** Dashboard continua direto da API (ainda não é guardado no Room). */
    suspend fun getDashboard(userId: Int): DashboardResponse {
        return api.getDashboard(userId)
    }
}
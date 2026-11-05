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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HistoryRepository(
    private val dao: DoseHistoryDao,
    private val api: ApiService = RetrofitClient.instance
) {

    /** A tela de Histórico observa isto: só as doses do dia atual. Funciona sem internet. */
    fun observeHistory(userId: Int): Flow<List<DoseHistoryEntity>> =
        dao.observeByUserOnDay(userId, LocalDate.now().toString())

    /** Doses tomadas pela notificação desde [fromMillis] (a Home marca como "Tomado"). */
    fun observeLocalTaken(userId: Int, fromMillis: Long): Flow<List<DoseHistoryEntity>> =
        dao.observeLocalTaken(userId, fromMillis)

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

    /**
     * Registra no Room uma dose tomada pela notificação. Retorna true se criou o registro e
     * false se essa dose (medicamento + horário previsto) já estava registrada.
     *
     * A linha fica com isSynced = false. [medicationRemoteId] é o ID REMOTO (null se o
     * medicamento ainda não foi sincronizado); o ID local nunca vai para o campo remoto.
     *
     * TODO(API): quando o contrato do POST /api/historys estiver confirmado, enviar as linhas
     * pendentes (dao.getUnsynced()) e marcar com dao.markSynced(id). Nada é enviado hoje.
     */
    suspend fun registerTakenLocally(
        userId: Int,
        medicationLocalId: Long,
        medicationRemoteId: Int?,
        medicationName: String,
        dosage: String,
        scheduledAtMillis: Long,
        takenAtMillis: Long
    ): Boolean {
        val zone = ZoneId.systemDefault()
        val scheduled = Instant.ofEpochMilli(scheduledAtMillis).atZone(zone)
        val taken = Instant.ofEpochMilli(takenAtMillis).atZone(zone)

        val entity = DoseHistoryEntity(
            userId = userId,
            medicamentoId = medicationRemoteId,
            medicamentoNome = medicationName,
            dosagem = dosage,
            horario = scheduled.format(HORA),
            tomado = taken.format(HORA),
            status = STATUS_TOMADO,
            isSynced = false,
            medicationLocalId = medicationLocalId,
            previstoEm = scheduledAtMillis,
            tomadoEm = takenAtMillis,
            dia = scheduled.toLocalDate().toString()
        )
        return dao.insertIgnore(entity) != -1L
    }

    /** true se essa dose (medicamento + horário previsto) já foi registrada. */
    suspend fun isDoseRegistered(medicationLocalId: Long, scheduledAtMillis: Long): Boolean =
        dao.countByDose(medicationLocalId, scheduledAtMillis) > 0

    private companion object {
        const val STATUS_TOMADO = "tomado"
        val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
package com.andre.medalert.data.repository

import com.andre.medalert.data.local.dao.DoseHistoryDao
import com.andre.medalert.data.local.dao.TakenInsertResult
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.remote.dto.DoseTakenRequest
import com.andre.medalert.data.remote.dto.DoseTakenRequestFactory
import com.andre.medalert.data.remote.dto.HistoryRecordDto
import com.andre.medalert.data.schedule.DoseReconciler
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "HistoryRepo"

/**
 * Trava de PROCESSO: o Worker, a tela e o registrador criam cada um o seu HistoryRepository,
 * então a trava fica fora da classe. Garante que só um fluxo por vez envie doses pendentes
 * (evita o mesmo POST duas vezes).
 */
private val syncMutex = Mutex()

/**
 * Resultado de um ciclo de envio.
 * [shouldRetry] = true quando vale tentar de novo depois (sem internet, erro 5xx, falha inesperada).
 * [rejected] = doses que o servidor recusou com 4xx: continuam pendentes no Room, sem tentativa automática.
 * [waiting] = doses que ainda não podem ser enviadas (medicamento sem ID remoto ou sem horário real).
 */
data class SyncOutcome(
    val sent: Int,
    val rejected: Int,
    val waiting: Int,
    val shouldRetry: Boolean
)

private sealed interface SendResult {
    object Ok : SendResult
    object NoConnection : SendResult
    data class Http(val code: Int) : SendResult
    data class Failure(val error: Exception) : SendResult
}

/**
 * Offline-first: a UI observa SEMPRE o Room. A API só alimenta o Room (reconciliação do dia)
 * ou recebe as doses registradas localmente (POST /api/historys).
 */
class HistoryRepository(
    private val dao: DoseHistoryDao,
    private val api: ApiService = RetrofitClient.instance
) {

    /** Só as doses do dia atual. */
    fun observeHistory(userId: Int): Flow<List<DoseHistoryEntity>> =
        dao.observeByUserOnDay(userId, LocalDate.now().toString())

    /** Todos os registros do paciente (Histórico e Calendário calculam o período em cima disto). */
    fun observeAll(userId: Int): Flow<List<DoseHistoryEntity>> = dao.observeByUser(userId)

    /** Doses tomadas pela notificação desde [fromMillis] (a Home marca como "Tomado"). */
    fun observeLocalTaken(userId: Int, fromMillis: Long): Flow<List<DoseHistoryEntity>> =
        dao.observeLocalTaken(userId, fromMillis)

    /**
     * GET /api/historys/doseDia/{userId} e reconciliação INCREMENTAL com o Room.
     * Em falha (rede ou HTTP) o Room fica exatamente como está.
     */
    suspend fun refresh(userId: Int, medications: List<MedicationEntity>): Result<Unit> =
        try {
            val remote = api.getDosesDia(userId)
            reconcileToday(userId, remote, medications)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Falha ao buscar as doses do dia", e)
            Result.failure(e)
        }

    /**
     * Reconciliação sem apagar nada:
     * - só doses que a API informa como TOMADAS e cuja associação com um medicamento local
     *   é confiável (nome + dosagem únicos, com remoteId) entram no Room;
     * - se a dose já existe localmente, o registro local é mantido; se ele estava pendente de
     *   envio, é marcado como sincronizado (o servidor já tem essa dose);
     * - doses pendentes na API, associações ambíguas e ausência no GET não alteram nada;
     * - dias anteriores e registros pendentes de envio nunca são tocados.
     *
     * Devolve quantas linhas novas foram inseridas.
     */
    suspend fun reconcileToday(
        userId: Int,
        remote: List<HistoryRecordDto>,
        medications: List<MedicationEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now()
    ): Int {
        val matches = DoseReconciler.associate(remote, medications, userId)
        if (matches.size < remote.size) {
            AppLog.w(
                TAG,
                "${remote.size - matches.size} registro(s) do doseDia sem associação única; ignorados"
            )
        }

        var inserted = 0
        matches.forEach { match ->
            DoseReconciler.takenRows(match, today, zone).forEach { row ->
                if (dao.insertIgnore(row) == -1L) {
                    val localId = row.medicationLocalId
                    val scheduled = row.previstoEm
                    if (localId != null && scheduled != null) {
                        dao.markSyncedByDose(localId, scheduled)
                    }
                } else {
                    inserted++
                }
            }
        }
        return inserted
    }

    /** Dashboard continua direto da API (ainda não é guardado no Room). */
    suspend fun getDashboard(userId: Int): DashboardResponse {
        return api.getDashboard(userId)
    }

    /**
     * Registra no Room uma dose tomada pela notificação e desconta o estoque (se o medicamento
     * tem estoque informado), na mesma transação. `inserted = false` no resultado significa que
     * essa dose (medicamento + horário previsto) já estava registrada: nada é descontado de novo.
     *
     * A linha fica com isSynced = false e é enviada depois por [syncPending]. [medicationRemoteId]
     * é o ID REMOTO (null se o medicamento ainda não foi sincronizado); o ID local nunca vai para
     * o campo remoto. [quantityPerDose] é a quantidade por dose do medicamento (mínimo 1).
     */
    suspend fun registerTakenLocally(
        userId: Int,
        medicationLocalId: Long,
        medicationRemoteId: Int?,
        medicationName: String,
        dosage: String,
        scheduledAtMillis: Long,
        takenAtMillis: Long,
        quantityPerDose: Int = 1
    ): TakenInsertResult {
        val entity = buildTakenEntity(
            userId = userId,
            medicationLocalId = medicationLocalId,
            medicationRemoteId = medicationRemoteId,
            medicationName = medicationName,
            dosage = dosage,
            scheduledAtMillis = scheduledAtMillis,
            takenAtMillis = takenAtMillis
        )
        return dao.insertTakenConsumingStock(entity, quantityPerDose)
    }

    /** true se essa dose (medicamento + horário previsto) já foi registrada. */
    suspend fun isDoseRegistered(medicationLocalId: Long, scheduledAtMillis: Long): Boolean =
        dao.countByDose(medicationLocalId, scheduledAtMillis) > 0

    /**
     * Envia ao POST /api/historys as doses do paciente [userId] ainda não sincronizadas.
     *
     * - medicamento_id = ID REMOTO (da própria linha ou lido do medicamento no Room); sem ID
     *   remoto a dose espera ([SyncOutcome.waiting]) e NUNCA vai com o ID local.
     * - usuario_id = paciente dono da linha (a consulta já filtra pelo paciente da sessão).
     * - tomado_em = horário real da tomada (ISO-8601 com offset).
     * - isSynced só vira true depois de um código HTTP 2xx.
     * - Sem conexão ou 5xx: para o laço e pede nova tentativa; 401: para (a sessão já foi tratada
     *   pelo interceptor); outros 4xx: a dose continua pendente e é contada em [SyncOutcome.rejected].
     * - Doses de medicamento excluído não são enviadas (ficam fora da consulta).
     *
     * Quem chama deve garantir que a sessão é de paciente (cuidador é somente leitura).
     */
    suspend fun syncPending(
        userId: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): SyncOutcome = syncMutex.withLock {
        val pending = dao.getUnsyncedByUser(userId)

        var sent = 0
        var rejected = 0
        var waiting = 0
        var retry = false

        loop@ for (row in pending) {
            val takenAt = row.tomadoEm
            val localId = row.medicationLocalId
            val remoteMedicationId = row.medicamentoId ?: localId?.let { dao.remoteIdOf(it) }

            if (takenAt == null || remoteMedicationId == null) {
                waiting++
                continue@loop
            }

            val request = DoseTakenRequestFactory.create(
                userId = row.userId,
                medicationRemoteId = remoteMedicationId,
                takenAtMillis = takenAt,
                zone = zone
            )

            when (val result = send(request)) {
                is SendResult.Ok -> {
                    dao.markSynced(row.id)
                    sent++
                }

                is SendResult.NoConnection -> {
                    retry = true
                    break@loop
                }

                is SendResult.Http -> when {
                    result.code == 401 -> break@loop
                    result.code >= 500 -> {
                        retry = true
                        break@loop
                    }
                    else -> rejected++
                }

                is SendResult.Failure -> {
                    AppLog.e(TAG, "Erro inesperado ao enviar dose (id local da linha=${row.id})", result.error)
                    retry = true
                    break@loop
                }
            }
        }

        SyncOutcome(sent = sent, rejected = rejected, waiting = waiting, shouldRetry = retry)
    }

    private suspend fun send(request: DoseTakenRequest): SendResult =
        try {
            val response = api.createHistory(request)
            response.body()?.close()
            if (response.isSuccessful) {
                SendResult.Ok
            } else {
                val errorBody = try {
                    response.errorBody()?.string()
                } catch (_: Exception) {
                    null
                }
                AppLog.w(TAG, "POST /api/historys -> HTTP ${response.code()} | errorBody=$errorBody")
                SendResult.Http(response.code())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SendResult.NoConnection
        } catch (e: Exception) {
            SendResult.Failure(e)
        }
}
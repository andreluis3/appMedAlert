package com.andre.medalert.data.repository

import com.andre.medalert.data.local.dao.DoseHistoryDao
import com.andre.medalert.data.local.dao.TakenInsertResult
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.SessionManager
import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.session.AdherenceRoute
import com.andre.medalert.data.remote.dto.DoseTakenRequest
import com.andre.medalert.data.remote.dto.DoseTakenRequestFactory
import com.andre.medalert.data.remote.dto.HistoryRecordDto
import com.andre.medalert.data.schedule.DoseReconciler
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.data.session.SessionInfo
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

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
 * [duplicates] = doses que o servidor disse já ter (409): contam como confirmadas.
 * [rejected] = doses que o servidor recusou com 4xx: continuam pendentes no Room, sem tentativa automática.
 * [waiting] = doses que ainda não podem ser enviadas (medicamento sem ID remoto ou sem horário real).
 */
data class SyncOutcome(
    val sent: Int,
    val rejected: Int,
    val waiting: Int,
    val shouldRetry: Boolean,
    /** Doses que o servidor informou já existirem (HTTP 409): confirmadas sem criar duplicata. */
    val duplicates: Int = 0
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
 *
 * [sessionProvider] devolve a sessão atual. Antes de buscar do servidor, antes de gravar o que
 * veio e antes de cada POST, o repositório confere que a sessão ainda é a do paciente cujos dados
 * está tratando; se mudou (logout, outro login, troca de vínculo), a operação para sem gravar
 * mais nada. O padrão lê o [SessionManager]; se ele ainda não foi iniciado (testes), devolve null
 * e a conferência é dispensada.
 *
 * Limite conhecido: a conferência acontece imediatamente antes do POST, mas entre ela e o envio
 * o token continua sendo o da sessão do momento. O backend precisa recusar (403) doses cujo
 * usuario_id não seja do paciente autorizado pelo token.
 */
class HistoryRepository(
    private val dao: DoseHistoryDao,
    private val api: ApiService = RetrofitClient.instance,
    private val sessionProvider: suspend () -> SessionInfo? = { SessionManager.currentSession() }
) {

    /** A sessão atual ainda acompanha o paciente [patientId]? Sem sessão disponível: dispensa. */
    private suspend fun sessionOwns(patientId: Int): Boolean {
        val session = sessionProvider() ?: return true
        return AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId) == patientId
    }

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
     * Em falha (rede ou HTTP) o Room fica exatamente como está. Se a sessão deixou de ser a do
     * paciente [userId] antes ou durante a requisição, devolve [StaleSessionException] e não grava.
     */
    suspend fun refresh(userId: Int, medications: List<MedicationEntity>): Result<Unit> {
        return try {
            if (!sessionOwns(userId)) return Result.failure(StaleSessionException())
            val remote = api.getDosesDia(userId)
            // A resposta pode chegar depois de um logout ou de uma troca de vínculo.
            if (!sessionOwns(userId)) return Result.failure(StaleSessionException())
            reconcileToday(userId, remote, medications)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Falha ao buscar as doses do dia", e)
            Result.failure(e)
        }
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
                        dao.markSyncedByDoseForUser(userId, localId, scheduled)
                    }
                } else {
                    inserted++
                }
            }
        }
        return inserted
    }

    /**
     * Dashboard do PACIENTE: GET api/historys/dashboard/{usuarioId}, com o id do próprio paciente.
     * Só para o paciente logado; o cuidador usa [getCaregiverDashboard].
     */
    suspend fun getPatientDashboard(userId: Int): DashboardResponse {
        return api.getDashboard(userId)
    }

    /**
     * Dashboard do paciente vinculado, para o CUIDADOR: GET api/historys/dashboard/cuid/{cuidadorId}.
     * [caregiverId] é o id do CUIDADOR logado (identity.userId), NUNCA o do paciente.
     */
    suspend fun getCaregiverDashboard(caregiverId: Int): DashboardResponse {
        return api.getDashboardCuidador(caregiverId)
    }

    /**
     * Escolhe o endpoint pela rota da sessão (ver [AdherenceRouting]): paciente com o id do
     * paciente, cuidador com o id do cuidador. Direto da API; não vai ao Room.
     */
    suspend fun getDashboard(route: AdherenceRoute.Remote): DashboardResponse =
        when (route) {
            is AdherenceRoute.Patient -> getPatientDashboard(route.patientId)
            is AdherenceRoute.Caregiver -> getCaregiverDashboard(route.caregiverId)
        }

    /** Mantido por compatibilidade: é SEMPRE o dashboard do PACIENTE. O cuidador não deve usar. */
    suspend fun getDashboard(userId: Int): DashboardResponse = getPatientDashboard(userId)

    /**
     * Registra no Room uma dose tomada pela notificação ou pela Home. `inserted = false` no
     * resultado significa que essa dose (medicamento + horário previsto) já estava registrada
     * (ou, com `refused = true`, que o medicamento pertence a outro paciente).
     *
     * A linha fica com isSynced = false e é enviada depois por [syncPending]. [medicationRemoteId]
     * é o ID REMOTO (null se o medicamento ainda não foi sincronizado); o ID local nunca vai para
     * o campo remoto.
     */
    suspend fun registerTakenLocally(
        userId: Int,
        medicationLocalId: Long,
        medicationRemoteId: Int?,
        medicationName: String,
        dosage: String,
        scheduledAtMillis: Long,
        takenAtMillis: Long,
        origin: String? = null,
        registeredById: Int? = null
    ): TakenInsertResult {
        val entity = buildTakenEntity(
            userId = userId,
            medicationLocalId = medicationLocalId,
            medicationRemoteId = medicationRemoteId,
            medicationName = medicationName,
            dosage = dosage,
            scheduledAtMillis = scheduledAtMillis,
            takenAtMillis = takenAtMillis,
            origin = origin,
            registeredById = registeredById,
            clientEventId = UUID.randomUUID().toString()
        )
        return dao.insertTaken(entity)
    }

    /** true se essa dose (medicamento + horário previsto) já foi registrada. */
    suspend fun isDoseRegistered(medicationLocalId: Long, scheduledAtMillis: Long): Boolean =
        dao.countByDose(medicationLocalId, scheduledAtMillis) > 0

    /**
     * Envia ao POST /api/historys as doses do paciente [userId] ainda não sincronizadas.
     *
     * - medicamento_id = ID REMOTO (da própria linha ou lido do medicamento no Room, só se ele
     *   for do mesmo paciente); sem ID remoto a dose espera ([SyncOutcome.waiting]) e NUNCA vai
     *   com o ID local.
     * - usuario_id = paciente dono da linha (a consulta já filtra pelo paciente da sessão).
     * - tomado_em = horário real da tomada (ISO-8601 com offset).
     * - isSynced só vira true depois de um código HTTP 2xx.
     * - Sem conexão ou 5xx: para o laço e pede nova tentativa; 401: para (a sessão já foi tratada
     *   pelo interceptor); outros 4xx: a dose continua pendente e é contada em [SyncOutcome.rejected].
     * - Doses de medicamento excluído não são enviadas (ficam fora da consulta).
     * - Se a sessão deixar de ser a do paciente [userId], o ciclo para sem enviar mais nada e SEM
     *   pedir nova tentativa: as doses continuam pendentes no Room e sobem quando esse paciente
     *   (ou o cuidador vinculado a ele) estiver na sessão de novo; o SessionLifecycle já
     *   enfileira a sincronização da sessão nova.
     *
     * [userId] é sempre o PACIENTE dono das doses: o cuidador também envia, com o id do paciente
     * vinculado. Quem chama deve conferir AccessPolicy.canRegisterDose.
     *
     * Antes de enviar, se há dose pendente de hoje, o doseDia é consultado e reconciliado: uma
     * dose que o servidor já tem como tomada (por outro aparelho ou por um POST cuja resposta se
     * perdeu) vira "sincronizada" sem novo POST. Isso só cobre o dia atual e depende de associação
     * única por nome + dosagem; a deduplicação definitiva entre aparelhos é do backend.
     * Um 409 do servidor é tratado como "esta dose já existe" e confirma a linha.
     */
    suspend fun syncPending(
        userId: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): SyncOutcome = syncMutex.withLock {
        if (!sessionOwns(userId)) {
            return@withLock SyncOutcome(sent = 0, rejected = 0, waiting = 0, shouldRetry = false)
        }

        var pending = dao.getUnsyncedByUser(userId)
        if (pending.isNotEmpty()) {
            reconcileBeforeSending(userId, pending, zone)
            pending = dao.getUnsyncedByUser(userId)
        }

        var sent = 0
        var duplicates = 0
        var rejected = 0
        var waiting = 0
        var retry = false

        loop@ for (row in pending) {
            val takenAt = row.tomadoEm
            val localId = row.medicationLocalId
            val remoteMedicationId =
                row.medicamentoId ?: localId?.let { dao.remoteIdOfForUser(it, row.userId) }

            if (takenAt == null || remoteMedicationId == null) {
                waiting++
                continue@loop
            }

            // A sessão pode ter mudado desde o início do ciclo (outro login, troca de vínculo):
            // nunca envia dose de um paciente com o token de outra sessão.
            if (!sessionOwns(userId)) break@loop

            val request = DoseTakenRequestFactory.create(
                userId = row.userId,
                medicationRemoteId = remoteMedicationId,
                takenAtMillis = takenAt,
                zone = zone
            )

            when (val result = send(request)) {
                is SendResult.Ok -> {
                    dao.markSyncedForUser(row.id, row.userId)
                    sent++
                }

                is SendResult.NoConnection -> {
                    retry = true
                    break@loop
                }

                is SendResult.Http -> when {
                    result.code == 401 -> break@loop
                    result.code == 409 -> {
                        dao.markSyncedForUser(row.id, row.userId)
                        duplicates++
                    }
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

        SyncOutcome(
            sent = sent,
            rejected = rejected,
            waiting = waiting,
            shouldRetry = retry,
            duplicates = duplicates
        )
    }

    /** Melhor esforço: se o doseDia falhar, o envio segue e o backend decide sobre duplicatas. */
    private suspend fun reconcileBeforeSending(
        userId: Int,
        pending: List<DoseHistoryEntity>,
        zone: ZoneId
    ) {
        val today = LocalDate.now(zone)
        if (pending.none { it.dia == today.toString() }) return
        try {
            val remote = api.getDosesDia(userId)
            if (!sessionOwns(userId)) return
            reconcileToday(userId, remote, dao.medicationsOf(userId), zone, today)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Não foi possível conferir o doseDia antes de enviar", e)
        }
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
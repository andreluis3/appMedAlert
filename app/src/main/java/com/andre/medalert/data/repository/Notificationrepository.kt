package com.andre.medalert.data.repository

import android.content.Context
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.NotificationEntity
import com.andre.medalert.data.model.NotificationType
import com.andre.medalert.data.model.NotificationUiModel
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.data.session.SessionInfo
import com.andre.medalert.notification.DoseIntakeRegistrar
import com.andre.medalert.notification.DoseRegisterResult
import com.andre.medalert.notification.MedicationReminderManager
import com.andre.medalert.notification.NotificationHelper
import com.andre.medalert.notification.SnoozeStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Resultado de "Já tomei" / "Lembrar depois" num cartão da caixa de entrada. */
enum class NotificationActionResult {
    /** A ação foi feita agora. */
    DONE,

    /** A dose já estava registrada: nada foi duplicado. */
    ALREADY_DONE,

    /** Não dá para agir: sem permissão, dose antiga/substituída, medicamento excluído ou inativo. */
    UNAVAILABLE,

    /** Falha inesperada. */
    ERROR
}

/**
 * Caixa de entrada de notificações do usuário logado.
 * Não registra doses por conta própria: [marcarMedicamentoComoTomado] usa o
 * [DoseIntakeRegistrar] (o mesmo do botão "Tomar agora"), e [lembrarMaisTarde] usa o [SnoozeStore].
 */
interface NotificationRepository {
    /** Lista do usuário da sessão, mais recentes primeiro. Atualiza sozinha quando o Room muda. */
    fun observe(): Flow<List<NotificationUiModel>>

    /** Quantidade de não lidas (para o contador do sino na Home). */
    fun observeUnreadCount(): Flow<Int>

    suspend fun marcarComoLida(id: Long)
    suspend fun marcarTodasComoLidas()
    suspend fun excluir(id: Long)
    suspend fun marcarMedicamentoComoTomado(id: Long): NotificationActionResult
    suspend fun lembrarMaisTarde(id: Long): NotificationActionResult
}

class RoomNotificationRepository(context: Context) : NotificationRepository {

    private val app = context.applicationContext
    private val prefs = UserPreferencesRepository(app)
    private val db = AppDatabase.getInstance(app)
    private val dao = db.notificationDao()
    private val doseDao = db.doseHistoryDao()

    private data class Scope(
        val recipientId: Int,
        val patientId: Int?,
        val canRegisterDose: Boolean
    )

    private fun SessionInfo.toScope(): Scope? {
        val recipient = userId?.takeIf { it > 0 } ?: return null
        return Scope(
            recipientId = recipient,
            patientId = AccessPolicy.patientIdFor(tipo, userId, pacienteId),
            canRegisterDose = AccessPolicy.canRegisterDose(tipo, userId, pacienteId)
        )
    }

    private val scope: Flow<Scope?> = prefs.session.map { it.toScope() }.distinctUntilChanged()

    private suspend fun currentScope(): Scope? = prefs.session.first().toScope()

    // ------------------------------------------------------------------ leitura

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<NotificationUiModel>> = scope.flatMapLatest { sc ->
        if (sc == null) {
            flowOf(emptyList<NotificationUiModel>())
        } else {
            val patientId = sc.patientId
            val taken: Flow<List<DoseHistoryEntity>> =
                if (sc.canRegisterDose && patientId != null) {
                    doseDao.observeLocalTaken(
                        patientId,
                        System.currentTimeMillis() - ACTION_WINDOW_MILLIS
                    )
                } else {
                    flowOf(emptyList<DoseHistoryEntity>())
                }
            combine(dao.observeAll(sc.recipientId), taken) { list, takenRows ->
                toUiModels(list, takenRows, sc.canRegisterDose)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUnreadCount(): Flow<Int> = scope.flatMapLatest { sc ->
        if (sc == null) flowOf(0) else dao.observeUnreadCount(sc.recipientId)
    }

    private fun toUiModels(
        list: List<NotificationEntity>,
        takenRows: List<DoseHistoryEntity>,
        canRegisterDose: Boolean
    ): List<NotificationUiModel> {
        val takenDoses: Set<Pair<Long, Long>> = takenRows.mapNotNull { row ->
            val localId = row.medicationLocalId
            val scheduled = row.previstoEm
            if (localId != null && scheduled != null) localId to scheduled else null
        }.toSet()

        // Dose mais recente com ação, por medicamento: só ela pode ter botões.
        val latestByMedication: Map<Long, Long> = list
            .filter { it.tipo in ACTION_TYPES && it.medicationLocalId != null && it.previstoEm != null }
            .groupBy { it.medicationLocalId!! }
            .mapValues { (_, rows) -> rows.maxOf { it.previstoEm!! } }

        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        return list.map { n ->
            val localId = n.medicationLocalId
            val scheduled = n.previstoEm
            val actionable = canRegisterDose &&
                    n.tipo in ACTION_TYPES &&
                    localId != null && scheduled != null &&
                    now - scheduled <= ACTION_WINDOW_MILLIS &&
                    latestByMedication[localId] == scheduled &&
                    (localId to scheduled) !in takenDoses
            n.toUi(actionable, zone, today)
        }
    }

    private fun NotificationEntity.toUi(
        actionable: Boolean,
        zone: ZoneId,
        today: LocalDate
    ): NotificationUiModel {
        val moment = Instant.ofEpochMilli(criadaEm).atZone(zone)
        val formatter = if (moment.toLocalDate() == today) HOUR_FORMAT else DAY_HOUR_FORMAT
        return NotificationUiModel(
            id = id,
            titulo = titulo,
            descricao = descricao,
            horario = formatter.format(moment),
            criadaEm = criadaEm,
            tipo = NotificationType.fromStored(tipo),
            lida = lidaEm != null,
            medicationName = medicationNome,
            acoesDisponiveis = actionable
        )
    }

    // ------------------------------------------------------------------ leitura / exclusão

    override suspend fun marcarComoLida(id: Long) {
        val sc = currentScope() ?: return
        dao.markRead(id, sc.recipientId, System.currentTimeMillis())
    }

    override suspend fun marcarTodasComoLidas() {
        val sc = currentScope() ?: return
        dao.markAllRead(sc.recipientId, System.currentTimeMillis())
    }

    override suspend fun excluir(id: Long) {
        val sc = currentScope() ?: return
        dao.deleteForRecipient(id, sc.recipientId)
    }

    // ------------------------------------------------------------------ ações dos cartões

    /**
     * "Já tomei": reutiliza [DoseIntakeRegistrar.registerTaken], que confere sessão, permissão
     * (AccessPolicy.canRegisterDose), medicamento ativo do paciente dono e duplicidade
     * (medicamento + horário previsto). Nenhum registro de dose é criado aqui.
     */
    override suspend fun marcarMedicamentoComoTomado(id: Long): NotificationActionResult {
        val sc = currentScope() ?: return NotificationActionResult.UNAVAILABLE
        if (!sc.canRegisterDose) return NotificationActionResult.UNAVAILABLE

        val n = dao.getByIdForRecipient(id, sc.recipientId)
            ?: return NotificationActionResult.UNAVAILABLE
        val localId = n.medicationLocalId ?: return NotificationActionResult.UNAVAILABLE
        val scheduled = n.previstoEm ?: return NotificationActionResult.UNAVAILABLE
        if (!isActionable(sc, n, localId, scheduled)) return NotificationActionResult.UNAVAILABLE

        return when (DoseIntakeRegistrar.registerTaken(app, localId, scheduled)) {
            DoseRegisterResult.REGISTERED -> {
                afterDoseHandled(sc, id, localId)
                NotificationActionResult.DONE
            }

            DoseRegisterResult.ALREADY_REGISTERED -> {
                afterDoseHandled(sc, id, localId)
                NotificationActionResult.ALREADY_DONE
            }

            DoseRegisterResult.MEDICATION_UNAVAILABLE -> NotificationActionResult.UNAVAILABLE
            DoseRegisterResult.ERROR -> NotificationActionResult.ERROR
        }
    }

    /**
     * "Lembrar depois": usa o [SnoozeStore] (alarme PRÓPRIO de adiamento por medicamento, que não
     * sobrescreve o alarme da próxima dose regular e sobrevive a reboot). A dose não é registrada.
     * Quando o adiamento dispara, o receiver reapresenta a mesma dose e a caixa NÃO duplica a
     * notificação (mesma chave de deduplicação).
     */
    override suspend fun lembrarMaisTarde(id: Long): NotificationActionResult {
        val sc = currentScope() ?: return NotificationActionResult.UNAVAILABLE
        if (!sc.canRegisterDose) return NotificationActionResult.UNAVAILABLE

        val n = dao.getByIdForRecipient(id, sc.recipientId)
            ?: return NotificationActionResult.UNAVAILABLE
        val localId = n.medicationLocalId ?: return NotificationActionResult.UNAVAILABLE
        val scheduled = n.previstoEm ?: return NotificationActionResult.UNAVAILABLE
        if (!isActionable(sc, n, localId, scheduled)) return NotificationActionResult.UNAVAILABLE

        // Medicamento ainda ativo e do paciente da sessão (mesma regra do receiver do alarme).
        MedicationReminderManager.findDeliverable(app, localId)
            ?: return NotificationActionResult.UNAVAILABLE

        if (DoseIntakeRegistrar.isAlreadyRegistered(app, localId, scheduled)) {
            afterDoseHandled(sc, id, localId)
            return NotificationActionResult.ALREADY_DONE
        }

        return try {
            NotificationHelper.cancelNotification(app, NotificationHelper.notificationIdFor(localId))
            SnoozeStore.snooze(app, localId, scheduled)
            dao.markRead(id, sc.recipientId, System.currentTimeMillis())
            NotificationActionResult.DONE
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            NotificationActionResult.ERROR
        }
    }

    /**
     * Revalida no momento da ação (a lista na tela pode estar desatualizada): tipo com ação, dentro
     * da janela e dose MAIS RECENTE do medicamento, para nunca mexer no lembrete/adiamento de
     * uma dose mais nova.
     */
    private suspend fun isActionable(
        sc: Scope,
        n: NotificationEntity,
        localId: Long,
        scheduled: Long
    ): Boolean {
        if (n.tipo !in ACTION_TYPES) return false
        if (System.currentTimeMillis() - scheduled > ACTION_WINDOW_MILLIS) return false
        return dao.latestActionableScheduledAt(sc.recipientId, localId) == scheduled
    }

    /** Dose resolvida: some o aviso do sistema e o adiamento pendente (igual ao DismissAlarmReceiver). */
    private suspend fun afterDoseHandled(sc: Scope, notificationId: Long, localId: Long) {
        SnoozeStore.cancel(app, localId)
        NotificationHelper.cancelNotification(app, NotificationHelper.notificationIdFor(localId))
        dao.markRead(notificationId, sc.recipientId, System.currentTimeMillis())
    }

    private companion object {
        /** Depois disso o cartão não oferece mais "Já tomei"/"Lembrar depois" (evita registrar dose velha). */
        const val ACTION_WINDOW_MILLIS = 12L * 60L * 60L * 1000L

        val ACTION_TYPES = setOf(
            NotificationType.MEDICATION_REMINDER.name,
            NotificationType.MEDICATION_DELAYED.name,
            NotificationType.MEDICATION_MISSED.name
        )

        val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DAY_HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    }
}
package com.andre.medalert.fcm

import android.content.Context
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.schedule.DoseReconciler
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.notification.NotificationHelper
import com.andre.medalert.sync.MedicationSyncWorker
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId

/**
 * Trata mensagens FCM como SINALIZAÇÃO, nunca como fonte de dados:
 * 1. toda mensagem recebida com sessão ativa dispara uma sincronização (API + Room);
 * 2. depois mostra um aviso local descrevendo o evento.
 * Os dados que a tela exibe continuam vindo do Room, alimentado pela API.
 *
 * FORMATO ESPERADO DA MENSAGEM (contrato PROPOSTO; o backend ainda não envia estes eventos —
 * ver pendências). Mensagem só de dados (data), com as chaves:
 *  - tipo: dose_registrada | medicamento_alterado | medicamento_excluido | dose_atrasada |
 *          dose_sem_confirmacao (qualquer outro valor vira um aviso genérico);
 *  - medicamento_id (opcional): ID REMOTO do medicamento;
 *  - previsto_em (opcional): horário previsto da dose (epoch millis ou ISO-8601 com offset);
 *  - titulo / mensagem (opcionais): textos prontos; sem eles usa-se um texto padrão do tipo.
 * Convite de cuidador (/api/users/requestCuidador): a mensagem traz `notification` (título/texto)
 * e `data = { pacientId }`. Com o app ABERTO este handler persiste o convite via [InviteIntake];
 * com o app em segundo plano/fechado quem mostra a notificação é o sistema e o `pacientId` chega
 * pelos extras do Intent na MainActivity (ver InviteIntake).
 */
object RemoteEventHandler {

    private const val TAG = "RemoteEventHandler"

    const val KEY_TIPO = "tipo"
    const val KEY_MEDICAMENTO_ID = "medicamento_id"
    const val KEY_PREVISTO_EM = "previsto_em"
    const val KEY_TITULO = "titulo"
    const val KEY_MENSAGEM = "mensagem"

    /** Tipos de evento conhecidos. [alert] = alerta de atraso: só aparece se a dose segue sem registro. */
    enum class EventType(
        val wire: String,
        val defaultTitle: String,
        val defaultText: String,
        val alert: Boolean = false
    ) {
        DOSE_REGISTRADA("dose_registrada", "Dose registrada", "Uma dose foi registrada."),
        MEDICAMENTO_ALTERADO(
            "medicamento_alterado", "Medicamento alterado", "Um medicamento do paciente foi alterado."
        ),
        MEDICAMENTO_EXCLUIDO(
            "medicamento_excluido", "Medicamento excluído", "Um medicamento do paciente foi excluído."
        ),
        DOSE_ATRASADA(
            "dose_atrasada", "Dose atrasada", "Uma dose do paciente está atrasada.", alert = true
        ),
        DOSE_SEM_CONFIRMACAO(
            "dose_sem_confirmacao",
            "Dose sem confirmação",
            "Uma dose do paciente ainda não foi confirmada.",
            alert = true
        );

        companion object {
            fun fromWire(raw: String?): EventType? =
                entries.firstOrNull { it.wire.equals(raw?.trim(), ignoreCase = true) }
        }
    }

    /**
     * Chamado pelo [MedAlertMessagingService]. Roda na thread de background do FCM, então
     * bloquear brevemente (Room/DataStore) é seguro.
     */
    fun handle(context: Context, data: Map<String, String>, notifTitle: String?, notifBody: String?) {
        val app = context.applicationContext
        try {
            runBlocking(Dispatchers.IO) {
                val session = UserPreferencesRepository(app).session.first()
                // Sem sessão (logout) não há o que sincronizar nem mostrar.
                if (session.userId?.takeIf { it > 0 } == null) return@runBlocking

                // 1) Sinalização -> sincronização real (a API e o Room são a fonte de verdade).
                MedicationSyncWorker.enqueueFollowUp(app)

                // 1b) Convite de cuidador: persiste ANTES de qualquer outra decisão, para não perder
                // a solicitação. Não aceita nada: só guarda o pacientId.
                val invitePacienteId = if (data.containsKey(InviteIntake.KEY_PACIENT_ID)) {
                    InviteIntake.capture(app, data[InviteIntake.KEY_PACIENT_ID], "onMessageReceived")
                } else {
                    null
                }

                // 2) Aviso local.
                val type = EventType.fromWire(data[KEY_TIPO])
                val title = data[KEY_TITULO] ?: notifTitle ?: type?.defaultTitle
                ?: invitePacienteId?.let { INVITE_DEFAULT_TITLE }
                val text = data[KEY_MENSAGEM] ?: notifBody ?: type?.defaultText
                ?: invitePacienteId?.let { INVITE_DEFAULT_TEXT }
                if (title == null || text == null) return@runBlocking

                // Eventos sobre o paciente não interessam a um cuidador que ainda não tem vínculo.
                val unlinkedCaregiver = AccessPolicy.isCaregiver(session.tipo) &&
                        !AccessPolicy.canViewLinkedPatient(session.tipo, session.pacienteId)
                if (type != null && unlinkedCaregiver) return@runBlocking

                val medicationRemoteId = data[KEY_MEDICAMENTO_ID]?.toIntOrNull()
                val scheduledAt = parseMillis(data[KEY_PREVISTO_EM])

                // Alerta de atraso para uma dose que o Room já mostra como registrada seria falso.
                if (type?.alert == true && medicationRemoteId != null && scheduledAt != null) {
                    val already = AppDatabase.getInstance(app).doseHistoryDao()
                        .countByRemoteDose(medicationRemoteId, scheduledAt) > 0
                    if (already) {
                        AppLog.d(TAG, "Alerta descartado: a dose já consta como registrada")
                        return@runBlocking
                    }
                }

                NotificationHelper.showRemoteEventNotification(
                    context = app,
                    notificationId = notificationIdFor(type, medicationRemoteId, scheduledAt, title),
                    title = title,
                    text = text,
                    invitePacienteId = invitePacienteId
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "Falha ao tratar mensagem FCM", e)
        }
    }

    /** O mesmo evento (tipo + medicamento + horário) substitui o aviso anterior em vez de duplicar. */
    private fun notificationIdFor(
        type: EventType?,
        medicationRemoteId: Int?,
        scheduledAt: Long?,
        title: String
    ): Int {
        val hash = listOf(type?.wire ?: title, medicationRemoteId, scheduledAt).hashCode()
        return REMOTE_NOTIFICATION_ID_BASE + (hash and 0xFFFFF)
    }

    private fun parseMillis(raw: String?): Long? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        text.toLongOrNull()?.let { return it }
        return DoseReconciler.parseTakenMillis(text, LocalDate.now(), ZoneId.systemDefault())
    }

    private const val REMOTE_NOTIFICATION_ID_BASE = 9_000_000

    // Só usados se a mensagem vier sem `notification` nem textos próprios.
    private const val INVITE_DEFAULT_TITLE = "Você tem um novo paciente"
    private const val INVITE_DEFAULT_TEXT =
        "Um paciente deseja que você seja o cuidador dele. Abra o aplicativo para ver o convite."
}
package com.andre.medalert.notification

import android.content.Context
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.entity.NotificationEntity
import com.andre.medalert.data.model.NotificationType
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * PONTO ÚNICO que grava uma notificação na caixa de entrada do usuário LOGADO neste aparelho.
 *
 * Regras:
 * - O destinatário é sempre o usuário da sessão (paciente ou cuidador).
 * - Só grava acontecimentos do PACIENTE-ALVO da sessão (o próprio paciente, ou o paciente vinculado
 *   ao cuidador). Um cuidador sem vínculo, ou um evento de outro paciente, é recusado: nada é gravado.
 *   Única exceção, explícita ([allowUnlinkedPatient]): o CONVITE de vínculo recebido por um cuidador,
 *   que por natureza vem de um paciente ao qual ele ainda não está vinculado.
 * - Nunca duplica: a [dedupeKey] tem índice único por destinatário (insert devolve -1 na repetição).
 * - Nunca lança exceção (exceto cancelamento): se gravar a notificação falhar, o fluxo que a chamou
 *   (registro de dose, cadastro de medicamento, alarme) continua normalmente.
 *
 * [init] é chamado uma vez no MedAlertApplication. Assim, camadas sem Context (como o
 * MedicationRepository) podem gravar passando `context = null`. Sem [init] (testes de JVM) o
 * gravador não faz nada.
 */
object NotificationRecorder {

    private const val TAG = "NotificationRecorder"
    private const val MAX_PER_RECIPIENT = 500

    const val ORIGEM_LOCAL = "local"
    const val ORIGEM_FCM = "fcm"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ------------------------------------------------------------------ chaves de deduplicação

    /**
     * Medicamento na chave: ID REMOTO quando conhecido (igual em todos os aparelhos) ou, na falta
     * dele, o ID local. Assim o mesmo acontecimento vindo por caminhos diferentes colapsa em um só.
     */
    private fun medicationRef(remoteId: Int?, localId: Long?): String = when {
        remoteId != null -> "r$remoteId"
        localId != null -> "l$localId"
        else -> "x"
    }

    fun doseTakenKey(
        patientId: Int,
        medicationRemoteId: Int?,
        medicationLocalId: Long?,
        previstoEm: Long
    ): String =
        "dose_taken:$patientId:${medicationRef(medicationRemoteId, medicationLocalId)}:$previstoEm"

    /** Lembrete: o adiamento reapresenta a MESMA dose (mesmo previstoEm), então não duplica. */
    fun reminderKey(patientId: Int, medicationLocalId: Long, previstoEm: Long): String =
        "reminder:$patientId:l$medicationLocalId:$previstoEm"

    /** Cadastro: o ID local nunca muda, então reenvio e sincronização não geram segundo aviso. */
    fun medicationCreatedKey(patientId: Int, medicationLocalId: Long): String =
        "med_created:$patientId:l$medicationLocalId"

    /**
     * Edição: cada edição é um acontecimento próprio. A mesma edição repetida no mesmo minuto
     * (toque duplo, reenvio) tem o mesmo conteúdo e cai na mesma chave.
     */
    fun medicationEditedKey(
        patientId: Int,
        medicationRemoteId: Int?,
        medicationLocalId: Long,
        changesHash: Int,
        minuteBucket: Long
    ): String =
        "med_edited:$patientId:${medicationRef(medicationRemoteId, medicationLocalId)}:$changesHash:$minuteBucket"

    /** Exclusão: o ID local não é reaproveitado (AUTOINCREMENT preservado), então a chave é estável. */
    fun medicationRemovedKey(
        patientId: Int,
        medicationRemoteId: Int?,
        medicationLocalId: Long
    ): String =
        "med_removed:$patientId:${medicationRef(medicationRemoteId, medicationLocalId)}"

    /** Dose atrasada / não registrada: uma vez por dose e por tipo. */
    fun overdueKey(missed: Boolean, patientId: Int, medicationLocalId: Long, previstoEm: Long): String =
        "${if (missed) "dose_missed" else "dose_delayed"}:$patientId:l$medicationLocalId:$previstoEm"

    /**
     * Evento recebido por FCM. Usa o horário previsto da dose quando a mensagem o traz; senão o
     * messageId do FCM (reentrega da mesma mensagem não duplica); senão o minuto atual.
     */
    fun remoteEventKey(
        patientId: Int,
        wire: String,
        medicationRemoteId: Int?,
        previstoEm: Long?,
        messageId: String?
    ): String {
        val discriminator = previstoEm?.toString()
            ?: messageId
            ?: "t${System.currentTimeMillis() / 60_000L}"
        return "fcm:$patientId:$wire:${medicationRef(medicationRemoteId, null)}:$discriminator"
    }

    fun inviteReceivedKey(pacienteId: Int, receivedAtMillis: Long): String =
        "invite_received:$pacienteId:$receivedAtMillis"

    fun inviteSentKey(patientId: Int, email: String, sentAtMillis: Long): String =
        "invite_sent:$patientId:${email.trim().lowercase()}:$sentAtMillis"

    // ------------------------------------------------------------------ gravação

    /**
     * @param context quando null usa o Context guardado por [init].
     * @return true se a notificação foi gravada agora; false se já existia, foi recusada ou falhou.
     */
    suspend fun record(
        context: Context?,
        tipo: NotificationType,
        titulo: String,
        descricao: String,
        dedupeKey: String,
        patientId: Int,
        medicationLocalId: Long? = null,
        medicationRemoteId: Int? = null,
        medicationNome: String? = null,
        previstoEm: Long? = null,
        origem: String = ORIGEM_LOCAL,
        criadaEm: Long = System.currentTimeMillis(),
        allowUnlinkedPatient: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val app = (context ?: appContext)?.applicationContext ?: return@withContext false
            val session = UserPreferencesRepository(app).session.first()

            val recipientId = session.userId?.takeIf { it > 0 } ?: return@withContext false

            // Vínculo autorizado: só o paciente-alvo da sessão. Exceção: convite recebido por cuidador.
            val allowed = if (allowUnlinkedPatient) {
                AccessPolicy.isCaregiver(session.tipo)
            } else {
                val sessionPatientId =
                    AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)
                sessionPatientId != null && sessionPatientId == patientId
            }
            if (!allowed) return@withContext false

            val dao = AppDatabase.getInstance(app).notificationDao()
            val inserted = dao.insertIgnore(
                NotificationEntity(
                    recipientUserId = recipientId,
                    patientId = patientId,
                    tipo = tipo.name,
                    titulo = titulo,
                    descricao = descricao,
                    criadaEm = criadaEm,
                    dedupeKey = dedupeKey,
                    medicationLocalId = medicationLocalId,
                    medicationRemoteId = medicationRemoteId,
                    medicationNome = medicationNome,
                    previstoEm = previstoEm,
                    origem = origem
                )
            ) != -1L

            if (inserted) dao.trimTo(recipientId, MAX_PER_RECIPIENT)
            inserted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Não foi possível gravar a notificação", e)
            false
        }
    }
}
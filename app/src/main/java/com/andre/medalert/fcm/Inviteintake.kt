package com.andre.medalert.fcm

import android.content.Context
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.model.NotificationType
import com.andre.medalert.data.model.PendingInviteCodec
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.notification.NotificationRecorder
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.flow.first

/**
 * Ponto ÚNICO de entrada do convite de cuidador, venha ele de onde vier:
 *  - app em primeiro plano: [RemoteEventHandler] (onMessageReceived) com `message.data`;
 *  - app em segundo plano/fechado: a notificação foi exibida pelo sistema e o `data` chega como
 *    extras do Intent quando o usuário toca nela (MainActivity).
 *
 * Só PERSISTE o convite (DataStore). Não aceita nada e não chama /users/accept.
 * Nunca registra tokens: o log só mostra o ID do paciente (não é segredo) e flags.
 *
 * Caixa de notificações: quando o convite é NOVO (não estava guardado), também é gravado um aviso
 * na caixa do cuidador. Como todos os caminhos passam por aqui, o convite aparece na caixa com o
 * app aberto, em segundo plano ou fechado, e a repetição do mesmo convite não duplica o aviso.
 */
object InviteIntake {

    private const val TAG = "InviteIntake"

    /** Chave real enviada pelo backend (grafia exata: "pacientId"). */
    const val KEY_PACIENT_ID = "pacientId"

    /**
     * Guarda o convite se a sessão for de CUIDADOR. Devolve o ID do paciente quando o convite
     * ficou guardado (novo ou já existente), ou null quando foi ignorado (valor inválido,
     * sem sessão ou sessão de paciente).
     */
    suspend fun capture(context: Context, rawPacienteId: String?, origem: String): Int? {
        AppLog.d(TAG, "Convite recebido ($origem); chave pacientId presente=${rawPacienteId != null}")

        val pacienteId = PendingInviteCodec.parsePacienteId(rawPacienteId)
        if (pacienteId == null) {
            AppLog.d(TAG, "Convite ignorado: pacientId ausente ou inválido")
            return null
        }

        val prefs = UserPreferencesRepository(context.applicationContext)
        val session = prefs.session.first()
        val hasSession = session.userId?.takeIf { it > 0 } != null
        if (!hasSession || !AccessPolicy.isCaregiver(session.tipo)) {
            AppLog.d(TAG, "Convite ignorado: não há sessão de cuidador neste aparelho")
            return null
        }

        val receivedAt = System.currentTimeMillis()
        val novo = prefs.addPendingInvite(pacienteId, receivedAt)
        AppLog.d(
            TAG,
            "Convite do paciente id=$pacienteId " + if (novo) "armazenado" else "já estava armazenado"
        )

        if (novo) {
            // O convite vem de um paciente ao qual o cuidador ainda NÃO está vinculado: é a única
            // situação em que o gravador aceita um paciente fora do vínculo (allowUnlinkedPatient).
            NotificationRecorder.record(
                context = context,
                tipo = NotificationType.RESPONSIBLE_ADDED,
                titulo = "Convite de acompanhamento",
                descricao = "Um paciente convidou você para ser o cuidador dele. " +
                        "Abra o convite para aceitar ou recusar.",
                dedupeKey = NotificationRecorder.inviteReceivedKey(pacienteId, receivedAt),
                patientId = pacienteId,
                origem = NotificationRecorder.ORIGEM_FCM,
                criadaEm = receivedAt,
                allowUnlinkedPatient = true
            )
        }
        return pacienteId
    }
}
package com.andre.medalert.fcm

import android.content.Context
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.model.PendingInviteCodec
import com.andre.medalert.data.session.AccessPolicy
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

        val novo = prefs.addPendingInvite(pacienteId, System.currentTimeMillis())
        AppLog.d(
            TAG,
            "Convite do paciente id=$pacienteId " + if (novo) "armazenado" else "já estava armazenado"
        )
        return pacienteId
    }
}
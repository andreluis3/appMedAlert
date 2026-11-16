package com.andre.medalert.data.session

import android.content.Context
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.fcm.FcmTokenManager
import com.andre.medalert.sync.MedicationSyncWorker
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChangedBy

/**
 * Reage às mudanças da sessão num lugar só (logout, 401, login e aceite de vínculo passam todos
 * pelo DataStore, então todos passam por aqui):
 * - sessão começou ou a IDENTIDADE mudou (usuário, tipo ou paciente-alvo): limpa o cache
 *   sincronizado de OUTRO paciente (se houver), depois sincroniza (e, para cuidador, registra o
 *   token FCM);
 * - sessão terminou: invalida o token FCM e cancela a sincronização em fila. Nenhum dado
 *   clínico é apagado: os dados pertencem ao PACIENTE e continuam no Room (inclusive quando quem
 *   saiu foi o cuidador). Quem enxerga o quê é decidido pela sessão: toda consulta filtra pelo
 *   paciente da sessão, então o próximo login só vê o que o seu vínculo permite.
 *
 * A ordem em [onSessionChanged] importa: a limpeza vem ANTES de enfileirar a sincronização.
 * Se a limpeza falhar, a sincronização é enfileirada mesmo assim: as telas só leem o paciente
 * da sessão, então nada se mistura, e a limpeza é refeita na próxima abertura (o marcador de
 * dono só é atualizado depois de limpar).
 * Iniciado uma vez em [com.andre.medalert.MedAlertApplication].
 */
object SessionLifecycle {

    private const val TAG = "SessionLifecycle"

    suspend fun observe(context: Context) {
        val app = context.applicationContext
        var previous: SessionIdentity? = null

        UserPreferencesRepository(app).session
            // Só muda o que importa: userId, tipo e paciente-alvo. Nome/e-mail/token não disparam nada.
            .distinctUntilChangedBy { it.identity() }
            .collect { current ->
                val before = previous
                val now = current.identity()
                previous = now
                try {
                    when {
                        now.userId != null -> onSessionChanged(app, current)
                        before?.userId != null -> onSessionEnded(app)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e(TAG, "Falha ao reagir à mudança de sessão", e)
                }
            }
    }

    private suspend fun onSessionChanged(app: Context, current: SessionInfo) {
        // Outro login no mesmo aparelho, troca de tipo, troca de paciente ou vínculo removido:
        // some o cache sincronizado do dono anterior antes de qualquer nova sincronização.
        try {
            SessionDataCleaner.reconcile(app, current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "Falha ao reconciliar o cache local; a sincronização segue", e)
        }

        MedicationSyncWorker.schedulePeriodic(app)
        MedicationSyncWorker.enqueueFollowUp(app)
    }

    private suspend fun onSessionEnded(app: Context) {
        MedicationSyncWorker.cancelQueued(app)
        FcmTokenManager.deleteToken()
        // Nada é apagado do Room (decisão do projeto): os dados são do paciente, e as pendências
        // de envio também continuam salvas.
    }
}
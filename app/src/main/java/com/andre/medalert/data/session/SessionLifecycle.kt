package com.andre.medalert.data.session

import android.content.Context
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.fcm.FcmTokenManager
import com.andre.medalert.sync.MedicationSyncWorker
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reage às mudanças da sessão num lugar só (logout, 401, login e aceite de vínculo passam todos
 * pelo DataStore, então todos passam por aqui):
 * - sessão começou ou o vínculo mudou: limpa o cache sincronizado de OUTRO paciente (se houver),
 *   depois sincroniza (e, para cuidador, registra o token FCM);
 * - sessão terminou: invalida o token FCM, cancela a sincronização em fila e, no cuidador, limpa
 *   o cache do paciente neste aparelho. Registros ainda pendentes de envio NUNCA são apagados.
 *
 * A ordem em [onSessionChanged] importa: a limpeza vem ANTES de enfileirar a sincronização.
 * Iniciado uma vez em [com.andre.medalert.MedAlertApplication].
 */
object SessionLifecycle {

    private const val TAG = "SessionLifecycle"

    suspend fun observe(context: Context) {
        val app = context.applicationContext
        var previous: SessionInfo? = null

        UserPreferencesRepository(app).session.distinctUntilChanged().collect { current ->
            val before = previous
            previous = current
            try {
                when {
                    current.userId != null &&
                            (before?.userId != current.userId || before?.pacienteId != current.pacienteId) ->
                        onSessionChanged(app, current)

                    before?.userId != null && current.userId == null -> onSessionEnded(app, before)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "Falha ao reagir à mudança de sessão", e)
            }
        }
    }

    private suspend fun onSessionChanged(app: Context, current: SessionInfo) {
        // Outro login no mesmo aparelho, troca de paciente ou vínculo removido: some o cache
        // sincronizado do dono anterior antes de qualquer nova sincronização.
        SessionDataCleaner.reconcile(app, current)

        MedicationSyncWorker.schedulePeriodic(app)
        MedicationSyncWorker.enqueueFollowUp(app)
    }

    private suspend fun onSessionEnded(app: Context, ended: SessionInfo) {
        MedicationSyncWorker.cancelQueued(app)
        FcmTokenManager.deleteToken()

        // Cuidador: o cache é de OUTRA pessoa e não deve ficar no aparelho. Paciente: os dados
        // são dele e não aparecem para ninguém; a limpeza acontece se OUTRO paciente entrar
        // (reconcile), o que preserva o histórico quando ele só reloga (ex.: token expirado).
        if (AccessPolicy.isCaregiver(ended.tipo)) {
            val patientId = ended.pacienteId?.takeIf { it > 0 } ?: return
            SessionDataCleaner.purgeCaregiverCache(app, patientId)
        }
    }
}
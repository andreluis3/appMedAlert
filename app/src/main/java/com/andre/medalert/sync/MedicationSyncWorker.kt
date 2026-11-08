package com.andre.medalert.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.data.repository.MedicationRepository
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.fcm.FcmTokenManager
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sincronização completa e ORDENADA do paciente da sessão (o próprio paciente, ou o paciente
 * vinculado ao cuidador). É o ponto único chamado por: abertura do app, início de sessão,
 * mudança de vínculo, cadastro offline, evento FCM, volta da conexão (constraint de rede do
 * WorkManager) e execução periódica.
 *
 * Ordem (cada passo depende do anterior):
 * 1. token FCM do cuidador (melhor esforço; não bloqueia o resto);
 * 2. medicamentos criados offline sobem (POST /api/meds) e ganham remoteId — só paciente;
 * 3. GET /api/meds/{pacienteId} atualiza o Room;
 * 4. administrações pendentes sobem (POST /api/historys) — paciente e cuidador; agora as doses
 *    que esperavam o remoteId do medicamento já podem seguir;
 * 5. GET doseDia reconcilia o dia de hoje no Room (traz o que o outro aparelho registrou).
 *
 * Nada é apagado do Room. Só falhas de rede/servidor pedem nova tentativa; 4xx permanente
 * não entra em loop. Seguro de disparar várias vezes: os repositórios usam travas de processo.
 */
class MedicationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val prefs = UserPreferencesRepository(app)
        val session = prefs.session.first()
        if (session.userId?.takeIf { it > 0 } == null) return Result.success()

        return try {
            var retry = false

            // 1) Token FCM (só cuidador envia; ver FcmTokenManager).
            if (FcmTokenManager.registerIfNeeded(app).exceptionOrNull() is IOException) retry = true

            // Cuidador sem vínculo: nada mais a sincronizar até aceitar o convite.
            val patientId = AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)
                ?: return if (retry) Result.retry() else Result.success()

            val db = AppDatabase.getInstance(app)
            val medicationRepository = MedicationRepository(db.medicationDao(), prefs)
            val historyRepository = HistoryRepository(db.doseHistoryDao())

            // 2) Medicamentos pendentes (no-op para cuidador).
            if (medicationRepository.syncPending().exceptionOrNull() is IOException) retry = true

            // 3) Lista de medicamentos atualizada.
            if (medicationRepository.refresh(patientId).exceptionOrNull() is IOException) retry = true

            // 4) Administrações pendentes.
            if (AccessPolicy.canRegisterDose(session.tipo, session.userId, session.pacienteId)) {
                val outcome = historyRepository.syncPending(patientId)
                AppLog.d(
                    TAG,
                    "doses: enviadas=${outcome.sent} duplicadas=${outcome.duplicates} " +
                            "recusadas=${outcome.rejected} aguardando=${outcome.waiting}"
                )
                if (outcome.shouldRetry) retry = true
            }

            // 5) Estado de hoje vindo do servidor.
            val medications = db.doseHistoryDao().medicationsOf(patientId)
            if (historyRepository.refresh(patientId, medications).exceptionOrNull() is IOException) {
                retry = true
            }

            if (retry) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "Falha inesperada na sincronização", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MedicationSyncWorker"
        private const val UNIQUE_NAME = "medication_sync"
        private const val PERIODIC_NAME = "medication_sync_periodic"

        private val NETWORK = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun request() = OneTimeWorkRequestBuilder<MedicationSyncWorker>()
            .setConstraints(NETWORK)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        /**
         * Pede uma sincronização. KEEP: se já existe uma na fila ou rodando, esta é descartada
         * (abrir o app várias vezes não empilha trabalho). Sem internet, fica aguardando a conexão.
         */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request())
        }

        /**
         * Igual a [enqueue], mas GARANTE uma execução depois da atual (APPEND_OR_REPLACE). Use
         * quando algo mudou agora (evento FCM, vínculo aceito, token novo) e uma execução em
         * andamento pode já ter passado do ponto relevante.
         */
        fun enqueueFollowUp(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request())
        }

        /** Sincronização periódica (mínimo do WorkManager: 15 min). Só agenda uma vez (KEEP). */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<MedicationSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(NETWORK)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Cancela a sincronização em fila (logout). Administrações pendentes continuam no Room. */
        fun cancelQueued(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}

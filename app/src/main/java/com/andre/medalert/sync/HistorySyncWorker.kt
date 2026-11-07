package com.andre.medalert.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Envia ao POST /api/historys as doses tomadas que ainda estão só no celular.
 *
 * - Só roda com internet (constraint) e tenta de novo com backoff exponencial quando a falha é
 *   de rede ou do servidor.
 * - Só paciente envia: cuidador é somente leitura e sem sessão não há de quem sincronizar.
 * - É seguro disparar várias vezes: o repositório usa uma trava e só marca isSynced após 2xx.
 */
class HistorySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val session = UserPreferencesRepository(applicationContext).session.first()
        if (!AccessPolicy.canModifyMedications(session.tipo)) return Result.success()
        val userId = session.userId?.takeIf { it > 0 } ?: return Result.success()

        return try {
            val repository = HistoryRepository(
                AppDatabase.getInstance(applicationContext).doseHistoryDao()
            )
            val outcome = repository.syncPending(userId)
            AppLog.d(
                TAG,
                "sync: enviadas=${outcome.sent} recusadas=${outcome.rejected} " +
                        "aguardando=${outcome.waiting} nova tentativa=${outcome.shouldRetry}"
            )
            if (outcome.shouldRetry) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "Falha inesperada na sincronização do histórico", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "HistorySyncWorker"
        private const val UNIQUE_NAME = "history_sync"

        /**
         * Agenda um ciclo de envio. APPEND_OR_REPLACE: se já há um ciclo em andamento, este entra
         * depois dele (o em andamento nunca é cancelado no meio de um POST).
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<HistorySyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

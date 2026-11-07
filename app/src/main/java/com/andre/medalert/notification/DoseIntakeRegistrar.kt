package com.andre.medalert.notification

import android.content.Context
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.schedule.StockRules
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.sync.HistorySyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DoseRegisterResult {
    /** Registro criado no Room. */
    REGISTERED,

    /** Essa dose já estava registrada (toque duplo, notificação repetida). Nada foi duplicado. */
    ALREADY_REGISTERED,

    /** Medicamento excluído, inativo ou de outro usuário: não há o que registrar. */
    MEDICATION_UNAVAILABLE,

    /** Falha inesperada ao gravar. */
    ERROR
}

/**
 * Ponto único para registrar uma dose como tomada a partir da notificação.
 *
 * Funciona com o app fechado: lê o medicamento e grava no Room direto, sem depender de tela.
 * Também desconta o estoque (quantidade por dose) e avisa se ficou baixo/esgotado.
 * A dose é identificada por (medicamento local, horário previsto).
 *
 * O registro nasce com isSynced = false e, logo depois de gravado, é agendado o envio ao
 * POST /api/historys ([HistorySyncWorker]). Sem internet o registro continua no Room e é
 * enviado quando a conexão voltar; o estoque nunca é descontado de novo.
 */
object DoseIntakeRegistrar {

    suspend fun registerTaken(
        context: Context,
        medicationLocalId: Long,
        scheduledAtMillis: Long,
        takenAtMillis: Long = System.currentTimeMillis()
    ): DoseRegisterResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        try {
            val med = MedicationReminderManager.findDeliverable(app, medicationLocalId)
                ?: return@withContext DoseRegisterResult.MEDICATION_UNAVAILABLE

            val repository = HistoryRepository(AppDatabase.getInstance(app).doseHistoryDao())
            val result = repository.registerTakenLocally(
                userId = med.userId,
                medicationLocalId = med.id,
                medicationRemoteId = med.remoteId,
                medicationName = med.nome,
                dosage = med.dosagem,
                scheduledAtMillis = scheduledAtMillis,
                takenAtMillis = takenAtMillis,
                // Quantidade por dose do cadastro; sem informação, 1 unidade por dose.
                quantityPerDose = med.quantidade ?: 1
            )

            if (!result.inserted) return@withContext DoseRegisterResult.ALREADY_REGISTERED

            // Estoque já foi descontado na mesma transação do registro. Aqui só avisa.
            if (StockRules.shouldAlert(result.stockBefore, result.stockAfter, result.stockMinimum)) {
                NotificationHelper.showLowStockNotification(
                    context = app,
                    localId = med.id,
                    medicationName = med.nome,
                    remaining = result.stockAfter ?: 0
                )
            }

            // O registro local já está salvo: falhar ao agendar o envio não pode virar ERROR.
            try {
                HistorySyncWorker.enqueue(app)
            } catch (_: Exception) {
                // O envio será tentado de novo ao abrir o app.
            }

            DoseRegisterResult.REGISTERED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DoseRegisterResult.ERROR
        }
    }

    /** true se essa dose já foi registrada como tomada. */
    suspend fun isAlreadyRegistered(
        context: Context,
        medicationLocalId: Long,
        scheduledAtMillis: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext
            HistoryRepository(AppDatabase.getInstance(app).doseHistoryDao())
                .isDoseRegistered(medicationLocalId, scheduledAtMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
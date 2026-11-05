package com.andre.medalert.notification

import android.content.Context
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.repository.HistoryRepository
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
 * A dose é identificada por (medicamento local, horário previsto). O envio para a API ainda
 * não existe: o registro fica com isSynced = false (ver HistoryRepository.registerTakenLocally).
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
            val inserted = repository.registerTakenLocally(
                userId = med.userId,
                medicationLocalId = med.id,
                medicationRemoteId = med.remoteId,
                medicationName = med.nome,
                dosage = med.dosagem,
                scheduledAtMillis = scheduledAtMillis,
                takenAtMillis = takenAtMillis
            )

            if (inserted) DoseRegisterResult.REGISTERED else DoseRegisterResult.ALREADY_REGISTERED
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
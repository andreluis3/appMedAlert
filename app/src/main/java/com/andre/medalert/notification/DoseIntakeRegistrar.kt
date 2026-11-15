package com.andre.medalert.notification

import android.content.Context
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.sync.HistorySyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class DoseRegisterResult {
    /** Registro criado no Room. */
    REGISTERED,

    /** Essa dose já estava registrada (toque duplo, notificação repetida). Nada foi duplicado. */
    ALREADY_REGISTERED,

    /** Medicamento excluído, inativo, de outro paciente, ou sessão sem permissão: não há o que registrar. */
    MEDICATION_UNAVAILABLE,

    /** Falha inesperada ao gravar. */
    ERROR
}

/**
 * Ponto único para registrar uma dose como tomada: pela notificação (paciente) ou pela Home
 * (paciente ou cuidador do paciente vinculado).
 *
 * Funciona com o app fechado: lê o medicamento e grava no Room direto, sem depender de tela.
 * Cada registro guarda a origem ("paciente" ou "cuidador"), quem registrou e um clientEventId.
 * A dose é identificada por (medicamento local, horário previsto).
 *
 * O registro nasce com isSynced = false e, logo depois de gravado, é agendado o envio ao
 * POST /api/historys ([HistorySyncWorker]). Sem internet o registro continua no Room e é
 * enviado quando a conexão voltar.
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
            val session = UserPreferencesRepository(app).session.first()
            val patientId = AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)
            if (patientId == null ||
                !AccessPolicy.canRegisterDose(session.tipo, session.userId, session.pacienteId)
            ) {
                return@withContext DoseRegisterResult.MEDICATION_UNAVAILABLE
            }

            // Medicamento do PACIENTE-ALVO: o do próprio paciente ou o do paciente vinculado ao
            // cuidador. O Room tem uma única cópia por medicamento remoto (nada de duplicata).
            val med = AppDatabase.getInstance(app).medicationDao().getById(medicationLocalId)
                ?.takeIf { it.ativo && it.userId == patientId }
                ?: return@withContext DoseRegisterResult.MEDICATION_UNAVAILABLE

            val origin = if (AccessPolicy.isCaregiver(session.tipo)) {
                AccessPolicy.TIPO_CUIDADOR
            } else {
                AccessPolicy.TIPO_PACIENTE
            }

            val repository = HistoryRepository(AppDatabase.getInstance(app).doseHistoryDao())
            val result = repository.registerTakenLocally(
                userId = med.userId,
                medicationLocalId = med.id,
                medicationRemoteId = med.remoteId,
                medicationName = med.nome,
                dosage = med.dosagem,
                scheduledAtMillis = scheduledAtMillis,
                takenAtMillis = takenAtMillis,
                origin = origin,
                registeredById = session.userId
            )

            if (!result.inserted) return@withContext DoseRegisterResult.ALREADY_REGISTERED

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
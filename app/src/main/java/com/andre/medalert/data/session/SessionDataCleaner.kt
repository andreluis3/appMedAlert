package com.andre.medalert.data.session

import android.content.Context
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.data.repository.MedicationRepository
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Higiene do Room entre pacientes. Regras:
 *
 * - Todo dado do Room pertence a um PACIENTE (userId = id do paciente). As telas só leem o
 *   paciente da sessão, então dados de outro paciente nunca aparecem; a limpeza existe para não
 *   deixá-los no aparelho depois que a identidade muda.
 * - O marcador `local_data_owner` (DataStore) guarda de quem são os dados sincronizados em cache.
 *   Quando uma sessão começa com OUTRO paciente-alvo (outro login, troca de vínculo, vínculo
 *   removido), os registros SINCRONIZADOS do dono anterior são apagados.
 * - NUNCA se apaga o que ainda está pendente de envio (medicamentos isSynced = 0, doses
 *   isSynced = 0) nem medicamentos de que uma dose pendente precisa para subir.
 * - Por isso não se usa clearAllTables(): ele destruiria o trabalho offline.
 */
object SessionDataCleaner {

    private const val TAG = "SessionDataCleaner"

    /**
     * Chamada quando uma sessão começa ou o vínculo muda. Apaga o cache sincronizado do dono
     * anterior (se for outro paciente) e passa o marcador para o paciente atual.
     */
    suspend fun reconcile(context: Context, current: SessionInfo) {
        val app = context.applicationContext
        val prefs = UserPreferencesRepository(app)
        val newPatient = AccessPolicy.patientIdFor(current.tipo, current.userId, current.pacienteId)
        val owner = prefs.localDataOwner.first()

        if (owner != null && owner != newPatient) {
            purgeSynced(app, owner)
        }
        if (owner != newPatient) {
            prefs.saveLocalDataOwner(newPatient)
        }
    }

    /**
     * Cuidador que saiu (logout ou 401): o cache do paciente não deve ficar no aparelho.
     * Só o que já está no servidor; pendentes ficam.
     */
    suspend fun purgeCaregiverCache(context: Context, patientId: Int) {
        val app = context.applicationContext
        purgeSynced(app, patientId)
        val prefs = UserPreferencesRepository(app)
        if (prefs.localDataOwner.first() == patientId) prefs.saveLocalDataOwner(null)
    }

    /**
     * Antes do logout explícito, com o token ainda válido: tenta enviar medicamentos e doses
     * pendentes por até [timeoutMs]. Melhor esforço: o que não subir continua no Room.
     */
    suspend fun flushPending(context: Context, timeoutMs: Long = 4_000L) {
        val app = context.applicationContext
        val prefs = UserPreferencesRepository(app)
        val session = prefs.session.first()
        val patientId = AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)
            ?: return
        val db = AppDatabase.getInstance(app)

        try {
            withTimeoutOrNull(timeoutMs) {
                // Medicamentos primeiro: as doses pendentes dependem do remoteId deles.
                MedicationRepository(db.medicationDao(), prefs).syncPending()
                if (AccessPolicy.canRegisterDose(session.tipo, session.userId, session.pacienteId)) {
                    HistoryRepository(db.doseHistoryDao()).syncPending(patientId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Não foi possível enviar pendências antes do logout", e)
        }
    }

    private suspend fun purgeSynced(app: Context, patientId: Int) {
        val db = AppDatabase.getInstance(app)
        val doses = db.doseHistoryDao().deleteSyncedByUser(patientId)
        val meds = db.medicationDao().deleteSyncedByUserKeepingReferenced(patientId)
        val pendingMeds = db.medicationDao().getUnsyncedByUser(patientId).size
        val pendingDoses = db.doseHistoryDao().getUnsyncedByUser(patientId).size
        AppLog.d(
            TAG,
            "cache do paciente $patientId limpo: doses=$doses medicamentos=$meds; " +
                    "pendentes preservados: medicamentos=$pendingMeds doses=$pendingDoses"
        )
    }
}
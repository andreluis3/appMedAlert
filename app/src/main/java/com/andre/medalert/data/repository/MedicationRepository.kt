package com.andre.medalert.data.repository

import com.andre.medalert.data.local.dao.MedicationDao
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.mapper.toEntity
import com.andre.medalert.data.mapper.toRequestOrNull
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.CreateMedicamentoRequest
import com.andre.medalert.data.remote.dto.MedicamentoData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first: a UI observa SEMPRE o Room (observeMedications).
 * A API só alimenta o Room (refresh) ou recebe o que foi salvo localmente.
 */
class MedicationRepository(
    private val dao: MedicationDao,
    private val api: ApiService = RetrofitClient.instance
) {

    /** A tela observa isto. Funciona sem internet. */
    fun observeMedications(userId: Int): Flow<List<MedicationEntity>> =
        dao.observeByUser(userId)

    /** Busca na API e atualiza o Room. Se estiver offline, falha e o Room fica como está. */
    suspend fun refresh(userId: Int): Result<Unit> =
        try {
            val remote = api.getMedicamentos(userId)
            dao.replaceSynced(userId, remote.map { it.toEntity(userId) })
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * 1) salva no Room (isSynced = false)
     * 2) tenta enviar à API
     * 3) se a API responder OK, marca isSynced = true
     *
     * O resultado indica só o envio: se for falha, o medicamento JÁ está salvo
     * localmente e será reenviado depois (syncPending / WorkManager).
     */
    suspend fun saveMedication(data: MedicamentoData): Result<Unit> {
        val localId = dao.insert(data.toEntity())
        return try {
            api.createMedicamento(CreateMedicamentoRequest(data))
            dao.markSynced(localId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Reenvia o que está com isSynced = false. Será chamado pelo WorkManager (Etapa 4). */
    suspend fun syncPending(): Result<Unit> {
        var firstError: Exception? = null
        for (pending in dao.getUnsynced()) {
            val request = pending.toRequestOrNull() ?: continue
            try {
                api.createMedicamento(request)
                dao.markSynced(pending.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        return firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}
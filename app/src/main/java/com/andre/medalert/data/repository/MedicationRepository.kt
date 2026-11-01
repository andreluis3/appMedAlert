package com.andre.medalert.data.repository

import android.util.Log
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.dao.MedicationDao
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.mapper.toEntity
import com.andre.medalert.data.mapper.toMedicamentoDataOrNull
import com.andre.medalert.data.mapper.withIsoDates
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.EditMedicamentoRequest
import com.andre.medalert.data.remote.dto.MedicamentoData
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException

private const val TAG = "MedicationRepo"

/** Resultado de um cadastro bem-sucedido. */
enum class SaveOutcome {
    /** A API confirmou. */
    SYNCED,

    /** Sem internet: ficou só no celular e será reenviado depois. */
    SAVED_OFFLINE
}

/**
 * Offline-first: a UI observa SEMPRE o Room (observeMedications).
 * A API só alimenta o Room (refresh) ou recebe o que foi salvo localmente.
 *
 * IDs:
 * - userId  = ID do paciente (listar e cadastrar).
 * - entity.id = ID LOCAL do Room (a tela usa só este).
 * - entity.remoteId = ID do medicamento na API (editar e excluir).
 */
class MedicationRepository(
    private val dao: MedicationDao,
    private val userPrefs: UserPreferencesRepository,
    private val api: ApiService = RetrofitClient.instance
) {

    /** A tela observa isto. Funciona sem internet. */
    fun observeMedications(userId: Int): Flow<List<MedicationEntity>> =
        dao.observeByUser(userId)

    /** GET /api/meds/{userId} e atualiza o Room. Se falhar, o Room fica como está. */
    suspend fun refresh(userId: Int): Result<Unit> =
        try {
            val remote = api.getMedicamentos(userId)
            dao.replaceSynced(userId, remote.map { it.toEntity(userId) })
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao atualizar a lista", e)
            Result.failure(e)
        }

    /** Atalho: atualiza a lista do usuário que está na sessão. */
    suspend fun refreshForCurrentUser(): Result<Unit> {
        val userId = userPrefs.userId.first()
        if (userId == null || userId <= 0) {
            return Result.failure(Exception("Sessão expirada. Faça login novamente."))
        }
        return refresh(userId)
    }

    /**
     * POST /api/meds (CadastroMedScreen).
     * - O userId REAL vem do DataStore (o userId de [draft] é ignorado).
     * - Salva no Room, envia à API e guarda o ID do medicamento (remoteId).
     * - Erro HTTP: loga o errorBody, remove o registro local e devolve mensagem amigável.
     * - Sem internet: mantém no Room (isSynced = false) e devolve SAVED_OFFLINE.
     */
    suspend fun saveMedicationForCurrentUser(draft: MedicamentoData): Result<SaveOutcome> {
        val userId = userPrefs.userId.first()
        if (userId == null || userId <= 0) {
            return Result.failure(Exception("Sessão expirada. Faça login novamente."))
        }

        val data = draft.copy(userId = userId)
        val localId = dao.insert(data.toEntity())

        return try {
            val created = api.createMedicamento(data.withIsoDates())
            dao.markSyncedWithRemote(localId, created?.id)
            // Traz da API os horários reais (doseDiaria); se falhar, o registro já está salvo.
            refresh(userId)
            Result.success(SaveOutcome.SYNCED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            logHttpError("POST /api/meds", e, data.withIsoDates())
            dao.deleteById(localId)
            Result.failure(Exception(mensagemParaHttp(e.code())))
        } catch (e: IOException) {
            Log.w(TAG, "Sem conexão ao salvar medicamento; ficou pendente (id local=$localId)", e)
            Result.success(SaveOutcome.SAVED_OFFLINE)
        } catch (e: Exception) {
            Log.e(TAG, "Erro inesperado ao salvar medicamento", e)
            dao.deleteById(localId)
            Result.failure(Exception("Não foi possível salvar o medicamento. Tente novamente."))
        }
    }

    /**
     * POST /api/meds/edit com {"id": remoteId, "data": changes}.
     * [changes] usa os nomes da API (snake_case), ex.: mapOf("nome" to "Novo", "frequencia" to 6).
     * A lista local só muda depois que a API confirma (refresh); em caso de erro nada é alterado.
     */
    suspend fun editMedication(localId: Long, changes: Map<String, Any?>): Result<Unit> {
        if (changes.isEmpty()) return Result.success(Unit)
        val entity = dao.getById(localId)
            ?: return Result.failure(Exception("Medicamento não encontrado."))
        val remoteId = entity.remoteId
            ?: return Result.failure(Exception(semRemoteIdMessage(entity)))

        val body = EditMedicamentoRequest(id = remoteId, data = changes)
        return try {
            api.editMedicamento(body).close()
            refresh(entity.userId) // se falhar, o Room fica com o dado antigo até o próximo refresh
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            falha(e, "POST /api/meds/edit", body)
        }
    }

    /**
     * DELETE /api/meds/{remoteId}. Apaga também histórico e horários no backend (cascade).
     * Só remove do Room depois que a API confirmar. Se o medicamento nunca foi enviado
     * (criado offline), remove só localmente.
     */
    suspend fun deleteMedication(localId: Long): Result<Unit> {
        val entity = dao.getById(localId) ?: return Result.success(Unit)
        val remoteId = entity.remoteId

        if (remoteId == null) {
            if (entity.isSynced) return Result.failure(Exception(semRemoteIdMessage(entity)))
            dao.deleteById(localId)
            return Result.success(Unit)
        }

        return try {
            api.deleteMedicamento(remoteId).close()
            dao.deleteById(localId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            falha(e, "DELETE /api/meds/$remoteId", null)
        }
    }

    /** Reenvia o que está com isSynced = false. Será chamado pelo WorkManager. */
    suspend fun syncPending(): Result<Unit> {
        var firstError: Exception? = null
        for (pending in dao.getUnsynced()) {
            val data = pending.toMedicamentoDataOrNull()?.withIsoDates() ?: continue
            try {
                val created = api.createMedicamento(data)
                dao.markSyncedWithRemote(pending.id, created?.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                logHttpError("POST /api/meds (sync)", e, data)
                if (firstError == null) firstError = e
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        return firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    private fun semRemoteIdMessage(entity: MedicationEntity): String =
        if (entity.isSynced) {
            "Não foi possível identificar este medicamento no servidor. Atualize a lista e tente de novo."
        } else {
            "Este medicamento ainda não foi enviado ao servidor. Aguarde a sincronização."
        }

    private fun <T> falha(e: Exception, rota: String, enviado: Any?): Result<T> = when (e) {
        is HttpException -> {
            logHttpError(rota, e, enviado)
            Result.failure(Exception(mensagemParaHttp(e.code())))
        }
        is IOException -> Result.failure(Exception("Sem conexão com a internet. Tente novamente."))
        else -> {
            Log.e(TAG, "Erro inesperado em $rota", e)
            Result.failure(Exception("Não foi possível concluir a operação. Tente novamente."))
        }
    }

    /** Loga rota, código, errorBody e o JSON enviado. Filtre o Logcat por "MedicationRepo". */
    private fun logHttpError(rota: String, e: HttpException, sent: Any?) {
        val errorBody = try {
            e.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        Log.e(TAG, "$rota -> HTTP ${e.code()} | errorBody=$errorBody | enviado=${Gson().toJson(sent)}")
    }

    private fun mensagemParaHttp(code: Int): String = when (code) {
        401, 403 -> "Sessão expirada. Faça login novamente."
        400, 422, 500 -> "O servidor recusou a operação. Confira os dados e tente novamente."
        else -> "Erro no servidor ($code). Tente novamente em instantes."
    }
}
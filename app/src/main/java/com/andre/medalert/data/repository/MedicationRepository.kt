package com.andre.medalert.data.repository

import android.util.Log
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.dao.MedicationDao
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.mapper.toEntity
import com.andre.medalert.data.mapper.toMedicamentoDataOrNull
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
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
 */
class MedicationRepository(
    private val dao: MedicationDao,
    private val userPrefs: UserPreferencesRepository,
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
     * Cadastro vindo da tela CadastroMedScreen.
     *
     * - O userId REAL vem do DataStore (o userId de [draft] é ignorado).
     * - Salva no Room, tenta enviar à API (JSON flat, sem wrapper) e marca isSynced se der certo.
     * - Erro HTTP (ex.: 500): loga o errorBody, remove o registro local e devolve mensagem amigável.
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
            api.createMedicamento(data)
            dao.markSynced(localId)
            Result.success(SaveOutcome.SYNCED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            logHttpError(e, data)
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

    /** Reenvia o que está com isSynced = false. Será chamado pelo WorkManager (Etapa 4). */
    suspend fun syncPending(): Result<Unit> {
        var firstError: Exception? = null
        for (pending in dao.getUnsynced()) {
            val data = pending.toMedicamentoDataOrNull() ?: continue
            try {
                api.createMedicamento(data)
                dao.markSynced(pending.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                logHttpError(e, data)
                if (firstError == null) firstError = e
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        return firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    /** Loga código, errorBody e o JSON enviado. Filtre o Logcat por "MedicationRepo". */
    private fun logHttpError(e: HttpException, sent: MedicamentoData) {
        val errorBody = try {
            e.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        Log.e(
            TAG,
            "POST /api/meds -> HTTP ${e.code()} | errorBody=$errorBody | enviado=${Gson().toJson(sent)}"
        )
    }

    private fun mensagemParaHttp(code: Int): String = when (code) {
        401, 403 -> "Sessão expirada. Faça login novamente."
        400, 422, 500 -> "O servidor recusou os dados. Confira nome, dosagem, frequência, quantidade e datas."
        else -> "Erro no servidor ($code). Tente novamente em instantes."
    }
}
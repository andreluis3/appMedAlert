package com.andre.medalert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Resultado de registrar uma dose tomada.
 * [inserted] = false: a dose não foi gravada. Se [refused] = true, foi RECUSADA porque o
 * medicamento pertence a outro paciente; se [refused] = false, a dose já estava registrada.
 */
data class TakenInsertResult(
    val inserted: Boolean,
    val refused: Boolean = false
)

@Dao
abstract class DoseHistoryDao {

    @Query("SELECT * FROM dose_history WHERE userId = :userId ORDER BY medicamentoNome COLLATE NOCASE, horario")
    abstract fun observeByUser(userId: Int): Flow<List<DoseHistoryEntity>>

    /** Só as doses de um dia (yyyy-MM-dd). */
    @Query(
        """
        SELECT * FROM dose_history
        WHERE userId = :userId AND dia = :dia
        ORDER BY medicamentoNome COLLATE NOCASE, horario
        """
    )
    abstract fun observeByUserOnDay(userId: Int, dia: String): Flow<List<DoseHistoryEntity>>

    /** Doses tomadas pela notificação desde [fromMillis]. A Home usa para marcar "Tomado". */
    @Query(
        """
        SELECT * FROM dose_history
        WHERE userId = :userId
          AND medicationLocalId IS NOT NULL
          AND (status = 'tomado' OR tomadoEm IS NOT NULL)
          AND previstoEm >= :fromMillis
        """
    )
    abstract fun observeLocalTaken(userId: Int, fromMillis: Long): Flow<List<DoseHistoryEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM dose_history
        WHERE medicationLocalId = :medicationLocalId AND previstoEm = :previstoEm
        """
    )
    abstract suspend fun countByDose(medicationLocalId: Long, previstoEm: Long): Int

    /**
     * Identidade da dose entre aparelhos: medicamento REMOTO + horário previsto.
     * Complementa o índice único local (medicationLocalId, previstoEm), que só vale neste aparelho.
     */
    @Query(
        """
        SELECT COUNT(*) FROM dose_history
        WHERE medicamentoId = :medicamentoId AND previstoEm = :previstoEm
        """
    )
    abstract suspend fun countByRemoteDose(medicamentoId: Int, previstoEm: Long): Int

    /** Medicamentos do paciente no Room (o reconciliador do dia precisa deles para associar nomes). */
    @Query("SELECT * FROM medications WHERE userId = :userId")
    abstract suspend fun medicationsOf(userId: Int): List<MedicationEntity>

    /**
     * Limpa o cache do paciente neste aparelho. Só remove o que já está no servidor
     * (isSynced = 1): administrações pendentes de envio NUNCA são apagadas.
     */
    @Query("DELETE FROM dose_history WHERE userId = :userId AND isSynced = 1")
    abstract suspend fun deleteSyncedByUser(userId: Int): Int

    /** Pendentes de envio de TODOS os pacientes. O sync usa [getUnsyncedByUser]. */
    @Deprecated("Mistura pacientes. Use getUnsyncedByUser(userId).")
    @Query("SELECT * FROM dose_history WHERE isSynced = 0 AND medicamentoRemovido = 0")
    abstract suspend fun getUnsynced(): List<DoseHistoryEntity>

    /** Pendentes de envio do paciente [userId], da mais antiga para a mais nova. */
    @Query(
        """
        SELECT * FROM dose_history
        WHERE userId = :userId AND isSynced = 0 AND medicamentoRemovido = 0
        ORDER BY tomadoEm, id
        """
    )
    abstract suspend fun getUnsyncedByUser(userId: Int): List<DoseHistoryEntity>

    /** ID REMOTO do medicamento (null se ainda não foi sincronizado ou se não existe mais). */
    @Deprecated("Não confere o dono do medicamento. Use remoteIdOfForUser.")
    @Query("SELECT remoteId FROM medications WHERE id = :medicationLocalId")
    abstract suspend fun remoteIdOf(medicationLocalId: Long): Int?

    /** ID REMOTO do medicamento, só se ele pertence ao paciente [userId]. */
    @Query("SELECT remoteId FROM medications WHERE id = :medicationLocalId AND userId = :userId")
    abstract suspend fun remoteIdOfForUser(medicationLocalId: Long, userId: Int): Int?

    /** Paciente dono do medicamento local (null se o medicamento não existe mais). */
    @Query("SELECT userId FROM medications WHERE id = :medicationLocalId")
    abstract suspend fun medicationOwnerOf(medicationLocalId: Long): Int?

    @Insert
    abstract suspend fun insertAll(entities: List<DoseHistoryEntity>)

    /** Retorna -1 se a dose já existe (índice único): é assim que evitamos duplicatas. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(entity: DoseHistoryEntity): Long

    @Deprecated("Não confere o dono da linha. Use markSyncedForUser.")
    @Query("UPDATE dose_history SET isSynced = 1 WHERE id = :id")
    abstract suspend fun markSynced(id: Long)

    /** Marca como sincronizada a dose [id], só se ela pertence ao paciente [userId]. */
    @Query("UPDATE dose_history SET isSynced = 1 WHERE id = :id AND userId = :userId")
    abstract suspend fun markSyncedForUser(id: Long, userId: Int): Int

    /**
     * Marca como sincronizada a dose local (medicamento + horário previsto) que o servidor
     * já informa como tomada. Evita reenviar um POST cuja resposta se perdeu.
     */
    @Deprecated("Não confere o dono. Use markSyncedByDoseForUser.")
    @Query(
        """
        UPDATE dose_history SET isSynced = 1
        WHERE medicationLocalId = :medicationLocalId AND previstoEm = :previstoEm AND isSynced = 0
        """
    )
    abstract suspend fun markSyncedByDose(medicationLocalId: Long, previstoEm: Long): Int

    /** Igual a [markSyncedByDose], limitado às doses do paciente [userId]. */
    @Query(
        """
        UPDATE dose_history SET isSynced = 1
        WHERE userId = :userId AND medicationLocalId = :medicationLocalId
          AND previstoEm = :previstoEm AND isSynced = 0
        """
    )
    abstract suspend fun markSyncedByDoseForUser(
        userId: Int,
        medicationLocalId: Long,
        previstoEm: Long
    ): Int

    /**
     * Registra a dose tomada, sem duplicar.
     * Barreiras:
     * 1. Dono: se o medicamento local existe e pertence a OUTRO paciente que não o da dose, a
     *    dose é RECUSADA (refused = true) e nada é gravado.
     * 2. Identidade REMOTA (medicamento remoto + horário previsto, ex.: dose que outro aparelho
     *    já registrou).
     * 3. Índice único local (medicationLocalId, previstoEm), que faz o insert devolver -1 em dose
     *    repetida (toque duplo, duas notificações, reentrega).
     */
    @Transaction
    open suspend fun insertTaken(entity: DoseHistoryEntity): TakenInsertResult {
        val localMedicationId = entity.medicationLocalId
        if (localMedicationId != null) {
            val owner = medicationOwnerOf(localMedicationId)
            if (owner != null && owner != entity.userId) {
                return TakenInsertResult(inserted = false, refused = true)
            }
        }

        val remoteMedicationId = entity.medicamentoId
        val scheduledAt = entity.previstoEm
        if (remoteMedicationId != null && scheduledAt != null &&
            countByRemoteDose(remoteMedicationId, scheduledAt) > 0
        ) {
            return TakenInsertResult(inserted = false)
        }

        return TakenInsertResult(inserted = insertIgnore(entity) != -1L)
    }
}
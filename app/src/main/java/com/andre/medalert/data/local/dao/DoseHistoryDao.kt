package com.andre.medalert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DoseHistoryDao {

    @Query("SELECT * FROM dose_history WHERE userId = :userId ORDER BY medicamentoNome COLLATE NOCASE, horario")
    abstract fun observeByUser(userId: Int): Flow<List<DoseHistoryEntity>>

    /** Só as doses de um dia (yyyy-MM-dd). A HistoryScreen mostra o dia atual. */
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
          AND status = 'tomado'
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

    @Query("SELECT * FROM dose_history WHERE isSynced = 0")
    abstract suspend fun getUnsynced(): List<DoseHistoryEntity>

    @Insert
    abstract suspend fun insertAll(entities: List<DoseHistoryEntity>)

    /** Retorna -1 se a dose já existe (índice único): é assim que evitamos duplicatas. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(entity: DoseHistoryEntity): Long

    @Query("UPDATE dose_history SET isSynced = 1 WHERE id = :id")
    abstract suspend fun markSynced(id: Long)

    @Query("DELETE FROM dose_history WHERE userId = :userId AND isSynced = 1")
    abstract suspend fun deleteSyncedByUser(userId: Int)

    /** Troca o histórico sincronizado pelo da API, mantendo doses ainda não enviadas. */
    @Transaction
    open suspend fun replaceSynced(userId: Int, entities: List<DoseHistoryEntity>) {
        deleteSyncedByUser(userId)
        insertAll(entities)
    }
}
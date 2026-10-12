package com.andre.medalert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DoseHistoryDao {

    @Query("SELECT * FROM dose_history WHERE userId = :userId ORDER BY medicamentoNome COLLATE NOCASE, horario")
    abstract fun observeByUser(userId: Int): Flow<List<DoseHistoryEntity>>

    @Query("SELECT * FROM dose_history WHERE isSynced = 0")
    abstract suspend fun getUnsynced(): List<DoseHistoryEntity>

    @Insert
    abstract suspend fun insertAll(entities: List<DoseHistoryEntity>)

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
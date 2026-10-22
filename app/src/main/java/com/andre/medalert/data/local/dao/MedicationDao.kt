package com.andre.medalert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.andre.medalert.data.local.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MedicationDao {

    @Query("SELECT * FROM medications WHERE userId = :userId ORDER BY nome COLLATE NOCASE")
    abstract fun observeByUser(userId: Int): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE isSynced = 0")
    abstract suspend fun getUnsynced(): List<MedicationEntity>

    @Insert
    abstract suspend fun insert(entity: MedicationEntity): Long

    @Insert
    abstract suspend fun insertAll(entities: List<MedicationEntity>)

    @Query("UPDATE medications SET isSynced = 1 WHERE id = :id")
    abstract suspend fun markSynced(id: Long)

    @Query("DELETE FROM medications WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    //delete from id avisar ao kayky
    @Query("DELETE FROM medications WHERE userId = :userId AND isSynced = 1")
    abstract suspend fun deleteSyncedByUser(userId: Int)

    /**
     * Troca tudo o que já estava sincronizado pela lista nova da API,
     * mantendo os medicamentos criados offline que ainda não foram enviados.
     */
    @Transaction
    open suspend fun replaceSynced(userId: Int, entities: List<MedicationEntity>) {
        deleteSyncedByUser(userId)
        insertAll(entities)
    }
}
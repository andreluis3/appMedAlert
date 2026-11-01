package com.andre.medalert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.local.entity.preservingDetailsFrom
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MedicationDao {

    @Query("SELECT * FROM medications WHERE userId = :userId ORDER BY nome COLLATE NOCASE")
    abstract fun observeByUser(userId: Int): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id")
    abstract suspend fun getById(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications WHERE isSynced = 0")
    abstract suspend fun getUnsynced(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE userId = :userId AND isSynced = 1")
    abstract suspend fun getSyncedByUser(userId: Int): List<MedicationEntity>

    @Insert
    abstract suspend fun insert(entity: MedicationEntity): Long

    @Insert
    abstract suspend fun insertAll(entities: List<MedicationEntity>)

    /** Insere ou, se o id já existir, substitui a linha mantendo o MESMO id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entities: List<MedicationEntity>)

    @Query("UPDATE medications SET isSynced = 1 WHERE id = :id")
    abstract suspend fun markSynced(id: Long)

    /** Marca como sincronizado e guarda o ID do medicamento no backend. */
    @Query("UPDATE medications SET isSynced = 1, remoteId = :remoteId WHERE id = :id")
    abstract suspend fun markSyncedWithRemote(id: Long, remoteId: Int?)

    @Query("DELETE FROM medications WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM medications WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM medications WHERE userId = :userId AND isSynced = 1")
    abstract suspend fun deleteSyncedByUser(userId: Int)

    /**
     * Atualiza o que já estava sincronizado com a lista nova da API SEM perder detalhes:
     * - mesmo remoteId  -> mantém o id local e os detalhes que a API não devolve
     *   (veja preservingDetailsFrom);
     * - remoteId novo   -> insere;
     * - sumiu da API    -> remove (foi excluído em outro lugar);
     * - criados offline (isSynced = 0) não são tocados.
     */
    @Transaction
    open suspend fun replaceSynced(userId: Int, entities: List<MedicationEntity>) {
        val existing = getSyncedByUser(userId)
        val byRemoteId = existing
            .filter { it.remoteId != null }
            .associateBy { it.remoteId }

        val merged = entities.map { novo ->
            novo.preservingDetailsFrom(novo.remoteId?.let { byRemoteId[it] })
        }

        val keepIds = merged.map { it.id }.filter { it != 0L }.toSet()
        val toDelete = existing.map { it.id }.filter { it !in keepIds }
        if (toDelete.isNotEmpty()) deleteByIds(toDelete)

        upsertAll(merged)
    }
}
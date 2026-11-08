package com.andre.medalert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow

/** Estoque do medicamento como está no Room (base, mínimo e consumo local). */
data class StockSnapshot(
    val estoque: Int?,
    val estoqueMinimo: Int?,
    val estoqueConsumido: Int
)

/**
 * Resultado de registrar uma dose tomada.
 * [inserted] = false: a dose já estava registrada (nada mudou, estoque inclusive).
 * Os campos de estoque são o estoque ATUAL (base - consumo) antes e depois; null = sem controle.
 */
data class TakenInsertResult(
    val inserted: Boolean,
    val stockBefore: Int? = null,
    val stockAfter: Int? = null,
    val stockMinimum: Int? = null
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
     * Limpa o cache do paciente neste aparelho (logout do cuidador). Só remove o que já está no
     * servidor (isSynced = 1): administrações pendentes de envio NUNCA são apagadas.
     */
    @Query("DELETE FROM dose_history WHERE userId = :userId AND isSynced = 1")
    abstract suspend fun deleteSyncedByUser(userId: Int): Int

    /** Pendentes de envio. Doses de medicamento excluído ficam de fora (o remoto já não existe). */
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
    @Query("SELECT remoteId FROM medications WHERE id = :medicationLocalId")
    abstract suspend fun remoteIdOf(medicationLocalId: Long): Int?

    @Insert
    abstract suspend fun insertAll(entities: List<DoseHistoryEntity>)

    /** Retorna -1 se a dose já existe (índice único): é assim que evitamos duplicatas. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(entity: DoseHistoryEntity): Long

    @Query("UPDATE dose_history SET isSynced = 1 WHERE id = :id")
    abstract suspend fun markSynced(id: Long)

    /**
     * Marca como sincronizada a dose local (medicamento + horário previsto) que o servidor
     * já informa como tomada. Evita reenviar um POST cuja resposta se perdeu.
     */
    @Query(
        """
        UPDATE dose_history SET isSynced = 1
        WHERE medicationLocalId = :medicationLocalId AND previstoEm = :previstoEm AND isSynced = 0
        """
    )
    abstract suspend fun markSyncedByDose(medicationLocalId: Long, previstoEm: Long): Int

    @Query(
        "SELECT estoque, estoqueMinimo, estoqueConsumido FROM medications WHERE id = :medicationLocalId"
    )
    abstract suspend fun stockOf(medicationLocalId: Long): StockSnapshot?

    /** Soma [quantity] ao consumo local, sem passar da base (estoque nunca fica negativo). */
    @Query(
        """
        UPDATE medications
        SET estoqueConsumido = MIN(estoqueConsumido + :quantity, estoque)
        WHERE id = :medicationLocalId AND estoque IS NOT NULL
        """
    )
    abstract suspend fun consumeStock(medicationLocalId: Long, quantity: Int): Int

    /**
     * Registra a dose tomada E desconta o estoque, na MESMA transação.
     * O desconto só acontece se a linha foi realmente inserida: o índice único
     * (medicationLocalId, previstoEm) faz o insert devolver -1 em dose repetida, então a mesma
     * dose nunca desconta duas vezes (toque duplo, duas notificações, reentrega do receiver).
     */
    @Transaction
    open suspend fun insertTakenConsumingStock(
        entity: DoseHistoryEntity,
        quantity: Int
    ): TakenInsertResult {
        // Mesma dose já conhecida pelo ID REMOTO (ex.: veio do servidor, registrada por outro
        // aparelho): não duplica e não desconta estoque de novo.
        val remoteMedicationId = entity.medicamentoId
        val scheduledAt = entity.previstoEm
        if (remoteMedicationId != null && scheduledAt != null &&
            countByRemoteDose(remoteMedicationId, scheduledAt) > 0
        ) {
            return TakenInsertResult(inserted = false)
        }

        if (insertIgnore(entity) == -1L) return TakenInsertResult(inserted = false)

        val medicationId = entity.medicationLocalId
            ?: return TakenInsertResult(inserted = true)
        val before = stockOf(medicationId)
            ?: return TakenInsertResult(inserted = true)

        if (before.estoque == null) {
            return TakenInsertResult(inserted = true, stockMinimum = before.estoqueMinimo)
        }

        consumeStock(medicationId, quantity.coerceAtLeast(1))
        val after = stockOf(medicationId)

        return TakenInsertResult(
            inserted = true,
            stockBefore = (before.estoque - before.estoqueConsumido).coerceAtLeast(0),
            stockAfter = after?.estoque?.let { (it - after.estoqueConsumido).coerceAtLeast(0) },
            stockMinimum = before.estoqueMinimo
        )
    }
}
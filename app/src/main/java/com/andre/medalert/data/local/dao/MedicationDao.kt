package com.andre.medalert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.local.entity.preservingDetailsFrom
import kotlinx.coroutines.flow.Flow

/**
 * Toda escrita/exclusão que age sobre UMA linha por id tem uma versão "ForUser" que exige que a
 * linha pertença ao paciente informado. As versões sem dono continuam existindo por
 * compatibilidade, mas estão @Deprecated: novo código deve usar as "ForUser".
 */
@Dao
abstract class MedicationDao {

    @Query(
        """
        SELECT * FROM medications
        WHERE userId = :userId
        ORDER BY nome COLLATE NOCASE
        """
    )
    abstract fun observeByUser(
        userId: Int
    ): Flow<List<MedicationEntity>>

    @Query(
        "SELECT * FROM medications WHERE id = :id"
    )
    abstract suspend fun getById(
        id: Long
    ): MedicationEntity?

    /** Igual a [getById], mas devolve null se a linha for de OUTRO paciente. */
    @Query(
        "SELECT * FROM medications WHERE id = :id AND userId = :userId"
    )
    abstract suspend fun getByIdForUser(
        id: Long,
        userId: Int
    ): MedicationEntity?

    /** Pendências de TODOS os usuários. Mantido por compatibilidade; o sync usa [getUnsyncedByUser]. */
    @Deprecated("Mistura pacientes. Use getUnsyncedByUser(userId).")
    @Query(
        "SELECT * FROM medications WHERE isSynced = 0"
    )
    abstract suspend fun getUnsynced(): List<MedicationEntity>

    /** Pendências (criadas offline ou ainda não confirmadas) do paciente da sessão, na ordem de criação. */
    @Query(
        """
        SELECT * FROM medications
        WHERE userId = :userId AND isSynced = 0
        ORDER BY id
        """
    )
    abstract suspend fun getUnsyncedByUser(
        userId: Int
    ): List<MedicationEntity>

    /**
     * Procura um cadastro PENDENTE idêntico ao que o usuário acabou de enviar.
     * Evita criar uma segunda linha local quando o usuário repete o envio de um cadastro
     * que ficou pendente (offline ou recusado pelo servidor).
     */
    @Query(
        """
        SELECT * FROM medications
        WHERE userId = :userId
          AND isSynced = 0
          AND nome = :nome COLLATE NOCASE
          AND dosagemValor = :dosagemValor
          AND unidadeDosagem = :unidadeDosagem
          AND horario_dose = :horarioDose
          AND dataInicio = :dataInicio
          AND frequencia = :frequencia
        LIMIT 1
        """
    )
    abstract suspend fun findPendingEquivalent(
        userId: Int,
        nome: String,
        dosagemValor: Double,
        unidadeDosagem: String,
        horarioDose: String,
        dataInicio: String,
        frequencia: Int
    ): MedicationEntity?

    @Query(
        """
        SELECT * FROM medications
        WHERE userId = :userId AND isSynced = 1
        """
    )
    abstract suspend fun getSyncedByUser(
        userId: Int
    ): List<MedicationEntity>

    @Insert
    abstract suspend fun insert(
        entity: MedicationEntity
    ): Long

    @Insert
    abstract suspend fun insertAll(
        entities: List<MedicationEntity>
    )

    /**
     * Atualiza a linha inteira, mantendo o mesmo ID local.
     * Não confere o dono: prefira [updateForUser].
     */
    @Deprecated("Não confere o dono da linha. Use updateForUser(entity, userId).")
    @Update
    abstract suspend fun update(
        entity: MedicationEntity
    )

    /**
     * Atualiza a linha inteira SÓ se ela existe e pertence a [userId] (e a entidade também diz
     * ser de [userId]). Devolve false, sem gravar nada, caso contrário.
     */
    @Transaction
    open suspend fun updateForUser(
        entity: MedicationEntity,
        userId: Int
    ): Boolean {
        val current = getById(entity.id) ?: return false
        if (current.userId != userId || entity.userId != userId) return false
        @Suppress("DEPRECATION")
        update(entity)
        return true
    }

    /**
     * Insere ou substitui os registros existentes
     * mantendo o mesmo ID local.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(
        entities: List<MedicationEntity>
    )

    /**
     * Marca um medicamento como sincronizado.
     *
     * Mantido para compatibilidade com chamadas existentes.
     */
    @Deprecated("Não confere o dono nem grava o remoteId. Use confirmSynced.")
    @Query(
        """
        UPDATE medications
        SET isSynced = 1
        WHERE id = :id
        """
    )
    abstract suspend fun markSynced(
        id: Long
    )

    /**
     * Marca como sincronizado e guarda o ID do medicamento no backend.
     * Não confere o dono: prefira [markSyncedWithRemoteForUser] (ou [confirmSynced]).
     */
    @Deprecated("Não confere o dono da linha. Use markSyncedWithRemoteForUser.")
    @Query(
        """
        UPDATE medications
        SET isSynced = 1,
            remoteId = :remoteId
        WHERE id = :id
        """
    )
    abstract suspend fun markSyncedWithRemote(
        id: Long,
        remoteId: Int
    ): Int

    /**
     * Marca como sincronizado e guarda o ID remoto, SÓ se a linha pertence a [userId].
     * Retorna a quantidade de linhas atualizadas.
     */
    @Query(
        """
        UPDATE medications
        SET isSynced = 1,
            remoteId = :remoteId
        WHERE id = :id AND userId = :userId
        """
    )
    abstract suspend fun markSyncedWithRemoteForUser(
        id: Long,
        remoteId: Int,
        userId: Int
    ): Int

    /** Apaga qualquer OUTRA linha do usuário com o mesmo remoteId (cópia criada por um refresh). */
    @Query(
        """
        DELETE FROM medications
        WHERE userId = :userId AND remoteId = :remoteId AND id != :keepId
        """
    )
    abstract suspend fun deleteOthersWithRemoteId(
        userId: Int,
        remoteId: Int,
        keepId: Long
    )

    /**
     * Confirma o envio de um pendente: grava o remoteId na linha local (ID local preservado).
     *
     * Se um refresh (GET) rodou entre o POST e esta chamada, a lista da API já trouxe o
     * medicamento recém-criado como uma linha NOVA com o mesmo remoteId. Essa cópia é removida
     * na mesma transação, então nunca ficam duas linhas para o mesmo medicamento.
     * A linha local só é confirmada se pertencer a [userId].
     *
     * Retorna a quantidade de linhas atualizadas (0 = a linha local não existe mais, ou é de
     * outro paciente).
     */
    @Transaction
    open suspend fun confirmSynced(
        localId: Long,
        remoteId: Int,
        userId: Int
    ): Int {
        deleteOthersWithRemoteId(userId, remoteId, localId)
        return markSyncedWithRemoteForUser(localId, remoteId, userId)
    }

    @Deprecated("Não confere o dono da linha. Use deleteByIdForUser ou deleteKeepingHistoryForUser.")
    @Query(
        "DELETE FROM medications WHERE id = :id"
    )
    abstract suspend fun deleteById(
        id: Long
    )

    @Query(
        "DELETE FROM medications WHERE id = :id AND userId = :userId"
    )
    abstract suspend fun deleteByIdForUser(
        id: Long,
        userId: Int
    )

    /** Marca como "medicamento excluído" o histórico local das doses desse medicamento. */
    @Deprecated("Não confere o dono. Use markHistoryRemovedForUser.")
    @Query(
        "UPDATE dose_history SET medicamentoRemovido = 1 WHERE medicationLocalId IN (:localIds)"
    )
    abstract suspend fun markHistoryRemoved(
        localIds: List<Long>
    )

    /** Igual a [markHistoryRemoved], limitado às doses do paciente [userId]. */
    @Query(
        """
        UPDATE dose_history SET medicamentoRemovido = 1
        WHERE userId = :userId AND medicationLocalId IN (:localIds)
        """
    )
    abstract suspend fun markHistoryRemovedForUser(
        userId: Int,
        localIds: List<Long>
    )

    /**
     * Exclui o medicamento PRESERVANDO o histórico de doses: as linhas de dose_history ficam
     * (com nome e dosagem gravados) e só ganham a marca medicamentoRemovido. Tudo na mesma
     * transação: ou o medicamento some e o histórico é marcado, ou nada muda.
     * Não confere o dono: prefira [deleteKeepingHistoryForUser].
     */
    @Deprecated("Não confere o dono da linha. Use deleteKeepingHistoryForUser.")
    @Transaction
    open suspend fun deleteKeepingHistory(
        localId: Long
    ) {
        @Suppress("DEPRECATION")
        markHistoryRemoved(listOf(localId))
        @Suppress("DEPRECATION")
        deleteById(localId)
    }

    /**
     * Exclui o medicamento preservando o histórico, SÓ se ele pertence a [userId].
     * Devolve false, sem alterar nada, se a linha não existe ou é de outro paciente.
     */
    @Transaction
    open suspend fun deleteKeepingHistoryForUser(
        localId: Long,
        userId: Int
    ): Boolean {
        val current = getById(localId) ?: return false
        if (current.userId != userId) return false
        markHistoryRemovedForUser(userId, listOf(localId))
        deleteByIdForUser(localId, userId)
        return true
    }

    @Deprecated("Não confere o dono das linhas. Use deleteByIdsForUser.")
    @Query(
        "DELETE FROM medications WHERE id IN (:ids)"
    )
    abstract suspend fun deleteByIds(
        ids: List<Long>
    )

    @Query(
        "DELETE FROM medications WHERE userId = :userId AND id IN (:ids)"
    )
    abstract suspend fun deleteByIdsForUser(
        userId: Int,
        ids: List<Long>
    )

    /**
     * Remove os medicamentos sincronizados de [userId], MAS nunca os que uma dose ainda pendente
     * de envio referencia (a dose perderia o medicamento e o remoteId de que precisa para subir).
     * Mesma regra de [deleteSyncedByUserKeepingReferenced]; mantido com este nome por
     * compatibilidade, agora sem o risco de apagar o que uma dose pendente precisa.
     */
    @Deprecated("Use deleteSyncedByUserKeepingReferenced, que informa quantas linhas saíram.")
    @Query(
        """
        DELETE FROM medications
        WHERE userId = :userId AND isSynced = 1
          AND id NOT IN (
              SELECT medicationLocalId FROM dose_history
              WHERE isSynced = 0 AND medicationLocalId IS NOT NULL
          )
        """
    )
    abstract suspend fun deleteSyncedByUser(
        userId: Int
    )

    /**
     * Limpeza de cache de um paciente que deixou de ser o dono dos dados deste aparelho.
     * Remove só medicamentos JÁ sincronizados (o servidor tem cópia) e que NÃO são referenciados
     * por uma dose ainda pendente de envio: sem isso a dose pendente perderia o medicamento
     * (e o remoteId) de que precisa para subir. Pendentes (isSynced = 0) nunca são apagados.
     * Devolve quantas linhas foram removidas.
     */
    @Query(
        """
        DELETE FROM medications
        WHERE userId = :userId AND isSynced = 1
          AND id NOT IN (
              SELECT medicationLocalId FROM dose_history
              WHERE isSynced = 0 AND medicationLocalId IS NOT NULL
          )
        """
    )
    abstract suspend fun deleteSyncedByUserKeepingReferenced(
        userId: Int
    ): Int

    /**
     * Atualiza os medicamentos sincronizados com a lista recebida da API.
     *
     * Regras:
     * - Mesmo remoteId: mantém o ID local e preserva detalhes existentes.
     * - Novo remoteId: insere um novo registro.
     * - remoteId repetido dentro da MESMA resposta: só o primeiro entra (evita linhas duplicadas).
     * - Medicamento ausente na API: remove o registro sincronizado.
     * - Medicamentos criados offline (isSynced = 0): não são alterados.
     * - Toda exclusão e marcação de histórico fica limitada ao paciente [userId].
     */
    @Transaction
    open suspend fun replaceSynced(
        userId: Int,
        entities: List<MedicationEntity>
    ) {

        val existing = getSyncedByUser(userId)

        val byRemoteId = existing
            .filter { it.remoteId != null }
            .associateBy { it.remoteId }

        val seen = HashSet<Int>()
        val unique = entities.filter { it.remoteId == null || seen.add(it.remoteId) }

        val merged = unique.map { novo ->
            novo.preservingDetailsFrom(
                novo.remoteId?.let { remoteId ->
                    byRemoteId[remoteId]
                }
            )
        }

        val keepIds = merged
            .map { it.id }
            .filter { it != 0L }
            .toSet()

        val toDelete = existing
            .map { it.id }
            .filter { it !in keepIds }

        if (toDelete.isNotEmpty()) {
            // Sumiu da API (excluído em outro aparelho, por exemplo): o histórico local fica.
            markHistoryRemovedForUser(userId, toDelete)
            deleteByIdsForUser(userId, toDelete)
        }

        upsertAll(merged)
    }
}
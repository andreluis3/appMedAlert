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

    /** Pendências de TODOS os usuários. Mantido por compatibilidade; o sync usa [getUnsyncedByUser]. */
    @Query(
        "SELECT * FROM medications WHERE isSynced = 0"
    )
    abstract suspend fun getUnsynced(): List<MedicationEntity>

    /** Pendências (criadas offline ou ainda não confirmadas) do usuário da sessão, na ordem de criação. */
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
     * Usado depois que a API confirma uma edição.
     */
    @Update
    abstract suspend fun update(
        entity: MedicationEntity
    )

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
     *
     * O ID remoto é obrigatório para evitar registros
     * marcados como sincronizados sem identificação no backend.
     *
     * Retorna a quantidade de linhas atualizadas.
     */
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
     *
     * Retorna a quantidade de linhas atualizadas (0 = a linha local não existe mais).
     */
    @Transaction
    open suspend fun confirmSynced(
        localId: Long,
        remoteId: Int,
        userId: Int
    ): Int {
        deleteOthersWithRemoteId(userId, remoteId, localId)
        return markSyncedWithRemote(localId, remoteId)
    }

    @Query(
        "DELETE FROM medications WHERE id = :id"
    )
    abstract suspend fun deleteById(
        id: Long
    )

    @Query(
        "DELETE FROM medications WHERE id IN (:ids)"
    )
    abstract suspend fun deleteByIds(
        ids: List<Long>
    )

    @Query(
        """
        DELETE FROM medications
        WHERE userId = :userId AND isSynced = 1
        """
    )
    abstract suspend fun deleteSyncedByUser(
        userId: Int
    )

    /**
     * Atualiza os medicamentos sincronizados com a lista recebida da API.
     *
     * Regras:
     * - Mesmo remoteId: mantém o ID local e preserva detalhes existentes.
     * - Novo remoteId: insere um novo registro.
     * - Medicamento ausente na API: remove o registro sincronizado.
     * - Medicamentos criados offline (isSynced = 0): não são alterados.
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

        val merged = entities.map { novo ->
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
            deleteByIds(toDelete)
        }

        upsertAll(merged)
    }
}
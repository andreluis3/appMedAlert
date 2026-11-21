package com.andre.medalert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andre.medalert.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Toda consulta e escrita é limitada ao DESTINATÁRIO (recipientUserId): nenhuma operação mexe em
 * notificação de outro usuário, mesmo que o id da linha seja conhecido.
 */
@Dao
abstract class NotificationDao {

    /** Mais recentes primeiro. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE recipientUserId = :recipientUserId
        ORDER BY criadaEm DESC, id DESC
        """
    )
    abstract fun observeAll(recipientUserId: Int): Flow<List<NotificationEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM notifications
        WHERE recipientUserId = :recipientUserId AND lidaEm IS NULL
        """
    )
    abstract fun observeUnreadCount(recipientUserId: Int): Flow<Int>

    /** Retorna -1 se a notificação já existe (índice único): é assim que evitamos duplicatas. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(entity: NotificationEntity): Long

    @Query(
        """
        SELECT * FROM notifications
        WHERE id = :id AND recipientUserId = :recipientUserId
        """
    )
    abstract suspend fun getByIdForRecipient(id: Long, recipientUserId: Int): NotificationEntity?

    /**
     * Horário previsto da dose MAIS RECENTE com ação ("Já tomei"/"Lembrar depois") deste
     * medicamento. As ações só valem para ela: agir numa dose antiga não pode cancelar o
     * lembrete nem o adiamento de uma dose mais nova.
     */
    @Query(
        """
        SELECT MAX(previstoEm) FROM notifications
        WHERE recipientUserId = :recipientUserId
          AND medicationLocalId = :medicationLocalId
          AND tipo IN ('MEDICATION_REMINDER', 'MEDICATION_DELAYED', 'MEDICATION_MISSED')
        """
    )
    abstract suspend fun latestActionableScheduledAt(
        recipientUserId: Int,
        medicationLocalId: Long
    ): Long?

    /** Marca como lida (só se ainda não estava). Devolve quantas linhas mudaram. */
    @Query(
        """
        UPDATE notifications SET lidaEm = :lidaEm
        WHERE id = :id AND recipientUserId = :recipientUserId AND lidaEm IS NULL
        """
    )
    abstract suspend fun markRead(id: Long, recipientUserId: Int, lidaEm: Long): Int

    @Query(
        """
        UPDATE notifications SET lidaEm = :lidaEm
        WHERE recipientUserId = :recipientUserId AND lidaEm IS NULL
        """
    )
    abstract suspend fun markAllRead(recipientUserId: Int, lidaEm: Long): Int

    @Query("DELETE FROM notifications WHERE id = :id AND recipientUserId = :recipientUserId")
    abstract suspend fun deleteForRecipient(id: Long, recipientUserId: Int): Int

    /** Mantém só as [keep] notificações mais recentes do destinatário (evita crescer sem limite). */
    @Query(
        """
        DELETE FROM notifications
        WHERE recipientUserId = :recipientUserId
          AND id NOT IN (
              SELECT id FROM notifications
              WHERE recipientUserId = :recipientUserId
              ORDER BY criadaEm DESC, id DESC
              LIMIT :keep
          )
        """
    )
    abstract suspend fun trimTo(recipientUserId: Int, keep: Int): Int
}
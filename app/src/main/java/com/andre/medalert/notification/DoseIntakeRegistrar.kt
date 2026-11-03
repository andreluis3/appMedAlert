package com.andre.medalert.notification

import android.content.Context

/**
 * Ponto único para registrar uma dose como tomada a partir da notificação.
 *
 * DEPENDE DO BACKEND/HISTÓRICO: o POST /api/historys da README é flat em snake_case
 * (usuario_id, medicamento_id, previsto_para, status...), mas o ApiService ainda usa o DTO antigo.
 * Quando isso for definido: converter [scheduledAtMillis] em previsto_para, enviar
 * (e, se estiver offline, guardar em DoseHistoryEntity com isSynced = false).
 *
 * Hoje NÃO cria nenhum registro. Devolve false para indicar "não registrado".
 */
object DoseIntakeRegistrar {

    @Suppress("UNUSED_PARAMETER")
    suspend fun registerTaken(
        context: Context,
        medicationLocalId: Long,
        scheduledAtMillis: Long
    ): Boolean = false
}
package com.andre.medalert.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uma dose do histórico (GET /api/historys devolve medicamento + lista de doses;
 * aqui cada dose vira uma linha).
 *
 * status segue a API: "tomado" | "atrasado" | "não tomado" | "pendente".
 * medicamentoId = ID REMOTO do medicamento (null quando ainda não sincronizado ou vindo da API).
 *
 * Campos locais (registro feito pela notificação "Tomar agora"):
 * - medicationLocalId: ID do medicamento no Room (NUNCA é enviado à API como ID remoto).
 * - previstoEm: horário previsto da dose em epoch millis. É a identidade da dose:
 *   o índice único (medicationLocalId, previstoEm) impede registro duplicado.
 * - tomadoEm: horário real da tomada em epoch millis.
 * - dia: dia da dose (yyyy-MM-dd), usado para consultar só o dia atual.
 * Linhas vindas da API deixam medicationLocalId/previstoEm nulos (o SQLite trata NULL como
 * distinto no índice único, então elas não colidem).
 *
 * isSynced = false: dose registrada localmente que ainda não foi enviada à API.
 */
@Entity(
    tableName = "dose_history",
    indices = [
        Index("userId"),
        Index(value = ["medicationLocalId", "previstoEm"], unique = true)
    ]
)
data class DoseHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int,
    val medicamentoId: Int? = null,
    val medicamentoNome: String,
    val dosagem: String,
    val horario: String,
    val tomado: String? = null,
    val status: String,
    val isSynced: Boolean = true,
    val medicationLocalId: Long? = null,
    val previstoEm: Long? = null,
    val tomadoEm: Long? = null,
    val dia: String? = null
)
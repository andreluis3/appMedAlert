package com.andre.medalert.data.local.entity

import androidx.room.ColumnInfo
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
 *
 * Identidade da administração (preenchida em registros feitos neste aparelho):
 * - clientEventId: UUID gerado no aparelho quando a administração é registrada. Fica guardado para
 *   o backend poder usá-lo como chave de idempotência (hoje o POST /api/historys NÃO o recebe).
 * - origem: "paciente" ou "cuidador" (quem registrou). null = origem desconhecida (linhas antigas ou
 *   vindas do GET doseDia, que não informa o autor).
 * - registradoPorId: ID do usuário (paciente ou cuidador) que registrou, quando conhecido.
 *
 * medicamentoRemovido: o medicamento foi excluído depois da dose. O REGISTRO É PRESERVADO (nome e
 * dosagem ficam gravados aqui) e só ganha esta marca. Linhas marcadas NÃO devem ser enviadas à
 * API: o medicamento remoto já não existe (o backend apaga o histórico em cascata).
 */
@Entity(
    tableName = "dose_history",
    indices = [
        Index("userId"),
        Index(value = ["medicationLocalId", "previstoEm"], unique = true),
        // Identidade da dose ENTRE aparelhos: medicamento REMOTO + horário previsto.
        // Não é único de propósito: linhas antigas podem ter repetições e a migration não apaga nada.
        // A deduplicação é feita no código (DAO) e, de forma definitiva, pelo backend.
        Index(value = ["medicamentoId", "previstoEm"])
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
    val dia: String? = null,
    @ColumnInfo(defaultValue = "0")
    val medicamentoRemovido: Boolean = false,
    val clientEventId: String? = null,
    val origem: String? = null,
    val registradoPorId: Int? = null
)
package com.andre.medalert.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uma dose do histórico (GET /api/historys devolve medicamento + lista de doses;
 * aqui cada dose vira uma linha).
 *
 * status segue a API: "tomado" | "atrasado" | "não tomado" | "pendente".
 * medicamentoId fica null para o que veio da API (ela não devolve o id);
 * será usado quando registrarmos doses tomadas offline (POST /api/historys).
 */
@Entity(
    tableName = "dose_history",
    indices = [Index("userId")]
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
    val isSynced: Boolean = true
)
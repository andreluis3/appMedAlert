package com.andre.medalert.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Medicamento guardado no celular.
 *
 * - Vindo da API (GET /api/meds/:userId): só temos nome, dosagem (texto) e horários;
 *   os demais campos ficam null e isSynced = true.
 * - Criado no app (offline): guarda todos os campos do POST /api/meds e
 *   isSynced = false até a API confirmar.
 *
 * A API não devolve id do medicamento, por isso o id aqui é local (autogerado).
 */
@Entity(
    tableName = "medications",
    indices = [Index("userId")]
)
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int,
    val nome: String,
    val dosagem: String,              // texto para exibir, ex.: "500 mg"
    val horarios: String = "",        // horários separados por vírgula, ex.: "08:00,16:00"
    val dataInicio: String? = null,
    val dataFim: String? = null,
    val dosagemValor: Int? = null,
    val frequencia: Int? = null,
    val quantidade: Int? = null,
    val unidadeDosagem: String? = null,
    val unidadeQuantidade: String? = null,
    val isSynced: Boolean = false
)

/** Horários como lista (a coluna guarda tudo numa string separada por vírgula). */
fun MedicationEntity.horariosLista(): List<String> =
    if (horarios.isBlank()) emptyList() else horarios.split(",")
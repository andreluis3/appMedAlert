package com.andre.medalert.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Medicamento guardado no celular.
 *
 * - [id] é LOCAL (autogerado). Agora ele é ESTÁVEL: o refresh da API reaproveita o mesmo id
 *   para o mesmo [remoteId], então a tela pode guardar o id local sem ele "sumir" depois.
 * - [remoteId] é o ID do medicamento no backend. É ELE que vai em editar e excluir.
 *   null = ainda não enviado (criado offline).
 * - Vindo da API (GET /api/meds/:usuarioId): nome, dosagem (texto), horários e remoteId.
 *   Os demais campos (datas, frequência, quantidade, unidades) a API NÃO devolve: ficam como
 *   estavam no celular (veja [preservingDetailsFrom]) ou null se nunca foram conhecidos.
 *   null = desconhecido; nunca significa vazio nem zero.
 * - Criado no app: guarda os campos do POST /api/meds e isSynced = false até a API confirmar.
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
    val isSynced: Boolean = false,
    val remoteId: Int? = null
)

/** Horários como lista (a coluna guarda tudo numa string separada por vírgula). */
fun MedicationEntity.horariosLista(): List<String> =
    if (horarios.isBlank()) emptyList() else horarios.split(",")

private fun String.normalizado(): String = trim().lowercase().replace(Regex("\\s+"), " ")

/**
 * Usado no refresh: [this] é o registro NOVO (resumido) vindo da API e [previous] é o que já
 * estava no Room para o mesmo remoteId.
 *
 * - nome, dosagem (texto), horários e isSynced vêm sempre da API (ela é a fonte da verdade).
 * - O id local é mantido.
 * - Os detalhes que a API não devolve (datas, frequência, quantidade, unidade da quantidade)
 *   são preservados do registro anterior.
 * - dosagemValor/unidadeDosagem só são preservados se o texto da dosagem da API ainda bater com
 *   o que tínhamos; se divergir (alguém mudou em outro lugar), viram null (desconhecido)
 *   em vez de manter um valor desatualizado.
 *
 * Limite conhecido: se frequência, quantidade ou datas forem alteradas fora deste aparelho, a API
 * não avisa e o celular continua com o valor antigo até o usuário editar por aqui.
 */
fun MedicationEntity.preservingDetailsFrom(previous: MedicationEntity?): MedicationEntity {
    if (previous == null) return this
    val dosagemConfere = previous.dosagemValor != null &&
            previous.unidadeDosagem != null &&
            previous.dosagem.normalizado() == dosagem.normalizado()
    return copy(
        id = previous.id,
        dataInicio = previous.dataInicio,
        dataFim = previous.dataFim,
        frequencia = previous.frequencia,
        quantidade = previous.quantidade,
        unidadeQuantidade = previous.unidadeQuantidade,
        dosagemValor = if (dosagemConfere) previous.dosagemValor else null,
        unidadeDosagem = if (dosagemConfere) previous.unidadeDosagem else null
    )
}
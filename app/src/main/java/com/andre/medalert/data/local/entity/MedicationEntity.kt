package com.andre.medalert.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Medicamento guardado no celular.
 *
 * - id é LOCAL e autogerado.
 * - remoteId é o ID do medicamento no backend.
 * - userId é SEMPRE o ID do PACIENTE dono (nunca o do cuidador).
 * - dosagem é o texto exibido ("2.5 mg"); dosagemValor é o número (Double, preserva decimais).
 * - dataInicio/dataFim armazenam as datas do tratamento.
 * - horarioDose armazena o horário inicial da primeira dose, SEMPRE em "HH:mm".
 * - horarios armazena a lista de horários retornada/calculada pelo backend.
 * - acompanhadoDesde (epoch millis) é quando o aparelho passou a conhecer o medicamento; a adesão
 *   offline só conta doses a partir desse momento (sem cobrar doses de antes do uso do app).
 *
 * Não há controle de estoque: o backend não tem essa coluna.
 */
@Entity(
    tableName = "medications",
    indices = [Index("userId")]
)
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val userId: Int,
    val nome: String,
    val dosagem: String,

    val horarios: String = "",

    @ColumnInfo(name = "horario_dose")
    val horarioDose: String? = null,

    val dataInicio: String? = null,
    val dataFim: String? = null,

    val dosagemValor: Double? = null,
    val frequencia: Int? = null,
    val quantidade: Int? = null,

    val unidadeDosagem: String? = null,
    val unidadeQuantidade: String? = null,

    val isSynced: Boolean = false,
    val remoteId: Int? = null,

    val observacoes: String? = null,

    @ColumnInfo(defaultValue = "1")
    val ativo: Boolean = true,

    val acompanhadoDesde: Long? = null
)

/**
 * Converte a coluna de horários em uma lista.
 * Os horários são armazenados separados por vírgula.
 */
fun MedicationEntity.horariosLista(): List<String> =
    if (horarios.isBlank()) {
        emptyList()
    } else {
        horarios.split(",")
    }

private fun String.normalizado(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

/**
 * Mantém a hora local antiga quando a API devolve a mesma data (registros antigos guardavam a
 * hora em dataInicio) e mantém o valor local quando a API não mandou o campo (o GET do cuidador
 * não traz data_inicio/data_fim).
 */
private fun mantemHoraLocal(
    api: String?,
    local: String?
): String? = when {
    api == null -> local
    local != null && api.take(10) == local.take(10) -> local
    else -> api
}

/**
 * Mescla o registro recebido da API com o registro que já estava no Room.
 *
 * A dosagem agora vem derivada no mapper (dosagem_valor ou número do texto), então o valor da
 * API tem prioridade; o valor anterior só é reaproveitado quando a API não trouxe nada e o
 * texto da dosagem continua igual.
 * O horarioDose da API já chega normalizado ("HH:mm") ou null (aí vale o do aparelho).
 */
fun MedicationEntity.preservingDetailsFrom(
    previous: MedicationEntity?,
    nowMillis: Long = System.currentTimeMillis()
): MedicationEntity {

    // Medicamento novo neste aparelho: o acompanhamento da adesão começa agora.
    if (previous == null) return copy(acompanhadoDesde = acompanhadoDesde ?: nowMillis)

    val desde = previous.acompanhadoDesde ?: acompanhadoDesde ?: nowMillis

    val dosagemConfere =
        previous.dosagemValor != null &&
                previous.unidadeDosagem != null &&
                previous.dosagem.normalizado() == dosagem.normalizado()

    // Resposta da API com frequência preenchida.
    if (frequencia != null) {
        return copy(
            id = previous.id,
            acompanhadoDesde = desde,

            horarios = horarios.ifBlank { previous.horarios },
            horarioDose = horarioDose ?: previous.horarioDose,

            dataInicio = mantemHoraLocal(dataInicio, previous.dataInicio),
            dataFim = mantemHoraLocal(dataFim, previous.dataFim),

            quantidade = quantidade ?: previous.quantidade,
            unidadeQuantidade = unidadeQuantidade ?: previous.unidadeQuantidade,

            observacoes = observacoes ?: previous.observacoes,

            dosagemValor = dosagemValor
                ?: if (dosagemConfere) previous.dosagemValor else null,

            unidadeDosagem = unidadeDosagem
                ?: if (dosagemConfere) previous.unidadeDosagem else null
        )
    }

    // Resposta resumida da API.
    return copy(
        id = previous.id,
        acompanhadoDesde = desde,

        dataInicio = previous.dataInicio,
        dataFim = previous.dataFim,

        horarios = horarios.ifBlank { previous.horarios },
        horarioDose = horarioDose ?: previous.horarioDose,

        frequencia = previous.frequencia,
        quantidade = quantidade ?: previous.quantidade,
        unidadeQuantidade = unidadeQuantidade ?: previous.unidadeQuantidade,

        observacoes = observacoes ?: previous.observacoes,

        ativo = previous.ativo,

        dosagemValor = dosagemValor
            ?: if (dosagemConfere) previous.dosagemValor else null,

        unidadeDosagem = unidadeDosagem
            ?: if (dosagemConfere) previous.unidadeDosagem else null
    )
}
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
 * - dosagem é o texto exibido ("2.5 mg"); dosagemValor é o número (Double, preserva decimais).
 * - dataInicio/dataFim armazenam as datas do tratamento.
 * - horarioDose armazena o horário inicial da primeira dose, SEMPRE em "HH:mm".
 * - horarios armazena a lista de horários retornada/calculada pelo backend.
 * - estoque é a BASE conhecida (servidor ou última edição do usuário); estoqueConsumido são as
 *   unidades descontadas LOCALMENTE por doses tomadas desde essa base. O estoque exibido é
 *   [estoqueAtual]. Assim um refresh da API não "devolve" o estoque já consumido no aparelho.
 * - acompanhadoDesde (epoch millis) é quando o aparelho passou a conhecer o medicamento; a adesão
 *   offline só conta doses a partir desse momento (sem cobrar doses de antes do uso do app).
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

    val estoque: Int? = null,
    val estoqueMinimo: Int? = null,
    val observacoes: String? = null,

    @ColumnInfo(defaultValue = "1")
    val ativo: Boolean = true,

    @ColumnInfo(defaultValue = "0")
    val estoqueConsumido: Int = 0,

    val acompanhadoDesde: Long? = null
)

/** Estoque que o usuário realmente tem: base menos o consumo local (nunca negativo). */
val MedicationEntity.estoqueAtual: Int?
    get() = estoque?.let { (it - estoqueConsumido).coerceAtLeast(0) }

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
 * Mantém a hora local antiga quando a API devolve a mesma data.
 *
 * Compatibilidade com registros antigos que guardavam a hora em dataInicio.
 */
private fun mantemHoraLocal(
    api: String?,
    local: String?
): String? {
    return if (
        api != null &&
        local != null &&
        api.take(10) == local.take(10)
    ) {
        local
    } else {
        api
    }
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

    // Se o servidor mandou um estoque DIFERENTE do que o aparelho tinha como base, o servidor
    // mudou a base (edição em outro aparelho, por exemplo): o consumo local deixa de valer.
    // Se o estoque é o mesmo (ou não veio), o consumo local continua descontando.
    val consumoLocal =
        if (estoque != null && previous.estoque != null && estoque != previous.estoque) {
            0
        } else {
            previous.estoqueConsumido
        }
    val desde = previous.acompanhadoDesde ?: acompanhadoDesde ?: nowMillis

    val dosagemConfere =
        previous.dosagemValor != null &&
                previous.unidadeDosagem != null &&
                previous.dosagem.normalizado() == dosagem.normalizado()

    // Resposta da API com frequência preenchida.
    if (frequencia != null) {
        return copy(
            id = previous.id,
            estoqueConsumido = consumoLocal,
            acompanhadoDesde = desde,

            horarios = horarios.ifBlank { previous.horarios },
            horarioDose = horarioDose ?: previous.horarioDose,

            dataInicio = mantemHoraLocal(
                dataInicio,
                previous.dataInicio
            ),
            dataFim = mantemHoraLocal(
                dataFim,
                previous.dataFim
            ),

            quantidade = quantidade ?: previous.quantidade,
            unidadeQuantidade = unidadeQuantidade ?: previous.unidadeQuantidade,

            estoque = estoque ?: previous.estoque,
            estoqueMinimo = estoqueMinimo ?: previous.estoqueMinimo,
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
        estoqueConsumido = consumoLocal,
        acompanhadoDesde = desde,

        dataInicio = previous.dataInicio,
        dataFim = previous.dataFim,

        horarios = horarios.ifBlank { previous.horarios },
        horarioDose = horarioDose ?: previous.horarioDose,

        frequencia = previous.frequencia,
        quantidade = quantidade ?: previous.quantidade,
        unidadeQuantidade = unidadeQuantidade ?: previous.unidadeQuantidade,

        estoque = estoque ?: previous.estoque,
        estoqueMinimo = estoqueMinimo ?: previous.estoqueMinimo,
        observacoes = observacoes ?: previous.observacoes,

        ativo = previous.ativo,

        dosagemValor = dosagemValor
            ?: if (dosagemConfere) previous.dosagemValor else null,

        unidadeDosagem = unidadeDosagem
            ?: if (dosagemConfere) previous.unidadeDosagem else null
    )
}
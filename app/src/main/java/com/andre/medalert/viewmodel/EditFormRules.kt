package com.andre.medalert.viewmodel

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.mapper.toIsoUtc
import com.andre.medalert.ui.screens.meds.parseDecimalOrNull
import java.math.BigDecimal

// Regras PURAS do formulário de edição (sem Android): preencher, validar e montar o payload.
// Ficam aqui, separadas do EditMedicationViewModel, para serem testadas em JVM.

/** Valores do formulário de edição (texto como o usuário digita; datas em "yyyy-MM-dd"). */
data class EditForm(
    val nome: String = "",
    val dosagem: String = "",
    val unidadeDosagem: String = "",
    val quantidade: String = "",
    val unidadeQuantidade: String = "",
    val frequenciaHoras: Int? = null,
    val dataInicio: String? = null,
    val dataFim: String? = null,
    val observacoes: String = "",
    val ativo: Boolean = true
)

private val DATA_ISO = Regex("""\d{4}-\d{2}-\d{2}""")

// ---------------------------------------------------------------------------
// Entity -> formulário
// ---------------------------------------------------------------------------

/** Preenche só o que o aparelho realmente sabe; desconhecido vira vazio/null (nunca inventado). */
internal fun MedicationEntity.toEditForm(): EditForm {
    // Fallback para registros antigos: lê "500 mg" ou "0,5 mg" do texto de exibição.
    val partes = dosagem.trim().split(" ", limit = 2)
    val valorDoTexto = partes.firstOrNull()
        ?.takeIf { parseDecimalOrNull(it) != null }
        ?.replace('.', ',')
        .orEmpty()
    val unidadeDoTexto = partes.getOrNull(1).orEmpty()

    return EditForm(
        nome = nome,
        // Mesma representação do campo digitado: "50" (não "50.0") e "2,5" (vírgula).
        dosagem = dosagemValor?.let(::dosagemParaCampo) ?: valorDoTexto,
        unidadeDosagem = unidadeDosagem ?: unidadeDoTexto,
        quantidade = quantidade?.toString().orEmpty(),
        unidadeQuantidade = unidadeQuantidade.orEmpty(),
        frequenciaHoras = frequencia,
        dataInicio = dataInicio?.take(10)?.takeIf { DATA_ISO.matches(it) },
        dataFim = dataFim?.take(10)?.takeIf { DATA_ISO.matches(it) },
        observacoes = observacoes.orEmpty(),
        ativo = ativo
    )
}

/** 50.0 -> "50", 2.5 -> "2,5". Sem notação científica e sem ".0" sobrando. */
internal fun dosagemParaCampo(valor: Double): String =
    BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString().replace('.', ',')

/** Dosagem mudou? Compara os NÚMEROS ("2,5" == "2.5" == "2,50"), não o texto digitado. */
private fun dosagemMudou(o: EditForm, a: EditForm): Boolean =
    parseDecimalOrNull(a.dosagem) != parseDecimalOrNull(o.dosagem) ||
            a.unidadeDosagem != o.unidadeDosagem

/** "yyyy-MM-dd HH:mm:ss-03:00" -> "HH:mm". Só existe em medicamentos criados neste aparelho. */
internal fun MedicationEntity.primeiraDoseOrNull(): String? =
    dataInicio?.takeIf { it.length >= 16 && it[10] == ' ' }?.substring(11, 16)

// ---------------------------------------------------------------------------
// Validação e diferenças
// ---------------------------------------------------------------------------

/** Valida os campos alterados (e o nome, sempre). Devolve a mensagem do primeiro problema. */
internal fun validate(o: EditForm, a: EditForm): String? {
    if (a.nome.isBlank()) return "Informe o nome do medicamento."
    if (a.nome.trim().length > 200) return "O nome pode ter no máximo 200 caracteres."

    if (dosagemMudou(o, a)) {
        // Decimais valem (0,5 mg, 2,5 ml), como no cadastro.
        val d = parseDecimalOrNull(a.dosagem)
        if (d == null || d <= 0.0) return "Informe uma dosagem válida."
        if (a.unidadeDosagem.isBlank()) return "Escolha a unidade da dosagem."
    }

    if (a.quantidade != o.quantidade) {
        val q = a.quantidade.toIntOrNull()
        if (q == null || q !in 1..1000) return "Informe a quantidade por dose (de 1 a 1000)."
    }
    if (a.unidadeQuantidade != o.unidadeQuantidade && a.unidadeQuantidade.isBlank()) {
        return "Escolha a unidade da quantidade."
    }

    if (a.frequenciaHoras != o.frequenciaHoras) {
        val f = a.frequenciaHoras
        if (f == null || f !in 1..24) return "Escolha a frequência."
    }

    if (a.dataInicio != o.dataInicio && a.dataInicio == null) return "Escolha a data de início."
    if (a.dataFim == null && o.dataFim != null) {
        // O payload nunca leva valores nulos (o Gson os omitiria), então remover a data não chegaria à API.
        return "Ainda não é possível remover a data de fim. Escolha uma nova data."
    }
    val inicio = a.dataInicio
    val fim = a.dataFim
    if (inicio != null && fim != null && fim < inicio) {
        return "A data de fim não pode ser antes da data de início."
    }

    if (a.observacoes.trim().length > 1000) return "As observações podem ter no máximo 1000 caracteres."
    return null
}

/**
 * Monta o "data" do POST /api/meds/edit só com o que mudou, com os nomes da API.
 * Nunca inclui valores nulos (o Gson os omitiria). Dosagem e unidade da dosagem vão juntas.
 * Chame só depois de [validate] devolver null.
 */
internal fun buildChanges(o: EditForm, a: EditForm): Map<String, Any?> {
    val c = linkedMapOf<String, Any?>()

    if (a.nome.trim() != o.nome.trim()) c["nome"] = a.nome.trim()

    if (dosagemMudou(o, a)) {
        val d = parseDecimalOrNull(a.dosagem)!!
        // Inteiro vai como Int (formato já usado), fracionário como Double (2.5).
        c["dosagem"] = if (d % 1.0 == 0.0) d.toInt() else d
        c["unidade_dosagem"] = a.unidadeDosagem
    }
    if (a.quantidade != o.quantidade) c["quantidade_por_dose"] = a.quantidade.toInt()
    if (a.unidadeQuantidade != o.unidadeQuantidade) c["unidade_quantidade"] = a.unidadeQuantidade

    if (a.frequenciaHoras != o.frequenciaHoras) c["frequencia"] = a.frequenciaHoras!!

    // A API guarda só o dia: mandamos "yyyy-MM-ddT00:00:00.000Z" (mesmo helper do cadastro).
    if (a.dataInicio != o.dataInicio) c["data_inicio"] = a.dataInicio!!.toIsoUtc()
    if (a.dataFim != o.dataFim && a.dataFim != null) c["data_fim"] = a.dataFim.toIsoUtc()


    // Observação apagada vai como texto vazio (null seria omitido e a API não mudaria nada).
    if (a.observacoes.trim() != o.observacoes.trim()) c["observacoes"] = a.observacoes.trim()

    if (a.ativo != o.ativo) c["ativo"] = a.ativo

    return c
}

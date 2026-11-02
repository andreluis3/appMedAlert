package com.andre.medalert.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Corpo do POST /api/meds (JSON FLAT, snake_case, conforme a README e o Postman).
 * Os nomes das propriedades Kotlin foram mantidos para não quebrar a CadastroMedScreen;
 * só o nome no JSON mudou. Campos nulos são omitidos pelo Gson (a API aplica os padrões).
 *
 * - dosagem é Double: a API recebe Number e o paciente pode usar 0.5 ou 2.5 (o Gson envia 50.0 para 50).
 * - horario_dose vai como "HH:mm" (ver HorarioDose).
 * - estoque e observacoes fazem parte do contrato testado; a tela de cadastro ainda não os
 *   preenche, mas ficam aqui para não se perderem na sincronização.
 * Datas: o repositório converte para ISO UTC (ex.: 2026-10-28T00:00:00.000Z) antes de enviar.
 */
data class MedicamentoData(
    @SerializedName("nome") val nome: String,
    @SerializedName("data_inicio") val dataInicio: String,
    @SerializedName("data_fim") val dataFim: String? = null,
    @SerializedName("dosagem") val dosagem: Double,
    @SerializedName("frequencia") val frequencia: Int,
    @SerializedName("quantidade_por_dose") val quantidade: Int,
    @SerializedName("unidade_dosagem") val unidadeDosagem: String,
    @SerializedName("unidade_quantidade") val unidadeQuantidade: String,
    @SerializedName("usuario_id") val userId: Int,
    @SerializedName("horario_dose") val horarioDose: String? = null,
    @SerializedName("estoque") val estoque: Int? = null,
    @SerializedName("observacoes") val observacoes: String? = null
)

/**
 * Item do GET /api/meds/{usuarioId}. [id] é o ID DO MEDICAMENTO (necessário para editar/excluir).
 *
 * Os quatro primeiros campos são o contrato original (resumo). Os demais foram ACRESCENTADOS pelo
 * backend (GET completo) e são todos anuláveis: se o servidor for uma versão antiga e não mandar
 * algum deles, o app não quebra e o Room mantém o que já tinha (veja preservingDetailsFrom).
 *
 * [dosagem] chega como String ("50" ou "50 mg"). dosagem_valor é Double (ex.: 500 ou 2.5); quando
 * ele não vier, o mapper extrai o número do texto de [dosagem].
 * [horarioDose] pode chegar como ISO UTC ("1970-01-01T11:00:00.000Z"): converta com HorarioDose.
 */
data class MedicamentoResponse(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("nome") val nome: String,
    @SerializedName("dosagem") val dosagem: String,
    @SerializedName("doseDiaria") val doseDiaria: List<String>? = null,
    @SerializedName("dosagem_valor") val dosagemValor: Double? = null,
    @SerializedName("unidade_dosagem") val unidadeDosagem: String? = null,
    @SerializedName("quantidade_por_dose") val quantidade: Int? = null,
    @SerializedName("unidade_quantidade") val unidadeQuantidade: String? = null,
    @SerializedName("frequencia") val frequencia: Int? = null,
    @SerializedName("data_inicio") val dataInicio: String? = null,
    @SerializedName("data_fim") val dataFim: String? = null,
    @SerializedName("estoque") val estoque: Int? = null,
    @SerializedName("estoque_minimo") val estoqueMinimo: Int? = null,
    @SerializedName("observacoes") val observacoes: String? = null,
    @SerializedName("ativo") val ativo: Boolean? = null,
    @SerializedName("horario_dose") val horarioDose: String? = null
)

/** Resposta do POST /api/meds (201): só nos interessa o id do medicamento criado. */
data class MedicamentoCriadoDto(
    @SerializedName("id") val id: Int? = null
)

/**
 * POST /api/meds/edit -> {"id": <ID DO MEDICAMENTO>, "data": {campos em snake_case}}.
 * Ex.: EditMedicamentoRequest(1, mapOf("nome" to "Novo", "frequencia" to 6))
 *
 * ATENÇÃO: esta é a ÚNICA declaração de EditMedicamentoRequest do projeto. Se existir um arquivo
 * EditMedicamentoRequest.kt separado na pasta dto, apague-o (senão: "Redeclaration").
 * Valores nulos no mapa seriam omitidos pelo Gson; por isso a edição nunca envia null.
 */
data class EditMedicamentoRequest(
    @SerializedName("id") val id: Int,
    @SerializedName("data") val data: Map<String, Any?>
)
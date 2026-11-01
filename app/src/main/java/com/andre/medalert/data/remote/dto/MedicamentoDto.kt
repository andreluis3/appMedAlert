package com.andre.medalert.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Corpo do POST /api/meds (JSON FLAT, snake_case, conforme a README).
 * Os nomes das propriedades Kotlin foram mantidos para não quebrar a CadastroMedScreen;
 * só o nome no JSON mudou. Campos nulos são omitidos pelo Gson (a API aplica os padrões).
 * Datas: o repositório converte para ISO UTC (ex.: 2026-10-28T00:00:00.000Z) antes de enviar.
 */
data class MedicamentoData(
    @SerializedName("nome") val nome: String,
    @SerializedName("data_inicio") val dataInicio: String,
    @SerializedName("data_fim") val dataFim: String? = null,
    @SerializedName("dosagem") val dosagem: Int,
    @SerializedName("frequencia") val frequencia: Int,
    @SerializedName("quantidade_por_dose") val quantidade: Int,
    @SerializedName("unidade_dosagem") val unidadeDosagem: String,
    @SerializedName("unidade_quantidade") val unidadeQuantidade: String,
    @SerializedName("usuario_id") val userId: Int
)

/**
 * Item do GET /api/meds/{usuarioId}. [id] é o ID DO MEDICAMENTO (necessário para editar/excluir).
 * Nulos tratados: se a API não mandar id ou doseDiaria, o app não quebra.
 */
data class MedicamentoResponse(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("nome") val nome: String,
    @SerializedName("dosagem") val dosagem: String,
    @SerializedName("doseDiaria") val doseDiaria: List<String>? = null
)

/** Resposta do POST /api/meds (201): só nos interessa o id do medicamento criado. */
data class MedicamentoCriadoDto(
    @SerializedName("id") val id: Int? = null
)

/**
 * POST /api/meds/edit -> {"id": <ID DO MEDICAMENTO>, "data": {campos em snake_case}}.
 * Ex.: EditMedicamentoRequest(1, mapOf("nome" to "Novo", "frequencia" to 6))
 */
data class EditMedicamentoRequest(
    @SerializedName("id") val id: Int,
    @SerializedName("data") val data: Map<String, Any?>
)
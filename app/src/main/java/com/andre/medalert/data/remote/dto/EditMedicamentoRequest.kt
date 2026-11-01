package com.andre.medalert.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO para o endpoint POST /api/meds/edit
 * Representa a estrutura aninhada: { "id": X, "data": { ... } }
 */
data class EditMedicamentoRequest(
    @SerializedName("id")
    val id: Int,

    @SerializedName("data")
    val data: Map<String, Any?>
)
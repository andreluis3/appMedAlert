package com.andre.medalert.data.repository

import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.CreateMedicamentoRequest
import com.andre.medalert.data.remote.dto.MedicamentoResponse

class MedicationRepository(
    private val api: ApiService = RetrofitClient.instance
) {
    suspend fun createMedicamento(request: CreateMedicamentoRequest) {
        api.createMedicamento(request)
    }

    suspend fun getMedicamentos(userId: Int): List<MedicamentoResponse> {
        return api.getMedicamentos(userId)
    }
}
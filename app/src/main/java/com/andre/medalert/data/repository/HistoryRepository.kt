package com.andre.medalert.data.repository

import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.remote.dto.HistoryQueryRequest
import com.andre.medalert.data.remote.dto.HistoryRecordDto

class HistoryRepository(
    private val api: ApiService = RetrofitClient.instance
) {
    suspend fun getHistory(userId: Int): List<HistoryRecordDto> {
        return api.getHistory(HistoryQueryRequest(id = userId))
    }

    suspend fun getDashboard(userId: Int): DashboardResponse {
        return api.getDashboard(userId)
    }
}
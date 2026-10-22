package com.andre.medalert.data.remote

import com.andre.medalert.data.remote.dto.*
import okhttp3.ResponseBody
import retrofit2.http.*

interface ApiService {

    // O backend responde só o ID em texto puro (ex.: 11), não um objeto JSON.
    // Por isso o retorno é ResponseBody e o parse é feito no UserRepository.
    @POST("api/users")
    suspend fun createUser(@Body request: CreateUserRequest): ResponseBody

    // Sucesso: ID puro (ex.: 10). Falha: 200 com {"message": "..."}.
    @POST("api/users/login")
    suspend fun login(@Body request: LoginRequest): ResponseBody

    // A documentação não mostra o corpo de resposta deste endpoint,
    // por isso ele não retorna nada (igual createMedicamento/createHistory).
    @POST("api/users/cuid")
    suspend fun createCuidador(@Body request: CreateCuidadorRequest)

    // JSON FLAT (sem wrapper "data"). Confirmado no Postman: 201 Created.
    // A documentação da API diz que precisa do wrapper, mas o servidor real responde 500 com ele.
    @POST("api/meds")
    suspend fun createMedicamento(@Body request: MedicamentoData)

    @GET("api/meds/{userId}")
    suspend fun getMedicamentos(@Path("userId") userId: Int): List<MedicamentoResponse>

    @POST("api/historys")
    suspend fun createHistory(@Body request: CreateHistoryRequest)

    // O backend espera corpo JSON num GET - por isso @HTTP com hasBody, em vez de @GET normal.
    @HTTP(method = "GET", path = "api/historys", hasBody = true)
    suspend fun getHistory(@Body request: HistoryQueryRequest): List<HistoryRecordDto>

    @GET("api/historys/dashboard")
    suspend fun getDashboard(@Query("id") userId: Int): DashboardResponse
}
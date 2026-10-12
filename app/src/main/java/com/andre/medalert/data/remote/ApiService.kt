package com.andre.medalert.data.remote

import com.andre.medalert.data.remote.dto.*
import retrofit2.http.*

interface ApiService {


    @POST("api/users")
    suspend fun createUser(@Body request: CreateUserRequest): CreateUserResponse

    @POST("api/users/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    // A documentação não mostra o corpo de resposta deste endpoint,
    // por isso ele não retorna nada (igual createMedicamento/createHistory).
    @POST("api/users/cuid")
    suspend fun createCuidador(@Body request: CreateCuidadorRequest)



    @POST("api/meds")
    suspend fun createMedicamento(@Body request: CreateMedicamentoRequest)

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
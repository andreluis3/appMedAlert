package com.andre.medalert.data.remote

import com.andre.medalert.data.remote.dto.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Prefixo "api/" confirmado no Postman (ex.: https://dosy-xi.vercel.app/api/users/login).
 * Rotas de /historys e /notification seguem o mesmo roteador; confirme no primeiro teste de cada uma.
 * NUNCA coloque o CRON_SECRET aqui: GET /api/notification é chamado só pelo cron da Vercel.
 */
interface ApiService {

    // ---------- Usuários ----------

    // 201: ID numérico em texto puro (ex.: 4). 409: e-mail já existe. Body FLAT (sem wrapper "data").
    @POST("api/users")
    suspend fun createUser(@Body request: UserData): ResponseBody

    // 200 paciente: {"id":1,"tipo":"paciente"} | cuidador: {"id":3,"tipo":"cuidador","pacienteId":1}
    // 401: e-mail ou senha incorretos. O parse tolerante fica no UserRepository.
    @POST("api/users/login")
    suspend fun login(@Body request: LoginRequest): ResponseBody

    // 201: ID numérico do cuidador.
    @POST("api/users/cuidador")
    suspend fun createCuidador(@Body request: CreateCuidadorRequest): ResponseBody

    // Salva o token FCM do cuidador.
    @POST("api/users/token")
    suspend fun saveFcmToken(@Body request: SaveFcmTokenRequest): ResponseBody

    // Paciente convida cuidador (backend envia push).
    @POST("api/users/requestCuidador")
    suspend fun requestCuidador(@Body request: RequestCuidadorRequest): ResponseBody

    // Cuidador aceita paciente. O campo se chama "pacientId" na README (grafia mantida).
    @POST("api/users/accept")
    suspend fun acceptPaciente(@Body request: AcceptPacienteRequest): ResponseBody

    // ---------- Medicamentos ----------

    // {usuarioId} = ID do PACIENTE.
    @GET("api/meds/{usuarioId}")
    suspend fun getMedicamentos(@Path("usuarioId") usuarioId: Int): List<MedicamentoResponse>

    // JSON flat em snake_case. 201 devolve o objeto criado (usamos só o id).
    @POST("api/meds")
    suspend fun createMedicamento(@Body request: MedicamentoData): MedicamentoCriadoDto?

    // Formato obrigatório: {"id": <ID DO MEDICAMENTO>, "data": {campos a alterar}}
    @POST("api/meds/edit")
    suspend fun editMedicamento(@Body request: EditMedicamentoRequest): ResponseBody

    // {medicamentoId} = ID do MEDICAMENTO. Apaga também histórico e horários (cascade).
    @DELETE("api/meds/{medicamentoId}")
    suspend fun deleteMedicamento(@Path("medicamentoId") medicamentoId: Int): ResponseBody

    // ---------- Histórico ----------

    // Registra uma dose TOMADA. Body confirmado no Postman:
    // {"usuario_id": 2, "medicamento_id": 1, "tomado_em": "2026-11-06T14:45:00-03:00"}
    // Devolve Response<ResponseBody> porque o corpo da resposta de sucesso não está documentado:
    // o app só confere o código HTTP (2xx = confirmado) e nunca interpreta o corpo.
    @POST("api/historys")
    suspend fun createHistory(@Body request: DoseTakenRequest): Response<ResponseBody>

    // Doses de hoje do paciente. A resposta não traz medicamento_id nem id da dose.
    @GET("api/historys/doseDia/{usuarioId}")
    suspend fun getDosesDia(@Path("usuarioId") usuarioId: Int): List<HistoryRecordDto>

    @Deprecated("Rota inexistente na README. Use getDosesDia(). Apagar quando nada mais chamar.")
    @HTTP(method = "GET", path = "api/historys", hasBody = true)
    suspend fun getHistory(@Body request: HistoryQueryRequest): List<HistoryRecordDto>

    @GET("api/historys/dashboard/{usuarioId}")
    suspend fun getDashboard(@Path("usuarioId") userId: Int): DashboardResponse
}
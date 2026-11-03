package com.andre.medalert.data.repository

import android.util.Log
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.model.User
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.ChangePasswordRequest
import com.andre.medalert.data.remote.dto.ChangePasswordResponse
import com.andre.medalert.data.remote.dto.CreateCuidadorRequest
import com.andre.medalert.data.remote.dto.CreateUserRequest
import com.andre.medalert.data.remote.dto.CreateUserResponse
import com.andre.medalert.data.remote.dto.LoginRequest
import com.andre.medalert.data.remote.dto.LoginResponse
import com.andre.medalert.data.remote.dto.toDomain
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Login recusado: e-mail/senha errados. Chega como HTTP 401 na API atual
 * (versões antigas respondiam 200 com {"message": "..."}; os dois casos viram esta exceção).
 */
class InvalidCredentialsException(message: String? = null) : Exception(message)

class UserRepository(
    private val api: ApiService = RetrofitClient.instance,
    private val userPreferences: UserPreferencesRepository
) {

    private val gson = Gson()

    val isLoggedIn: Flow<Boolean> = userPreferences.isLoggedIn
    val userId: Flow<Int?> = userPreferences.userId
    val token: Flow<String?> = userPreferences.token
    val userType: Flow<String> = userPreferences.userType
    val pacienteId: Flow<Int?> = userPreferences.pacienteId

    /**
     * Cria a conta de PACIENTE (POST /api/users, body FLAT). A API devolve só o ID (ex.: 4).
     * NÃO salva sessão: depois do cadastro o app vai para a tela de Login.
     * 409 (e-mail já existe) e 500 saem como HttpException para o ViewModel traduzir.
     */
    suspend fun createUser(request: CreateUserRequest): CreateUserResponse {
        val raw = withContext(Dispatchers.IO) { api.createUser(request.data).use { it.string() } }
        Log.d("USER_REPO", "createUser corpo=$raw")

        val id = extractId(raw)
        if (id == null || id <= 0) {
            throw IllegalStateException("Cadastro sem userId válido. Resposta: $raw")
        }
        return CreateUserResponse(userId = id)
    }

    /**
     * Cria a conta de CUIDADOR (POST /api/users/cuidador). A API devolve o ID do cuidador.
     * NÃO salva sessão e NÃO cria vínculo: o vínculo com o paciente acontece depois,
     * pelo convite (/users/requestCuidador) e aceite (/users/accept).
     * Erros HTTP (ex.: e-mail duplicado) saem como HttpException para o ViewModel traduzir.
     */
    suspend fun createCuidador(request: CreateCuidadorRequest): CreateUserResponse {
        val raw = withContext(Dispatchers.IO) { api.createCuidador(request).use { it.string() } }
        Log.d("USER_REPO", "createCuidador corpo=$raw")

        val id = extractId(raw)
        if (id == null || id <= 0) {
            throw IllegalStateException("Cadastro de cuidador sem id válido. Resposta: $raw")
        }
        return CreateUserResponse(userId = id)
    }

    /**
     * Faz o login e, em caso de sucesso, salva id, tipo e pacienteId no DataStore.
     *
     * Aceita: {"id":1,"tipo":"paciente"}, {"id":3,"tipo":"cuidador","pacienteId":1}
     * e, por compatibilidade, um número puro (ex.: 5), tratado como paciente.
     * 401 / corpo sem id -> InvalidCredentialsException. Outros erros vão em Result.failure.
     */
    suspend fun login(email: String, senha: String): Result<User> {
        return try {
            val raw = withContext(Dispatchers.IO) {
                api.login(LoginRequest(email = email, senha = senha)).use { it.string() }
            }
            Log.d("USER_REPO", "login corpo=$raw")

            val response = parseLogin(raw)
            if (response == null) {
                Result.failure(InvalidCredentialsException(extractMessage(raw)))
            } else {
                val user = response.toDomain()
                userPreferences.saveSession(
                    userId = user.id,
                    token = user.token,
                    tipo = user.tipo,
                    pacienteId = user.pacienteId,
                    nome = user.nome.takeIf { it.isNotBlank() }
                )
                Result.success(user)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Log.e("USER_REPO", "Login HTTP ${e.code()}", e)
            if (e.code() == 401) {
                val corpo = try {
                    e.response()?.errorBody()?.string()
                } catch (_: Exception) {
                    null
                }
                Result.failure(InvalidCredentialsException(corpo?.let { extractMessage(it) }))
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e("USER_REPO", "Falha no login", e)
            Result.failure(e)
        }
    }

    suspend fun logout() {
        userPreferences.clear()
    }

    /**
     * TODO: Não existe endpoint de troca de senha na API. Por regra do projeto,
     * não inventamos endpoint. Quando o backend expuser, adicionar em ApiService.kt.
     */
    suspend fun changePassword(request: ChangePasswordRequest): ChangePasswordResponse {
        throw NotImplementedError("Endpoint de alterar senha ainda não implementado no backend.")
    }

    // ---------- helpers de parse ----------

    /** Devolve o LoginResponse se houver um id > 0; senão null. */
    private fun parseLogin(raw: String): LoginResponse? {
        val puro = raw.trim().trim('"').toIntOrNull()
        if (puro != null) {
            return if (puro > 0) LoginResponse(id = puro, tipo = "paciente") else null
        }
        val obj = asJsonObject(raw) ?: return null
        val parsed = try {
            gson.fromJson(obj, LoginResponse::class.java)
        } catch (e: Exception) {
            null
        } ?: return null
        // Versões antigas mandavam "userId" em vez de "id".
        val id = parsed.id ?: obj.get("userId")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
        return if (id != null && id > 0) parsed.copy(id = id) else null
    }

    /** Aceita "4", "\"4\"" ou {"id": 4} / {"userId": 4}. Devolve null se não achar ID. */
    private fun extractId(raw: String): Int? {
        raw.trim().trim('"').toIntOrNull()?.let { return it }
        val obj = asJsonObject(raw) ?: return null
        return listOf("id", "userId").firstNotNullOfOrNull { key ->
            obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
        }
    }

    /** Pega o "message" de {"message": "..."}, se existir. */
    private fun extractMessage(raw: String): String? {
        val obj = asJsonObject(raw) ?: return null
        return obj.get("message")?.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun asJsonObject(raw: String): JsonObject? {
        return try {
            JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        }
    }
}
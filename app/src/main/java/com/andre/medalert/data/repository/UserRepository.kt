package com.andre.medalert.data.repository

import android.util.Log
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.model.User
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.ChangePasswordRequest
import com.andre.medalert.data.remote.dto.ChangePasswordResponse
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

/**
 * Login recusado pelo backend. Ele responde HTTP 200 com {"message": "..."} quando
 * o e-mail não existe ou a senha está errada, então não chega como HttpException.
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

    /**
     * Cria a conta. O backend devolve só o ID (ex.: 11) no corpo.
     * NÃO salva sessão: depois do cadastro o app vai para a tela de Login.
     * Erros HTTP (ex.: 500 por e-mail/senha duplicados) saem como HttpException.
     */
    suspend fun createUser(request: CreateUserRequest): CreateUserResponse {
        val raw = withContext(Dispatchers.IO) { api.createUser(request).use { it.string() } }
        Log.d("USER_REPO", "createUser corpo=$raw")

        val id = extractUserId(raw)
        if (id == null || id <= 0) {
            throw IllegalStateException("Cadastro sem userId válido. Resposta: $raw")
        }
        return CreateUserResponse(userId = id)
    }

    /**
     * Faz o login e, em caso de sucesso, salva userId e token no DataStore.
     *
     * Sucesso = corpo com um ID > 0 (número puro ou objeto com userId).
     * Falha = HTTP 200 com {"message": "..."} -> InvalidCredentialsException.
     * Outros erros (HttpException, IOException...) vão em Result.failure para o ViewModel traduzir.
     */
    suspend fun login(email: String, senha: String): Result<User> {
        return try {
            val raw = withContext(Dispatchers.IO) {
                api.login(LoginRequest(email = email, senha = senha)).use { it.string() }
            }
            Log.d("USER_REPO", "login corpo=$raw")

            val id = extractUserId(raw)
            if (id == null || id <= 0) {
                Result.failure<User>(InvalidCredentialsException(extractMessage(raw)))
            } else {
                // Backend manda só o ID no login: monta o LoginResponse a partir dele.
                val response: LoginResponse = if (raw.trim().toIntOrNull() != null) {
                    gson.fromJson("""{"userId":$id,"nome":""}""", LoginResponse::class.java)
                } else {
                    gson.fromJson(raw, LoginResponse::class.java)
                }
                val user = response.toDomain()
                userPreferences.saveSession(userId = user.id, token = user.token)
                Result.success(user)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("USER_REPO", "Falha no login", e)
            Result.failure(e)
        }
    }

    suspend fun logout() {
        userPreferences.clear()
    }

    /**
     * TODO: Não existe endpoint de troca de senha em ApiService.kt ainda.
     * Por regra do projeto, não inventamos endpoint. Quando o backend expuser,
     * adicionar o método em ApiService.kt e trocar o corpo por:
     *
     *   return api.changePassword(request)
     */
    suspend fun changePassword(request: ChangePasswordRequest): ChangePasswordResponse {
        throw NotImplementedError("Endpoint de alterar senha ainda não implementado no backend.")
    }

    // ---------- helpers de parse ----------

    /** Aceita "11", "\"11\"" ou {"userId": 11} / {"id": 11}. Devolve null se não achar ID. */
    private fun extractUserId(raw: String): Int? {
        val text = raw.trim().trim('"')
        text.toIntOrNull()?.let { return it }
        val obj = asJsonObject(raw) ?: return null
        return listOf("userId", "id").firstNotNullOfOrNull { key ->
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
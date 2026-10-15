package com.andre.medalert.data.repository

import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.model.User
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.ChangePasswordRequest
import com.andre.medalert.data.remote.dto.ChangePasswordResponse
import com.andre.medalert.data.remote.dto.CreateUserRequest
import com.andre.medalert.data.remote.dto.CreateUserResponse
import com.andre.medalert.data.remote.dto.LoginRequest
import com.andre.medalert.data.remote.dto.toDomain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class UserRepository(
    private val api: ApiService = RetrofitClient.instance,
    private val userPreferences: UserPreferencesRepository
) {

    val isLoggedIn: Flow<Boolean> = userPreferences.isLoggedIn
    val userId: Flow<Int?> = userPreferences.userId
    val token: Flow<String?> = userPreferences.token

    suspend fun createUser(request: CreateUserRequest): CreateUserResponse {
        return api.createUser(request)
    }

    /**
     * Faz o login na API e, em caso de sucesso, salva userId e token no DataStore.
     * Em caso de falha devolve Result.failure com a exceção original
     * (HttpException, IOException etc.) para o ViewModel traduzir em mensagem.
     */
    suspend fun login(email: String, senha: String): Result<User> {
        return try {
            val response = api.login(LoginRequest(email = email, senha = senha))
            val user = response.toDomain()
            userPreferences.saveSession(userId = user.id, token = user.token)
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
}
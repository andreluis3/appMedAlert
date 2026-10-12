package com.andre.medalert.data.repository

import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.ChangePasswordRequest
import com.andre.medalert.data.remote.dto.ChangePasswordResponse
import com.andre.medalert.data.remote.dto.CreateUserRequest
import com.andre.medalert.data.remote.dto.CreateUserResponse
import com.andre.medalert.data.remote.dto.LoginRequest
import com.andre.medalert.data.remote.dto.LoginResponse

class UserRepository(
    private val api: ApiService = RetrofitClient.instance
) {
    suspend fun createUser(request: CreateUserRequest): CreateUserResponse {
        return api.createUser(request)
    }

    suspend fun login(request: LoginRequest): LoginResponse {
        return api.login(request)
    }

    /**
     * TODO: Não existe endpoint de troca de senha em ApiService.kt ainda
     * (verificado — só há POST /api/users, POST /api/users/login e
     * POST /api/users/cuid). Por regra do projeto, não inventamos endpoint.
     *
     * Quando o backend expuser algo como POST /api/users/change-password,
     * trocar o corpo desta função por:
     *
     *   return api.changePassword(request)
     *
     * e adicionar o método correspondente em ApiService.kt.
     */
    suspend fun changePassword(request: ChangePasswordRequest): ChangePasswordResponse {
        throw NotImplementedError("Endpoint de alterar senha ainda não implementado no backend.")
    }
}
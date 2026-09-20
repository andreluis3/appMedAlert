package com.andre.medalert.data.repository

import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
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
}
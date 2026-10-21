package com.andre.medalert.data.remote.dto

data class UserData(
    val email: String,
    val nome: String,
    val senha: String
)

data class CreateUserRequest(
    val data: UserData
)

data class CreateUserResponse(
    val userId: Int
)


//removido e a class changepassword daqui e foi para o ChangepasswordDTO


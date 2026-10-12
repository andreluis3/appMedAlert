package com.andre.medalert.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // TODO: trocar pela URL real do backend quando estiver no ar.
    // 10.0.2.2 é o endereço que o emulador Android usa pra acessar o
    // "localhost" da sua máquina - útil enquanto o back roda local.
    private const val BASE_URL = "http://10.0.2.2:3000/"

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
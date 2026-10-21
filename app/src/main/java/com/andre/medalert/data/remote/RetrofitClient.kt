package com.andre.medalert.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Backend hospedado na Vercel (https, então não precisa de usesCleartextTraffic).
    // Funciona igual no emulador e no celular físico, desde que haja internet.
    // Se um dia for testar com o backend rodando local: emulador = http://10.0.2.2:3000/
    // (aí volta a precisar do usesCleartextTraffic no Manifest).
    private const val BASE_URL = "https://dosy-xi.vercel.app/"

    // Mostra no Logcat (filtre por "OkHttp") a URL, o body enviado, o código
    // de resposta e o JSON recebido. Serve para você ENXERGAR o que o Retrofit faz.
    // ATENÇÃO: o level BODY também imprime a senha digitada no login/cadastro.
    // Use só em desenvolvimento e remova (ou troque por Level.NONE) antes de entregar.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
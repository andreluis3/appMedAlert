package com.andre.medalert.data.remote

import com.andre.medalert.BuildConfig
import com.andre.medalert.util.AppLog
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Backend hospedado no Railway.
    // A URL termina com "/" para manter o comportamento correto do Retrofit.
    private const val BASE_URL =
        "https://dosy-production.up.railway.app/"

    /**
     * Rotas públicas que não exigem token.
     */
    private fun isPublic(request: Request): Boolean {
        val path = request.url.encodedPath

        return path.endsWith("/api/users/login") ||
                path.endsWith("/api/users/cuidador") ||
                (path.endsWith("/api/users") && request.method == "POST")
    }

    /**
     * Envia Authorization: Bearer <token> nas rotas protegidas.
     */
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val publica = isPublic(original)

        val token = if (publica) null else SessionManager.currentToken()

        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        val response = chain.proceed(request)

        if (response.code == 401 && !publica) {
            // Informa qual token fez a requisição: 401 tardio de sessão antiga não derruba a nova.
            SessionManager.expire(token)
        }

        response
    }

    /**
     * Log de rede apenas em builds de debug.
     */
    private val loggingInterceptor =
        HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                AppLog.d("OkHttp", message)
            }
        }).apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
        }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(loggingInterceptor)
                }
            }
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

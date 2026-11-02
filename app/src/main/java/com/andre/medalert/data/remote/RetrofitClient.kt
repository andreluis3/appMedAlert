package com.andre.medalert.data.remote

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
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

    /** Rotas que não exigem token (login e criação de conta). Um 401 nelas NÃO derruba a sessão. */
    private fun isPublic(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.endsWith("/api/users/login") ||
                path.endsWith("/api/users/cuidador") ||
                (path.endsWith("/api/users") && request.method == "POST")
    }

    /**
     * Manda "Authorization: Bearer <token>" nas rotas protegidas.
     * Se o servidor responder 401 numa rota protegida (token ausente, inválido ou expirado),
     * limpa a sessão e avisa a UI para voltar ao login. A resposta segue para o Repository,
     * que mostra a mensagem amigável.
     */
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val publica = isPublic(original)

        val token = if (publica) null else SessionManager.currentToken()
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }

        val response = chain.proceed(request)
        if (response.code == 401 && !publica) {
            SessionManager.expire()
        }
        response
    }

    // Mostra no Logcat (filtre por "OkHttp") a URL, o body, o código e o JSON recebido.
    // O token (resposta do login) e a senha (corpo do login/cadastro) saem mascarados.
    // Antes de entregar o app, troque o level por NONE.
    private val SEGREDOS = Regex("(\"(?:token|senha)\"\\s*:\\s*\")[^\"]*(\")")

    private val loggingInterceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
        override fun log(message: String) {
            Log.d("OkHttp", SEGREDOS.replace(message, "\$1***\$2"))
        }
    }).apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
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

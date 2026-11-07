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

    // Log de rede SÓ em build de debug (BuildConfig.DEBUG). Em release o interceptor nem é
    // adicionado, então nada de URL, corpo, token ou medicamento chega ao Logcat.
    // Mesmo em debug, token, senha, e-mail, nome, telefone e observações saem mascarados
    // (LogRedactor), e o cabeçalho Authorization também.
    private val loggingInterceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
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
            .apply { if (BuildConfig.DEBUG) addInterceptor(loggingInterceptor) }
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

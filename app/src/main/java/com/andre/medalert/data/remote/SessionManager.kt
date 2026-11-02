package com.andre.medalert.data.remote

import android.content.Context
import com.andre.medalert.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Ponte entre o OkHttp (que não tem Context) e o DataStore da sessão.
 *
 * - [currentToken]: lido pelo interceptor a cada requisição (o DataStore guarda em cache,
 *   então é barato) para mandar "Authorization: Bearer <token>".
 * - [expire]: chamado quando uma rota protegida responde 401. Limpa a sessão e avisa a UI
 *   por [sessionExpired]; o MainActivity coleta e volta para o login.
 *
 * Inicializado uma vez em [com.andre.medalert.MedAlertApplication].
 */
object SessionManager {

    @Volatile
    private var prefs: UserPreferencesRepository? = null

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = UserPreferencesRepository(context.applicationContext)
        }
    }

    /** Roda em thread do OkHttp (nunca na main), então bloquear aqui é seguro. */
    internal fun currentToken(): String? {
        val p = prefs ?: return null
        return runBlocking { p.token.first() }
    }

    internal fun expire() {
        val p = prefs
        if (p != null) runBlocking { p.clear() }
        _sessionExpired.tryEmit(Unit)
    }
}

package com.andre.medalert.data.remote

import android.content.Context
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.session.SessionInfo
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
 * - [expire]: chamado quando uma rota protegida responde 401, informando QUAL token fez a
 *   requisição. A sessão só é encerrada se ainda for a dona desse token; um 401 tardio de uma
 *   sessão anterior (logout/login no meio da requisição) não derruba a sessão nova. A decisão é
 *   atômica (uma transação do DataStore), então 401s simultâneos geram UM único evento de
 *   expiração, avisado por [sessionExpired]; o MainActivity coleta e volta para o login.
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

    /**
     * Foto da sessão atual, ou null se o [SessionManager] ainda não foi iniciado.
     * Usada pelos repositórios para conferir, no meio de uma sincronização, que a sessão
     * continua sendo a do paciente cujos dados estão sendo gravados.
     */
    internal suspend fun currentSession(): SessionInfo? = prefs?.session?.first()

    /**
     * 401 recebido por uma requisição que usou [tokenUsed]. Só encerra a sessão se ela ainda for
     * a dona desse token. Roda em thread do OkHttp.
     */
    internal fun expire(tokenUsed: String?) {
        val p = prefs ?: return
        val cleared = runBlocking { p.clearSessionIfToken(tokenUsed) }
        if (cleared) _sessionExpired.tryEmit(Unit)
    }

    /** Compatibilidade: encerra a sessão atual sem comparar token. Prefira [expire] com o token. */
    internal fun expire() {
        val p = prefs
        if (p != null) runBlocking { p.clear() }
        _sessionExpired.tryEmit(Unit)
    }
}
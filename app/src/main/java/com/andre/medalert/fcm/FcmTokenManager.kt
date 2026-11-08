package com.andre.medalert.fcm

import android.content.Context
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.SaveFcmTokenRequest
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.util.AppLog
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Registro do token FCM na API, associado ao usuário autenticado.
 *
 * CONTRATO REAL (ApiService.saveFcmToken): POST /api/users/token com {cuidadorId, token}.
 * Só existe para CUIDADOR. Não há endpoint equivalente para paciente; por isso o paciente não
 * recebe push de "cuidador registrou dose" até o backend oferecer um (ver lista de pendências).
 *
 * O token nunca é guardado no aparelho: é pedido ao Firebase quando necessário. O que se guarda
 * é só o marcador "<cuidadorId>:<token>" já enviado, para não reenviar o mesmo token.
 * No logout o token do aparelho é invalidado ([deleteToken]) e o marcador some junto da sessão.
 */
object FcmTokenManager {

    private const val TAG = "FcmTokenManager"

    /**
     * Envia o token atual se a sessão for de cuidador e esse token ainda não foi registrado para
     * esse cuidador. Falha de rede volta como Result.failure(IOException) (o worker tenta de novo).
     */
    suspend fun registerIfNeeded(
        context: Context,
        api: ApiService = RetrofitClient.instance
    ): Result<Unit> {
        val prefs = UserPreferencesRepository(context.applicationContext)
        val session = prefs.session.first()
        AppLog.d(TAG, "DEBUG userId=${session.userId}")
        AppLog.d(TAG, "DEBUG tipo=${session.tipo}")
        AppLog.d(TAG, "DEBUG pacienteId=${session.pacienteId}")
        AppLog.d(TAG, "DEBUG isCaregiver=${AccessPolicy.isCaregiver(session.tipo)}")
        AppLog.d(TAG, "DEBUG iniciando fetchToken")
        val userId = session.userId?.takeIf { it > 0 } ?: return Result.success(Unit)

        if (!AccessPolicy.isCaregiver(session.tipo)) {
            AppLog.d(TAG, "Paciente: a API não tem endpoint de token FCM; nada a registrar")
            return Result.success(Unit)
        }

        return try {
            val token = fetchToken()
            AppLog.d(TAG, "DEBUG token obtido=${token.take(20)}...")
            val marker = "$userId:$token"
            if (prefs.fcmRegistered.first() == marker) return Result.success(Unit)

            api.saveFcmToken(SaveFcmTokenRequest(cuidadorId = userId, token = token)).close()
            AppLog.d(TAG, "DEBUG enviando token para API cuidadorId=$userId")
            AppLog.d(TAG, "DEBUG token registrado na API")
            prefs.saveFcmRegistered(marker)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Não foi possível registrar o token FCM", e)
            Result.failure(e)
        }
    }

    /** Invalida o token deste aparelho (logout): o Firebase gera outro no próximo login. */
    suspend fun deleteToken() {
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Não foi possível invalidar o token FCM", e)
        }
    }

    private suspend fun fetchToken(): String = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            // task.result lança exceção quando a tarefa falhou: só lê depois de conferir.
            val token = if (task.isSuccessful) task.result else null
            if (!token.isNullOrBlank()) {
                continuation.resume(token)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Token FCM indisponível")
                )
            }
        }
    }
}

package com.andre.medalert.data.repository

import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.remote.ApiService
import com.andre.medalert.data.remote.RetrofitClient
import com.andre.medalert.data.remote.dto.AcceptPacienteRequest
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** Resultado do aceite, já com mensagem amigável para a tela. */
sealed interface AcceptResult {
    object Success : AcceptResult
    data class Failure(val message: String) : AcceptResult
}

/**
 * Aceite do convite pelo CUIDADOR: POST /api/users/accept { cuidadorId, pacientId }.
 *
 * Regra principal: NADA muda no aparelho antes de a API confirmar (2xx). Só depois disso o
 * paciente_id é gravado no DataStore (sem tocar no resto da sessão) e o convite sai da lista.
 * Sem logout e sem recriar a sessão.
 */
class CaregiverLinkRepository(
    private val api: ApiService = RetrofitClient.instance,
    private val prefs: UserPreferencesRepository
) {

    suspend fun accept(pacienteId: Int): AcceptResult {
        val session = prefs.session.first()
        val cuidadorId = session.userId?.takeIf { it > 0 }
        if (cuidadorId == null || !AccessPolicy.isCaregiver(session.tipo)) {
            return AcceptResult.Failure("Entre com uma conta de cuidador para aceitar o convite.")
        }
        if (pacienteId <= 0) {
            return AcceptResult.Failure("Convite inválido.")
        }

        return try {
            // 1) API primeiro. Qualquer 2xx é sucesso ({"message":"Cuidador aceito com sucesso"});
            //    não-2xx vira HttpException.
            withContext(Dispatchers.IO) {
                api.acceptPaciente(
                    AcceptPacienteRequest(cuidadorId = cuidadorId, pacientId = pacienteId)
                ).use { }
            }
            AppLog.d(TAG, "Aceite confirmado pela API (paciente id=$pacienteId)")

            // 2) Só agora o estado local: paciente_id + remoção do convite, numa transação.
            if (prefs.updateLinkedPatientId(pacienteId)) {
                AcceptResult.Success
            } else {
                AcceptResult.Failure(
                    "O vínculo foi aceito, mas não foi possível atualizar a sessão. " +
                            "Saia e entre novamente."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            AppLog.e(TAG, "Aceite recusado pela API: HTTP ${e.code()}", e)
            AcceptResult.Failure(
                when (e.code()) {
                    400, 404, 409 -> "Não foi possível aceitar: o convite não é mais válido."
                    in 500..599 -> "O servidor não conseguiu concluir o vínculo. Tente de novo em instantes."
                    else -> "Não foi possível aceitar o convite agora. Tente novamente."
                }
            )
        } catch (e: IOException) {
            AppLog.e(TAG, "Aceite sem conexão", e)
            AcceptResult.Failure("Sem conexão com a internet. Verifique e tente novamente.")
        } catch (e: Exception) {
            AppLog.e(TAG, "Falha inesperada no aceite", e)
            AcceptResult.Failure("Não foi possível aceitar o convite agora. Tente novamente.")
        }
    }

    private companion object {
        const val TAG = "CaregiverLinkRepo"
    }
}
package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.mapper.HorarioDose
import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.remote.dto.validationProblem
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.data.schedule.LocalAdherence
import com.andre.medalert.data.schedule.toDashboardResponse
import com.andre.medalert.data.session.SessionIdentity
import com.andre.medalert.data.session.identity
import com.andre.medalert.state.AdherenceUiState
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * A sessão é OBSERVADA, não lida uma vez: login, logout e troca de vínculo recarregam a adesão
 * do paciente certo, e a requisição do paciente anterior é cancelada (collectLatest). Assim a
 * adesão de um paciente nunca aparece (nem é gravada no estado) depois que a sessão passou a
 * outro paciente.
 *
 * Logs (tag AdherenceVM): só perfil, id do paciente consultado, contadores e códigos HTTP.
 * Nunca token, e-mail ou nome.
 */
class AdherenceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(AppDatabase.getInstance(application).doseHistoryDao())
    private val prefs = UserPreferencesRepository(application)

    private val _uiState = MutableStateFlow<AdherenceUiState>(AdherenceUiState.Loading)
    val uiState: StateFlow<AdherenceUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        load()
    }

    /** Também serve de "tentar de novo": reinicia a observação da sessão e a carga. */
    fun load() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.value = AdherenceUiState.Loading

            prefs.session
                .map { it.identity() }
                .distinctUntilChanged()
                .collectLatest { identity -> loadFor(identity) }
        }
    }

    /** Carrega a adesão de UMA identidade; é cancelada inteira quando a identidade muda. */
    private suspend fun loadFor(identity: SessionIdentity) {
        val patientId = identity.patientId
        if (patientId == null) {
            AppLog.d(
                TAG,
                if (identity.awaitingLink) {
                    "cuidador sem vínculo: nenhuma consulta de adesão"
                } else {
                    "sem sessão válida: nenhuma consulta de adesão"
                }
            )
            _uiState.value =
                if (identity.awaitingLink) {
                    AdherenceUiState.AwaitingLink
                } else {
                    AdherenceUiState.Error("Sessão expirada. Entre novamente.")
                }
            return
        }

        val perfil = if (identity.isCaregiver) "cuidador" else "paciente"
        AppLog.d(TAG, "consultando dashboard: perfil=$perfil, usuarioId enviado=$patientId")

        // Nunca mostra a adesão do paciente anterior enquanto a nova carrega.
        _uiState.value = AdherenceUiState.Loading

        _uiState.value = try {
            val remote = repository.getDashboard(patientId)
            AppLog.d(
                TAG,
                "dashboard recebido: perfil=$perfil, id=$patientId, adesao='${remote.adesao}', " +
                        "dosesEsp=${remote.dosesEsp}, dosesTom=${remote.dosesTom}, " +
                        "dosesAtr=${remote.dosesAtr}, dosesNT=${remote.dosesNT}"
            )

            val problem = remote.validationProblem()
            when {
                // Resposta malformada: não vira "0%" nem é trocada pelo Room em silêncio.
                problem != null -> {
                    AppLog.w(TAG, "resposta do dashboard inválida ($problem): perfil=$perfil, id=$patientId")
                    AdherenceUiState.Error(INVALID_RESPONSE_MESSAGE)
                }

                // Servidor sem dados ainda: se o aparelho já tem como calcular, mostra o local.
                remote.dosesEsp == 0 ->
                    localOrNull(patientId)?.let { AdherenceUiState.Success(it, isLocal = true) }
                        ?: AdherenceUiState.Success(remote)

                else -> {
                    // Diagnóstico: servidor diz 0 tomadas. Há doses tomadas aqui ainda não enviadas?
                    if (!identity.isCaregiver && remote.dosesTom == 0) logPendingDoses(patientId)
                    AdherenceUiState.Success(remote.withLocalForgottenTimes())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detalhe = if (e is HttpException) "HTTP ${e.code()}" else e.javaClass.simpleName
            AppLog.w(TAG, "falha ao consultar o dashboard: perfil=$perfil, id=$patientId, $detalhe")

            // Sem internet ou erro do servidor: a adesão continua visível, calculada no Room.
            // Para cuidador isso devolve null (LocalAdherence não calcula no aparelho dele).
            localOrNull(patientId)?.let { AdherenceUiState.Success(it, isLocal = true) }
                ?: when (e) {
                    is HttpException ->
                        AdherenceUiState.Error("Não foi possível carregar os dados (erro ${e.code()}).")
                    is IOException -> AdherenceUiState.Error("Sem conexão com a internet.")
                    else -> AdherenceUiState.Error()
                }
        }
    }

    /** Adesão dos últimos 7 dias calculada com o Room; null se não há dose para avaliar (ou é cuidador). */
    private suspend fun localOrNull(patientId: Int): DashboardResponse? {
        return try {
            LocalAdherence.compute(getApplication<Application>(), patientId)
                ?.takeIf { it.expected > 0 }
                ?.toDashboardResponse()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Só diagnóstico (log): quantas doses deste paciente estão pendentes de envio no aparelho. */
    private suspend fun logPendingDoses(patientId: Int) {
        try {
            val pending = AppDatabase.getInstance(getApplication<Application>())
                .doseHistoryDao()
                .getUnsyncedByUser(patientId)
                .size
            AppLog.d(TAG, "servidor com 0 tomadas; doses pendentes de envio neste aparelho: $pending")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Diagnóstico não pode derrubar a tela.
        }
    }

    /**
     * O dashboard devolve os horários de "horariosEsq" em UTC (ex.: 03:53 para uma dose das 00:53).
     * A tela mostra o horário do paciente, igual ao da tela de Medicamentos. Só se aplica à
     * resposta do servidor: o cálculo local (Room) já vem no horário do paciente.
     */
    private fun DashboardResponse.withLocalForgottenTimes(): DashboardResponse =
        copy(
            horariosEsq = horariosEsq.map {
                it.copy(horario = HorarioDose.horaUtcParaLocal(it.horario) ?: it.horario)
            }
        )

    private companion object {
        const val TAG = "AdherenceVM"
        const val INVALID_RESPONSE_MESSAGE =
            "O servidor devolveu dados inesperados. Tente novamente mais tarde."
    }
}
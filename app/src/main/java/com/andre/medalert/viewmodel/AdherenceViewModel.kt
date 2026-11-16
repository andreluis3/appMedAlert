package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.data.schedule.LocalAdherence
import com.andre.medalert.data.schedule.toDashboardResponse
import com.andre.medalert.data.session.SessionIdentity
import com.andre.medalert.data.session.identity
import com.andre.medalert.state.AdherenceUiState
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
            _uiState.value =
                if (identity.awaitingLink) {
                    AdherenceUiState.AwaitingLink
                } else {
                    AdherenceUiState.Error("Sessão expirada. Entre novamente.")
                }
            return
        }

        // Nunca mostra a adesão do paciente anterior enquanto a nova carrega.
        _uiState.value = AdherenceUiState.Loading

        _uiState.value = try {
            val remote = repository.getDashboard(patientId)
            // Servidor sem dados ainda: se o aparelho já tem como calcular, mostra o local.
            if (remote.dosesEsp == 0) {
                localOrNull(patientId)?.let { AdherenceUiState.Success(it, isLocal = true) }
                    ?: AdherenceUiState.Success(remote)
            } else {
                AdherenceUiState.Success(remote)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sem internet ou erro do servidor: a adesão continua visível, calculada no Room.
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
}
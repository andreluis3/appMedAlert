package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.schedule.LocalAdherence
import com.andre.medalert.data.schedule.toDashboardResponse
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.state.AdherenceUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class AdherenceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(AppDatabase.getInstance(application).doseHistoryDao())

    private val _uiState = MutableStateFlow<AdherenceUiState>(AdherenceUiState.Loading)
    val uiState: StateFlow<AdherenceUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = AdherenceUiState.Loading

            val app = getApplication<Application>()
            val session = UserPreferencesRepository(app).session.first()
            val patientId = AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)

            if (patientId == null) {
                _uiState.value =
                    if (AccessPolicy.isAwaitingLink(session.tipo, session.userId, session.pacienteId)) {
                        AdherenceUiState.AwaitingLink
                    } else {
                        AdherenceUiState.Error("Sessão expirada. Entre novamente.")
                    }
                return@launch
            }

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
    }

    /** Adesão dos últimos 7 dias calculada com o Room; null se não há dose para avaliar (ou é cuidador). */
    private suspend fun localOrNull(patientId: Int): com.andre.medalert.data.remote.dto.DashboardResponse? {
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

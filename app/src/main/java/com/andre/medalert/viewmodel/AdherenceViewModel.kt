package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.schedule.SessionUser
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

            val patientId = SessionUser.patientId(getApplication())
            if (patientId == null) {
                _uiState.value = AdherenceUiState.Error("Sessão expirada. Entre novamente.")
                return@launch
            }

            _uiState.value = try {
                // Se dosesEsp == 0, a própria tela mostra o estado "Ainda não há dados de adesão".
                AdherenceUiState.Success(repository.getDashboard(patientId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                AdherenceUiState.Error("Não foi possível carregar os dados (erro ${e.code()}).")
            } catch (e: IOException) {
                AdherenceUiState.Error("Sem conexão com a internet.")
            } catch (e: Exception) {
                AdherenceUiState.Error()
            }
        }
    }
}
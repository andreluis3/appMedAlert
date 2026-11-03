package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.data.mapper.toHistoryUiModel
import com.andre.medalert.data.model.HistoryUiModel
import com.andre.medalert.data.schedule.SessionUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface HistoryUiState {
    object Loading : HistoryUiState
    data class Error(val message: String = "Não foi possível carregar o histórico.") : HistoryUiState
    data class Content(val records: List<HistoryUiModel>) : HistoryUiState
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(AppDatabase.getInstance(application).doseHistoryDao())

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading

            val patientId = SessionUser.patientId(getApplication())
            if (patientId == null) {
                _uiState.value = HistoryUiState.Error("Sessão expirada. Entre novamente.")
                return@launch
            }

            val refresh = repository.refresh(patientId)
            val saved = repository.observeHistory(patientId).first()

            _uiState.value = if (saved.isEmpty() && refresh.isFailure) {
                HistoryUiState.Error()
            } else {
                HistoryUiState.Content(saved.map { it.toHistoryUiModel() })
            }
        }
    }
}
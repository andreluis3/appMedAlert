package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.remote.dto.MedicamentoData
import com.andre.medalert.data.repository.MedicationRepository
import com.andre.medalert.data.repository.SaveOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MedUiState {
    object Idle : MedUiState
    object Loading : MedUiState
    data class Success(val savedOffline: Boolean) : MedUiState
    data class Error(val message: String) : MedUiState
}

class MedViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MedUiState>(MedUiState.Idle)
    val uiState: StateFlow<MedUiState> = _uiState.asStateFlow()

    /** O userId do [data] é ignorado: o repositório usa o do DataStore. */
    fun saveMedication(data: MedicamentoData) {
        if (_uiState.value is MedUiState.Loading) return
        _uiState.value = MedUiState.Loading

        viewModelScope.launch {
            repository.saveMedicationForCurrentUser(data).fold(
                onSuccess = { outcome ->
                    _uiState.value = MedUiState.Success(savedOffline = outcome == SaveOutcome.SAVED_OFFLINE)
                },
                onFailure = { e ->
                    _uiState.value = MedUiState.Error(
                        e.message ?: "Não foi possível salvar o medicamento."
                    )
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = MedUiState.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                MedViewModel(
                    MedicationRepository(
                        dao = AppDatabase.getInstance(app).medicationDao(),
                        userPrefs = UserPreferencesRepository(app)
                    )
                )
            }
        }
    }
}
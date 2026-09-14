package com.andre.medalert.viewmodel

import androidx.lifecycle.ViewModel
import com.andre.medalert.state.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun toggleMedicationReminders() {
        _uiState.update { it.copy(medicationReminders = !it.medicationReminders) }
    }

    fun toggleAlertSound() {
        _uiState.update { it.copy(alertSound = !it.alertSound) }
    }

    fun toggleLargeText() {
        _uiState.update { it.copy(largeText = !it.largeText) }
    }

    fun toggleDarkTheme() {
        _uiState.update { it.copy(darkTheme = !it.darkTheme) }
    }
}
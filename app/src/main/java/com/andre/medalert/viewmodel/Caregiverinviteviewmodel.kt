package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.state.CaregiverInviteUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Lê o convite pendente do paciente indicado na rota ("caregiverInvite/{pacienteId}").
 * ETAPA 1: somente leitura. Aceitar/recusar entram na etapa seguinte.
 */
class CaregiverInviteViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    /** ID do PACIENTE (nunca o do cuidador). */
    private val pacienteId: Int = savedStateHandle.get<Int>("pacienteId") ?: 0

    private val prefs = UserPreferencesRepository(application)

    val uiState: StateFlow<CaregiverInviteUiState> = prefs.pendingInvites
        .map { invites ->
            val invite = invites.firstOrNull { it.pacienteId == pacienteId }
            if (invite != null) CaregiverInviteUiState.Found(invite) else CaregiverInviteUiState.NotFound
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CaregiverInviteUiState.Loading
        )
}
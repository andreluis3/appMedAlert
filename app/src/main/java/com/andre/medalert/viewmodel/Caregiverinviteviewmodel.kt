package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.repository.AcceptResult
import com.andre.medalert.data.repository.CaregiverLinkRepository
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.state.CaregiverInviteAction
import com.andre.medalert.state.CaregiverInviteUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Convite pendente do paciente indicado na rota ("caregiverInvite/{pacienteId}"):
 * lê o convite guardado, aceita (API) ou recusa (só local).
 */
class CaregiverInviteViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    /** ID do PACIENTE (nunca o do cuidador). */
    private val pacienteId: Int = savedStateHandle.get<Int>("pacienteId") ?: 0

    private val prefs = UserPreferencesRepository(application)
    private val repository = CaregiverLinkRepository(prefs = prefs)

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

    private val _action = MutableStateFlow<CaregiverInviteAction>(CaregiverInviteAction.Idle)
    val action: StateFlow<CaregiverInviteAction> = _action.asStateFlow()

    /** Paciente que este cuidador já acompanha (null se ainda não tem vínculo). */
    val linkedPatientId: StateFlow<Int?> = prefs.session
        .map { if (AccessPolicy.canViewLinkedPatient(it.tipo, it.pacienteId)) it.pacienteId else null }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** Aceita o convite. Ignora toques repetidos enquanto uma chamada está em andamento. */
    fun accept() {
        val current = _action.value
        if (current is CaregiverInviteAction.Accepting || current is CaregiverInviteAction.Accepted) return
        _action.value = CaregiverInviteAction.Accepting

        viewModelScope.launch {
            _action.value = when (val result = repository.accept(pacienteId)) {
                is AcceptResult.Success -> CaregiverInviteAction.Accepted(pacienteId)
                is AcceptResult.Failure -> CaregiverInviteAction.Failed(result.message)
            }
        }
    }

    /**
     * Recusa: a API NÃO tem endpoint de recusa, então só remove o convite deste aparelho
     * (o paciente não é avisado). [onDone] roda depois da remoção.
     */
    fun decline(onDone: () -> Unit) {
        val current = _action.value
        if (current is CaregiverInviteAction.Accepting || current is CaregiverInviteAction.Accepted) return

        viewModelScope.launch {
            prefs.removePendingInvite(pacienteId)
            onDone()
        }
    }
}
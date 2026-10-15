package com.andre.medalert.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.model.CaregiverDecision
import com.andre.medalert.data.model.CaregiverRequestStatus
import com.andre.medalert.data.repository.CaregiverRequestRepository
import com.andre.medalert.data.repository.MockCaregiverRequestRepository
import com.andre.medalert.state.CaregiverRequestUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * O id da solicitação chega pela rota de navegação ("caregiverRequest/{requestId}")
 * através do SavedStateHandle, então a tela não precisa passar parâmetros.
 */
class CaregiverRequestViewModel @JvmOverloads constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CaregiverRequestRepository = MockCaregiverRequestRepository
) : ViewModel() {

    private val requestId: String = savedStateHandle.get<String>("requestId").orEmpty()

    private val _uiState = MutableStateFlow<CaregiverRequestUiState>(CaregiverRequestUiState.Loading)
    val uiState: StateFlow<CaregiverRequestUiState> = _uiState.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        viewModelScope.launch {
            _uiState.value = CaregiverRequestUiState.Loading
            runCatching { repository.obter(requestId) }
                .onSuccess { _uiState.value = CaregiverRequestUiState.Content(request = it) }
                .onFailure { _uiState.value = CaregiverRequestUiState.Error() }
        }
    }

    fun responder(decisao: CaregiverDecision) {
        val atual = _uiState.value as? CaregiverRequestUiState.Content ?: return

        // Evita toque repetido e resposta em solicitação já respondida.
        if (atual.submitting != null) return
        if (atual.request.status != CaregiverRequestStatus.PENDENTE) return

        _uiState.value = atual.copy(submitting = decisao, actionError = null)

        viewModelScope.launch {
            runCatching { repository.responder(atual.request.id, decisao) }
                .onSuccess {
                    _uiState.value = CaregiverRequestUiState.Content(
                        request = it,
                        respondedNow = true
                    )
                }
                .onFailure {
                    _uiState.value = atual.copy(
                        submitting = null,
                        actionError = "Não foi possível enviar sua resposta. Tente novamente."
                    )
                }
        }
    }
}
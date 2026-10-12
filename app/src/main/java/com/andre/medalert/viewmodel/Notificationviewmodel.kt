package com.andre.medalert.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.model.NotificationUiModel
import com.andre.medalert.data.repository.MockNotificationRepository
import com.andre.medalert.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NotificationUiState {
    object Loading : NotificationUiState
    data class Error(val message: String = "Não foi possível carregar notificações.") : NotificationUiState
    data class Success(val notifications: List<NotificationUiModel>) : NotificationUiState
}

class NotificationViewModel @JvmOverloads constructor(
    private val repository: NotificationRepository = MockNotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationUiState>(NotificationUiState.Loading)
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        viewModelScope.launch {
            _uiState.value = NotificationUiState.Loading
            runCatching { repository.listar() }
                .onSuccess { _uiState.value = NotificationUiState.Success(it) }
                .onFailure { _uiState.value = NotificationUiState.Error() }
        }
    }

    fun marcarComoLida(id: Int) {
        viewModelScope.launch {
            repository.marcarComoLida(id)
            carregar()
        }
    }

    /** A interação mais importante da tela. */
    fun marcarComoTomado(id: Int) {
        viewModelScope.launch {
            repository.marcarMedicamentoComoTomado(id)
            carregar()
        }
    }

    fun lembrarMaisTarde(id: Int) {
        viewModelScope.launch {
            repository.lembrarMaisTarde(id)
            carregar()
        }
    }
}
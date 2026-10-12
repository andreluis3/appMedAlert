package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.repository.HistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(
        dao = AppDatabase.getInstance(application).doseHistoryDao()
    )

    // Enquanto não há login/DataStore (Etapa 3), quem chama informa o userId.
    private val userId = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<DoseHistoryEntity>> = userId
        .flatMapLatest { id -> if (id == null) emptyFlow() else repository.observeHistory(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setUser(id: Int) {
        if (userId.value == id) return
        userId.value = id
        refresh()
    }

    fun refresh() {
        val id = userId.value ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refresh(id).onFailure {
                _error.value = "Não foi possível atualizar. Mostrando dados salvos no celular."
            }
            _isRefreshing.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
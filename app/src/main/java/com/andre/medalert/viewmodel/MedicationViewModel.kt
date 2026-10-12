package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.remote.dto.MedicamentoData
import com.andre.medalert.data.repository.MedicationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MedicationRepository(
        dao = AppDatabase.getInstance(application).medicationDao()
    )

    // Enquanto não há login/DataStore (Etapa 3), quem chama informa o userId.
    private val userId = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val medications: StateFlow<List<MedicationEntity>> = userId
        .flatMapLatest { id -> if (id == null) emptyFlow() else repository.observeMedications(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Define o usuário e atualiza a lista a partir da API (sem apagar o que já está no Room). */
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
                // Offline: continua mostrando o que está no Room.
                _error.value = "Não foi possível atualizar. Mostrando dados salvos no celular."
            }
            _isRefreshing.value = false
        }
    }

    fun saveMedication(data: MedicamentoData) {
        viewModelScope.launch {
            repository.saveMedication(data).onFailure {
                _error.value = "Medicamento salvo no celular. Será enviado quando houver internet."
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.repository.MedicationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MedicationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MedicationRepository(
        dao = AppDatabase.getInstance(application).medicationDao(),
        userPrefs = UserPreferencesRepository(application)
    )

    // Quem chama informa o userId (vem do DataStore na MainActivity).
    private val userId = MutableStateFlow<Int?>(null)

    // A tela observa SEMPRE o Room: quando o refresh grava na tabela, a lista atualiza sozinha.
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
        if (_isRefreshing.value) return // evita dois refresh ao mesmo tempo
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refresh(id)
                .onSuccess {
                    // Um refresh bem-sucedido apaga o aviso de erro anterior.
                    _error.value = null
                }
                .onFailure { e ->
                    // Offline ou erro do servidor: continua mostrando o que está no Room.
                    // (Um 401 também derruba a sessão no interceptor e leva ao login.)
                    _error.value = when {
                        e is HttpException && e.code() == 401 -> "Sessão expirada. Entre novamente."
                        else -> "Não foi possível atualizar. Mostrando dados salvos no celular."
                    }
                }
            _isRefreshing.value = false
        }
    }

    /**
     * Exclui pelo ID LOCAL. A API confirma primeiro; só então o Room remove e a lista atualiza
     * sozinha. Em caso de erro, o medicamento continua na lista e a mensagem aparece em [error].
     */
    fun deleteMedication(localId: Long) {
        viewModelScope.launch {
            repository.deleteMedication(localId)
                .onSuccess { _error.value = null }
                .onFailure { _error.value = it.message ?: "Não foi possível excluir o medicamento." }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

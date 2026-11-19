package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.repository.MedicationRepository
import com.andre.medalert.data.repository.StaleSessionException
import com.andre.medalert.data.schedule.withServerHorarios
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MedicationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MedicationRepository(
        dao = AppDatabase.getInstance(application).medicationDao(),
        userPrefs = UserPreferencesRepository(application)
    )

    // ID do PACIENTE dono dos medicamentos (o próprio paciente, ou o vinculado ao cuidador).
    // Quem chama informa (vem do PatientContext na MainActivity); nunca é o ID do cuidador.
    private val patientId = MutableStateFlow<Int?>(null)

    // A tela observa SEMPRE o Room: quando o refresh grava na tabela, a lista atualiza sozinha.
    // Sem paciente (logout, cuidador sem vínculo) a lista fica vazia, nunca com dados do anterior.
    //
    // withServerHorarios(): só para EXIBIÇÃO. O GET do cuidador não traz a lista de horários
    // (doseDiaria); quando ela está vazia, monta os horários a partir de horario_dose + frequencia
    // pela regra do servidor. Nada disso é gravado no Room.
    @OptIn(ExperimentalCoroutinesApi::class)
    val medications: StateFlow<List<MedicationEntity>> = patientId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                repository.observeMedications(id).map { list -> list.map { it.withServerHorarios() } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Controle do refresh. Tudo roda na main (viewModelScope), então não precisa de trava.
    private var refreshJob: Job? = null

    /** Muda a cada refresh iniciado ou invalidado; um refresh com geração antiga não mexe no estado. */
    private var refreshGeneration = 0

    /** Pedido de refresh que chegou com outro em andamento: roda UMA vez depois, em vez de se perder. */
    private var refreshRequestedWhileRunning = false

    /**
     * Define o paciente dono dos medicamentos e atualiza a lista a partir da API (sem apagar o que
     * já está no Room). null = sem paciente: a lista esvazia e nada é buscado.
     *
     * Ao TROCAR de paciente, o refresh do anterior é cancelado e invalidado, e o aviso de erro do
     * paciente anterior é limpo.
     */
    fun setPatient(id: Int?) {
        if (patientId.value == id) return
        cancelRefresh()
        _error.value = null
        patientId.value = id
        if (id != null) refresh()
    }

    private fun cancelRefresh() {
        refreshGeneration++
        refreshJob?.cancel()
        refreshJob = null
        refreshRequestedWhileRunning = false
        _isRefreshing.value = false
    }

    fun refresh() {
        if (patientId.value == null) return

        // Já existe um refresh em andamento para este paciente: agenda mais um em vez de ignorar.
        if (refreshJob?.isActive == true) {
            refreshRequestedWhileRunning = true
            return
        }

        val generation = ++refreshGeneration
        refreshJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                do {
                    refreshRequestedWhileRunning = false
                    val id = patientId.value ?: return@launch

                    val result = repository.refresh(id)

                    // O paciente mudou (ou o refresh foi invalidado) durante a requisição:
                    // o resultado não pertence mais a esta tela.
                    if (generation != refreshGeneration || patientId.value != id) return@launch

                    result
                        .onSuccess {
                            // Um refresh bem-sucedido apaga o aviso de erro anterior.
                            _error.value = null
                        }
                        .onFailure { e ->
                            // StaleSession: a sessão mudou e este ViewModel ainda será avisado
                            // por setPatient; não é uma falha para mostrar ao usuário.
                            if (e !is StaleSessionException) {
                                // Offline ou erro do servidor: continua mostrando o que está no Room.
                                // (Um 401 também derruba a sessão no interceptor e leva ao login.)
                                _error.value = when {
                                    e is HttpException && e.code() == 401 ->
                                        "Sessão expirada. Entre novamente."
                                    else ->
                                        "Não foi possível atualizar. Mostrando dados salvos no celular."
                                }
                            }
                        }
                } while (refreshRequestedWhileRunning)
            } finally {
                // Só o refresh vigente encerra o indicador; um invalidado não apaga o do novo.
                if (generation == refreshGeneration) _isRefreshing.value = false
            }
        }
    }

    /**
     * Exclui pelo ID LOCAL. A API confirma primeiro; só então o Room remove e a lista atualiza
     * sozinha. Em caso de erro, o medicamento continua na lista e a mensagem aparece em [error]
     * (se a tela ainda for do mesmo paciente).
     */
    fun deleteMedication(localId: Long) {
        val owner = patientId.value
        viewModelScope.launch {
            repository.deleteMedication(localId)
                .onSuccess {
                    if (patientId.value == owner) _error.value = null
                }
                .onFailure {
                    if (patientId.value == owner) {
                        _error.value = it.message ?: "Não foi possível excluir o medicamento."
                    }
                }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
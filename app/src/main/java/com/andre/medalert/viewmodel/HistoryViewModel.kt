package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.model.HistoryPeriod
import com.andre.medalert.data.model.HistoryUiModel
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.data.schedule.DoseTimelineBuilder
import com.andre.medalert.data.schedule.HistoryRange
import com.andre.medalert.data.schedule.SessionMedications
import com.andre.medalert.data.schedule.toUiModel
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.sync.MedicationSyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

sealed interface HistoryUiState {
    object Loading : HistoryUiState
    data class Error(val message: String = "Não foi possível carregar o histórico.") : HistoryUiState
    data class Content(
        val records: List<HistoryUiModel>,
        val period: HistoryPeriod = HistoryPeriod.HOJE
    ) : HistoryUiState

    /** Cuidador logado sem paciente vinculado: não há histórico para mostrar. */
    object AwaitingLink : HistoryUiState
}

/**
 * Histórico = doses PREVISTAS (medicamentos do Room + DoseScheduleCalculator) cruzadas com os
 * REGISTROS de administração (Room). Não usa MockData e funciona offline.
 *
 * O GET /historys/doseDia só reconcilia o dia de hoje (sem apagar nada); Semana, Mês e Todos são
 * calculados localmente.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(AppDatabase.getInstance(application).doseHistoryDao())

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _period = MutableStateFlow(HistoryPeriod.HOJE)
    val period: StateFlow<HistoryPeriod> = _period.asStateFlow()

    private var observeJob: Job? = null

    init {
        load()
    }

    fun selectPeriod(period: HistoryPeriod) {
        _period.value = period
    }

    fun load() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading

            val session = UserPreferencesRepository(getApplication<Application>()).session.first()
            val patientId = AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)
            if (patientId == null ||
                !AccessPolicy.canViewHistory(session.tipo, session.userId, session.pacienteId)
            ) {
                _uiState.value =
                    if (AccessPolicy.isAwaitingLink(session.tipo, session.userId, session.pacienteId)) {
                        HistoryUiState.AwaitingLink
                    } else {
                        HistoryUiState.Error("Sessão expirada. Entre novamente.")
                    }
                return@launch
            }

            // Paciente e cuidador enviam as doses pendentes do paciente (medicamentos pendentes
            // sobem antes, na mesma sincronização).
            if (AccessPolicy.canRegisterDose(session.tipo, session.userId, session.pacienteId)) {
                try {
                    MedicationSyncWorker.enqueue(getApplication<Application>())
                } catch (_: Exception) {
                    // O envio será tentado de novo na próxima abertura do app.
                }
            }

            val medicationsFlow = SessionMedications.observe(getApplication<Application>())

            // Atualiza o dia de hoje com o servidor em paralelo. Se falhar, o Room fica como está
            // e a tela continua mostrando o que existe localmente.
            launch {
                val medications = medicationsFlow.first()
                repository.refresh(patientId, medications)
            }

            // Continua observando o Room: uma dose registrada pela notificação aparece
            // na tela na hora, mesmo com a tela já aberta.
            combine(
                medicationsFlow,
                repository.observeAll(patientId),
                _period,
                minuteTicker()
            ) { medications, records, period, now ->
                val earliest = HistoryRange.earliestDate(medications, records)
                val (from, toExclusive) = HistoryRange.of(period, now.toLocalDate(), earliest)
                val events = DoseTimelineBuilder.build(medications, records, from, toExclusive, now)
                HistoryUiState.Content(records = events.map { it.toUiModel() }, period = period)
            }.collect { content ->
                _uiState.value = content
            }
        }
    }

    /** Emite agora e a cada minuto, para os status mudarem sozinhos com a passagem do tempo. */
    private fun minuteTicker(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000)
        }
    }
}
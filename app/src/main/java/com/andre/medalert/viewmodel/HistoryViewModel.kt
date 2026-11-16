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
import com.andre.medalert.data.schedule.toUiModel
import com.andre.medalert.data.session.SessionIdentity
import com.andre.medalert.data.session.identity
import com.andre.medalert.sync.MedicationSyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
 *
 * A sessão é OBSERVADA, não lida uma vez: login, logout e troca de vínculo reiniciam a
 * observação para o paciente certo (collectLatest cancela a anterior, inclusive um refresh em
 * andamento). Medicamentos e registros vêm SEMPRE do mesmo paciente.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = HistoryRepository(database.doseHistoryDao())
    private val medicationDao = database.medicationDao()
    private val prefs = UserPreferencesRepository(application)

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

            prefs.session
                .map { it.identity() }
                .distinctUntilChanged()
                .collectLatest { identity -> observe(identity) }
        }
    }

    /** Observa o paciente de UMA identidade; é cancelada inteira quando a identidade muda. */
    private suspend fun observe(identity: SessionIdentity): Unit = coroutineScope {
        val patientId = identity.patientId
        if (patientId == null) {
            _uiState.value =
                if (identity.awaitingLink) {
                    HistoryUiState.AwaitingLink
                } else {
                    HistoryUiState.Error("Sessão expirada. Entre novamente.")
                }
            return@coroutineScope
        }

        // Nunca mostra o conteúdo do paciente anterior enquanto o novo carrega.
        _uiState.value = HistoryUiState.Loading

        // Paciente e cuidador enviam as doses pendentes do paciente (medicamentos pendentes
        // sobem antes, na mesma sincronização).
        try {
            MedicationSyncWorker.enqueue(getApplication<Application>())
        } catch (_: Exception) {
            // O envio será tentado de novo na próxima abertura do app.
        }

        // Medicamentos do MESMO paciente dos registros (não depende de outra leitura de sessão).
        val medicationsFlow = medicationDao.observeByUser(patientId)

        // Atualiza o dia de hoje com o servidor em paralelo. Se falhar, o Room fica como está
        // e a tela continua mostrando o que existe localmente. Se a identidade mudar, este
        // launch é cancelado junto com o scope: nada é gravado para o paciente antigo.
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

    /** Emite agora e a cada minuto, para os status mudarem sozinhos com a passagem do tempo. */
    private fun minuteTicker(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000)
        }
    }
}
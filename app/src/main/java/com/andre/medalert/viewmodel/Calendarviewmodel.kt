package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.data.schedule.CalendarDayUi
import com.andre.medalert.data.schedule.CalendarSummary
import com.andre.medalert.data.schedule.DoseTimelineBuilder
import com.andre.medalert.data.schedule.SessionMedications
import com.andre.medalert.data.session.AccessPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class CalendarUiState(
    val month: YearMonth,
    val cells: List<CalendarDayUi>
)

/**
 * Calendário = doses PREVISTAS (DoseScheduleCalculator, a partir dos medicamentos do Room)
 * cruzadas com os REGISTROS de administração. Mostra as doses mesmo sem nenhum registro, e o
 * status de cada dia (pendente, tomada, atrasada, não tomada) usa as mesmas regras do Histórico.
 */
class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(AppDatabase.getInstance(application).doseHistoryDao())

    private val visibleMonth = MutableStateFlow(YearMonth.now())

    /** Registros do paciente da sessão (o próprio paciente, ou o vinculado ao cuidador). */
    private val records: Flow<List<DoseHistoryEntity>> = flow {
        val session = UserPreferencesRepository(getApplication<Application>()).session.first()
        val patientId = AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)
        if (patientId == null) {
            emit(emptyList())
        } else {
            emitAll(repository.observeAll(patientId))
        }
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        SessionMedications.observe(application),
        records,
        visibleMonth,
        minuteTicker()
    ) { medications, history, month, now ->
        val from = month.atDay(1).atStartOfDay()
        val toExclusive = month.plusMonths(1).atDay(1).atStartOfDay()
        val events = DoseTimelineBuilder.build(medications, history, from, toExclusive, now)
        CalendarUiState(
            month = month,
            cells = CalendarSummary.cellsFor(month, events, now.toLocalDate())
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YearMonth.now().let { month ->
            CalendarUiState(
                month = month,
                cells = CalendarSummary.cellsFor(month, emptyList(), LocalDate.now())
            )
        }
    )

    fun showPreviousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun showNextMonth() {
        visibleMonth.value = visibleMonth.value.plusMonths(1)
    }

    /** Emite agora e a cada minuto, para os status mudarem sozinhos com a passagem do tempo. */
    private fun minuteTicker(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000)
        }
    }
}
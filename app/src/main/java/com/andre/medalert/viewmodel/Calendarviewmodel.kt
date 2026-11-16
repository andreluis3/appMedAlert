package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.repository.HistoryRepository
import com.andre.medalert.data.schedule.CalendarDayUi
import com.andre.medalert.data.schedule.CalendarSummary
import com.andre.medalert.data.schedule.DoseTimelineBuilder
import com.andre.medalert.data.session.identity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
 *
 * O paciente é OBSERVADO a partir da sessão: login, logout e troca de vínculo trocam os dados
 * sozinhos. Medicamentos e registros saem do MESMO paciente, no mesmo par, então nunca se cruzam
 * dados de pacientes diferentes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = HistoryRepository(database.doseHistoryDao())
    private val medicationDao = database.medicationDao()
    private val prefs = UserPreferencesRepository(application)

    private val visibleMonth = MutableStateFlow(YearMonth.now())

    /** Paciente da sessão (o próprio, ou o vinculado ao cuidador); null sem sessão/vínculo. */
    private val patientId: Flow<Int?> =
        prefs.session.map { it.identity().patientId }.distinctUntilChanged()

    /** Medicamentos e registros do mesmo paciente; trocar de paciente descarta o par anterior. */
    private val patientData: Flow<Pair<List<MedicationEntity>, List<DoseHistoryEntity>>> =
        patientId.flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList<MedicationEntity>() to emptyList<DoseHistoryEntity>())
            } else {
                combine(
                    medicationDao.observeByUser(id),
                    repository.observeAll(id)
                ) { medications, records -> medications to records }
            }
        }

    val uiState: StateFlow<CalendarUiState> = combine(
        patientData,
        visibleMonth,
        minuteTicker()
    ) { data, month, now ->
        val (medications, history) = data
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
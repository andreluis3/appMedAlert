package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.repository.MedicationRepository
import com.andre.medalert.data.schedule.DoseScheduleCalculator
import com.andre.medalert.data.schedule.SessionMedications
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.schedule.SessionUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.andre.medalert.data.repository.HistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HomeDoseUi(
    val key: String,
    val medicationName: String,
    val dosage: String,
    val time: String,
    val isPast: Boolean,
    /** true quando a dose foi registrada como tomada (pela notificação). */
    val isTaken: Boolean = false
)

data class HomeUiState(
    val userName: String? = null,
    val nextDoseLabel: String = "Nenhuma dose prevista",
    val dosesToday: List<HomeDoseUi> = emptyList(),
    val upcomingTimes: List<String> = emptyList(),
    val medicationsToday: Int = 0,
    val adherenceLabel: String = "Sem dados"
)

private val HORA_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DATA_FORMAT = DateTimeFormatter.ofPattern("dd/MM")

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = UserPreferencesRepository(application)
    private val db = AppDatabase.getInstance(application)
    private val medicationRepository = MedicationRepository(db.medicationDao(), prefs)
    private val historyRepository = HistoryRepository(db.doseHistoryDao())

    private val adherence = MutableStateFlow("Sem dados")

    private val ticker = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }

    /**
     * Doses já tomadas (registradas pela notificação), como "idLocal-horárioPrevistoMillis".
     * Observa o Room, então a Home atualiza sozinha quando "Tomar agora" é tocado.
     */
    private val takenKeys: Flow<Set<String>> = flow {
        emit(SessionUser.patientId(application))
    }.flatMapLatest { patientId ->
        if (patientId == null) {
            flowOf(emptySet())
        } else {
            val fromMillis = LocalDate.now().minusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            historyRepository.observeLocalTaken(patientId, fromMillis).map { rows ->
                rows.mapNotNull { row ->
                    val medId = row.medicationLocalId
                    val scheduled = row.previstoEm
                    if (medId != null && scheduled != null) "$medId-$scheduled" else null
                }.toSet()
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        prefs.userName,
        SessionMedications.observe(application),
        ticker,
        adherence,
        takenKeys
    ) { name, medications, now, adherenceLabel, taken ->
        build(name, medications, now, adherenceLabel, taken)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            val patientId = SessionUser.patientId(getApplication()) ?: return@launch
            // Offline-first: se falhar, a Home continua com o que já está no Room.
            medicationRepository.refresh(patientId)
            runCatching { historyRepository.getDashboard(patientId) }
                .onSuccess { dashboard ->
                    adherence.value =
                        if (dashboard.dosesEsp > 0) formatPercent(dashboard.adesao) else "Sem dados"
                }
        }
    }

    private fun build(
        name: String?,
        medications: List<MedicationEntity>,
        now: LocalDateTime,
        adherenceLabel: String,
        takenKeys: Set<String>
    ): HomeUiState {
        val active = medications.filter { it.ativo }
        val today = now.toLocalDate()
        val zone = ZoneId.systemDefault()
        val doses = DoseScheduleCalculator.dosesOnDay(active, today)

        val dosesUi = doses.map {
            val scheduledMillis = it.at.atZone(zone).toInstant().toEpochMilli()
            HomeDoseUi(
                key = "${it.medicationLocalId}-${it.at}",
                medicationName = it.medicationName,
                dosage = it.dosage,
                time = it.at.format(HORA_FORMAT),
                isPast = it.at.isBefore(now),
                isTaken = "${it.medicationLocalId}-$scheduledMillis" in takenKeys
            )
        }

        val next = DoseScheduleCalculator.nextDoseOverall(active, now)
        val nextLabel = when {
            next == null -> "Nenhuma dose prevista"
            next.at.toLocalDate() == today ->
                "Hoje às ${next.at.format(HORA_FORMAT)} • ${next.medicationName}"
            next.at.toLocalDate() == today.plusDays(1) ->
                "Amanhã às ${next.at.format(HORA_FORMAT)} • ${next.medicationName}"
            else ->
                "${next.at.format(DATA_FORMAT)} às ${next.at.format(HORA_FORMAT)} • ${next.medicationName}"
        }

        return HomeUiState(
            userName = name,
            nextDoseLabel = nextLabel,
            dosesToday = dosesUi,
            upcomingTimes = doses.filter { !it.at.isBefore(now) }
                .map { it.at.format(HORA_FORMAT) }.distinct(),
            medicationsToday = doses.map { it.medicationLocalId }.distinct().size,
            adherenceLabel = adherenceLabel
        )
    }

    /** "23.6" -> "23,6%", "50.0" -> "50%". Só formata, não recalcula. */
    private fun formatPercent(raw: String): String {
        val clean = raw.trim().removeSuffix("%").removeSuffix(".0")
        return clean.replace('.', ',') + "%"
    }
}
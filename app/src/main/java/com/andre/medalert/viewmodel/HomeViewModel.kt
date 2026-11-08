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
import com.andre.medalert.data.schedule.LocalAdherence
import com.andre.medalert.data.schedule.SessionUser
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.notification.DoseIntakeRegistrar
import com.andre.medalert.notification.DoseRegisterResult
import com.andre.medalert.notification.NotificationHelper
import com.andre.medalert.notification.SnoozeStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    /** ID LOCAL do medicamento no Room e horário previsto: identificam a dose ao registrar. */
    val medicationLocalId: Long,
    val scheduledAtMillis: Long,
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
    val adherenceLabel: String = "Sem dados",
    /** Cuidador sem paciente vinculado: a Home mostra o aviso em vez de uma tela vazia. */
    val awaitingLink: Boolean = false
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

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Avisos de uma só vez (toast) sobre o resultado de registrar uma dose. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

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

    private val awaitingLink: Flow<Boolean> = prefs.session
        .map { AccessPolicy.isAwaitingLink(it.tipo, it.userId, it.pacienteId) }
        .distinctUntilChanged()

    val uiState: StateFlow<HomeUiState> = combine(
        prefs.userName,
        SessionMedications.observe(application),
        ticker,
        adherence,
        takenKeys.combine(awaitingLink) { taken, awaiting -> taken to awaiting }
    ) { name, medications, now, adherenceLabel, (taken, awaiting) ->
        build(name, medications, now, adherenceLabel, taken).copy(awaitingLink = awaiting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            val patientId = SessionUser.patientId(getApplication()) ?: return@launch
            // Offline-first: se falhar, a Home continua com o que já está no Room.
            medicationRepository.refresh(patientId)
            val dashboard = runCatching { historyRepository.getDashboard(patientId) }.getOrNull()
            adherence.value = if (dashboard != null && dashboard.dosesEsp > 0) {
                formatPercent(dashboard.adesao)
            } else {
                // Sem internet, erro do servidor ou servidor sem dados: calcula com o Room.
                runCatching { LocalAdherence.compute(getApplication(), patientId) }
                    .getOrNull()
                    ?.takeIf { it.expected > 0 }
                    ?.percent
                    ?.let { "$it%" }
                    ?: "Sem dados"
            }
        }
    }

    /**
     * Registra a dose como tomada: o paciente registra a própria e o cuidador registra a do
     * paciente vinculado (a origem fica gravada). O envio à API é agendado pelo registrador.
     * O índice único do Room e a checagem por ID remoto impedem registrar duas vezes.
     */
    fun registerDose(dose: HomeDoseUi) {
        if (dose.isTaken) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = DoseIntakeRegistrar.registerTaken(
                context = app,
                medicationLocalId = dose.medicationLocalId,
                scheduledAtMillis = dose.scheduledAtMillis
            )
            when (result) {
                DoseRegisterResult.REGISTERED -> {
                    // Se o lembrete desta dose ainda está na tela ou adiado, não há mais o que lembrar.
                    SnoozeStore.cancel(app, dose.medicationLocalId)
                    NotificationHelper.cancelNotification(
                        app,
                        NotificationHelper.notificationIdFor(dose.medicationLocalId)
                    )
                    _messages.tryEmit("Dose registrada.")
                }

                DoseRegisterResult.ALREADY_REGISTERED ->
                    _messages.tryEmit("Esta dose já estava registrada.")

                DoseRegisterResult.MEDICATION_UNAVAILABLE ->
                    _messages.tryEmit("Não foi possível registrar: medicamento indisponível.")

                DoseRegisterResult.ERROR ->
                    _messages.tryEmit("Não foi possível registrar a dose. Tente novamente.")
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
                medicationLocalId = it.medicationLocalId,
                scheduledAtMillis = scheduledMillis,
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
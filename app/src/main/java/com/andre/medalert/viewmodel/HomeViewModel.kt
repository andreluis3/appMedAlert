package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.repository.MedicationRepository
import com.andre.medalert.data.schedule.CaregiverDoseSchedule
import com.andre.medalert.data.schedule.DoseScheduleCalculator
import com.andre.medalert.data.schedule.SessionMedications
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.schedule.LocalAdherence
import com.andre.medalert.data.session.LinkState
import com.andre.medalert.data.session.PatientContextProvider
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val isTaken: Boolean = false,
    /** Quantidade cadastrada do medicamento (ex.: "1 comprimido"); vazio se o Room não tem. */
    val quantity: String = ""
)

data class HomeUiState(
    val userName: String? = null,
    val nextDoseLabel: String = "Nenhuma dose prevista",
    val dosesToday: List<HomeDoseUi> = emptyList(),
    val upcomingTimes: List<String> = emptyList(),
    val isCaregiver: Boolean = false,
    val medicationsToday: Int = 0,
    val adherenceLabel: String = "Sem dados",
    /** Cuidador sem paciente vinculado: a Home mostra o aviso em vez de uma tela vazia. */
    val awaitingLink: Boolean = false,
    /** Cuidador vinculado: ID do paciente acompanhado (a API não devolve o nome dele). */
    /** Cuidador vinculado: ID do paciente acompanhado. */
    val linkedPatientId: Int? = null,
    /** Cuidador vinculado: nome do paciente (vem de GET /api/meds/cuid/{id}); null até chegar. */
    val linkedPatientName: String? = null,
    /**
     * Só o PACIENTE registra doses (o cuidador acompanha em somente leitura). Começa false e só
     * vira true quando o PatientContext confirma que a sessão é de paciente: assim o botão
     * "Registrar" nunca aparece, nem por um instante, para o cuidador.
     */
    val canRegisterDoses: Boolean = false
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
     * Paciente cujos dados a Home mostra (o próprio, ou o vinculado ao cuidador). Muda sozinho
     * quando o aceite grava o paciente_id: nada aqui exige recriar o ViewModel nem novo login.
     */
    private val patientIdFlow: Flow<Int?> = prefs.session
        .map { AccessPolicy.patientIdFor(it.tipo, it.userId, it.pacienteId) }
        .distinctUntilChanged()

    /**
     * Doses já tomadas (registradas pela notificação), como "idLocal-horárioPrevistoMillis".
     * Observa o Room, então a Home atualiza sozinha quando "Tomar agora" é tocado.
     */
    private val takenKeys: Flow<Set<String>> = patientIdFlow.flatMapLatest { patientId ->
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

    /** (aguardando vínculo, paciente vinculado). Atualiza sozinho quando o aceite grava o paciente_id. */
    private data class LinkInfo(
        val awaiting: Boolean,
        val patientId: Int?,
        val patientName: String?,
        val isCaregiver: Boolean,
        val canRegisterDoses: Boolean
    )

    /**
     * (aguardando vínculo, paciente vinculado e nome, permissão de registrar dose). Vem do
     * PatientContext, então muda sozinho no aceite, na troca de paciente (o nome antigo some),
     * quando o nome chega do /cuid/ e no logout.
     */
    private val linkState: Flow<LinkInfo> = PatientContextProvider(prefs).context
        .map { ctx ->
            val linked = ctx.patientId?.takeIf { ctx.linkState == LinkState.CAREGIVER_LINKED }
            LinkInfo(
                awaiting = ctx.isAwaitingLink,
                patientId = linked,
                patientName = if (linked != null) ctx.patientName else null,
                isCaregiver = ctx.isCaregiver,
                canRegisterDoses = ctx.canRegisterDoses
            )
        }
        .distinctUntilChanged()

    val uiState: StateFlow<HomeUiState> = combine(
        prefs.userName,
        SessionMedications.observe(application),
        ticker,
        adherence,
        takenKeys.combine(linkState) { taken, link -> taken to link }
    ) { name, medications, now, adherenceLabel, (taken, link) ->
        build(name, medications, now, adherenceLabel, taken, link.isCaregiver)
            .copy(
                isCaregiver = link.isCaregiver,
                awaitingLink = link.awaiting,
                linkedPatientId = link.patientId,
                linkedPatientName = link.patientName,
                canRegisterDoses = link.canRegisterDoses
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Reage à sessão: quando o cuidador aceita o vínculo o paciente_id muda e a Home carrega
        // os dados do paciente sem recriar o ViewModel nem pedir novo login.
        viewModelScope.launch {
            patientIdFlow.collectLatest { patientId ->
                if (patientId == null) {
                    adherence.value = "Sem dados"
                    return@collectLatest
                }
                // Offline-first: se falhar, a Home continua com o que já está no Room.
                medicationRepository.refreshForCurrentUser()
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
    }

    /**
     * Registra a dose como tomada. SÓ O PACIENTE registra a própria dose: o cuidador acompanha em
     * somente leitura. A permissão é conferida aqui (sessão lida na hora, sem depender da tela)
     * e de novo no [DoseIntakeRegistrar], que é o ponto central; esconder o botão não basta.
     * O envio à API é agendado pelo registrador. O índice único do Room e a checagem por ID
     * remoto impedem registrar duas vezes.
     */
    fun registerDose(dose: HomeDoseUi) {
        if (dose.isTaken) return
        viewModelScope.launch {
            val session = prefs.session.first()
            if (!AccessPolicy.canRegisterDose(session.tipo, session.userId, session.pacienteId)) {
                _messages.tryEmit("Somente o paciente pode registrar doses.")
                return@launch
            }

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
        takenKeys: Set<String>,
        isCaregiver: Boolean
    ): HomeUiState {
        val active = medications.filter { it.ativo }
        val today = now.toLocalDate()
        val zone = ZoneId.systemDefault()
        // Paciente: cálculo completo (datas de início/fim). Cuidador: o GET /cuid/ não traz
        // data_inicio/data_fim, então usa a regra de horários do próprio servidor (ver
        // CaregiverDoseSchedule). O cálculo do paciente não é alterado.
        val doses = if (isCaregiver) {
            CaregiverDoseSchedule.dosesOnDay(active, today)
        } else {
            DoseScheduleCalculator.dosesOnDay(active, today)
        }

        // Quantidade vem do mesmo medicamento do Room (mesmo formato do MedicationMapper).
        val byId = active.associateBy { it.id }

        val dosesUi = doses.map {
            val scheduledMillis = it.at.atZone(zone).toInstant().toEpochMilli()
            val entity = byId[it.medicationLocalId]
            val quantity = entity?.quantidade
                ?.let { q -> listOfNotNull(q.toString(), entity.unidadeQuantidade).joinToString(" ") }
                .orEmpty()
            HomeDoseUi(
                key = "${it.medicationLocalId}-${it.at}",
                medicationLocalId = it.medicationLocalId,
                scheduledAtMillis = scheduledMillis,
                medicationName = it.medicationName,
                dosage = it.dosage,
                time = it.at.format(HORA_FORMAT),
                isPast = it.at.isBefore(now),
                isTaken = "${it.medicationLocalId}-$scheduledMillis" in takenKeys,
                quantity = quantity
            )
        }

        val next = if (isCaregiver) {
            CaregiverDoseSchedule.nextDoseOverall(active, now)
        } else {
            DoseScheduleCalculator.nextDoseOverall(active, now)
        }
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
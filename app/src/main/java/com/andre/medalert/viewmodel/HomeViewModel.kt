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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.remote.dto.parsePercentOrNull
import com.andre.medalert.data.remote.dto.validationProblem
import com.andre.medalert.data.schedule.LocalAdherence
import com.andre.medalert.util.AppLog
import com.andre.medalert.data.session.LinkState
import com.andre.medalert.data.session.PatientContextProvider
import com.andre.medalert.data.schedule.SessionUser
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.data.session.identity
import com.andre.medalert.data.session.AdherenceRouting
import com.andre.medalert.data.session.AdherenceRoute
import com.andre.medalert.notification.DoseIntakeRegistrar
import com.andre.medalert.notification.DoseRegisterResult
import com.andre.medalert.notification.NotificationHelper
import com.andre.medalert.notification.SnoozeStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val canRegisterDoses: Boolean = false,
    // Novo: responsável salvo localmente.
    val responsibleName: String? = null
)

private val HORA_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DATA_FORMAT = DateTimeFormatter.ofPattern("dd/MM")

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = UserPreferencesRepository(application)
    private val db = AppDatabase.getInstance(application)
    private val medicationRepository = MedicationRepository(db.medicationDao(), prefs)
    private val historyRepository = HistoryRepository(db.doseHistoryDao())

    /** Texto do card "Adesão ao tratamento". "Sem dados" quando não há como avaliar (nunca "0%" falso). */
    private val adherence = MutableStateFlow(NO_ADHERENCE_DATA)

    /** Cada incremento pede uma nova consulta da adesão (ver [refreshAdherence]). */
    private val adherenceRefreshKey = MutableStateFlow(0)

    /** Paciente a quem o texto atual do card pertence: ao mudar, o valor antigo é descartado. */
    private var adherencePatientId: Int? = null

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

    /**
     * Primeiro responsável convidado pelo paciente.
     *
     * Observa o DataStore em tempo real.
     * Não consulta API e não considera convite como vínculo confirmado.
     */
    private val responsibleNameFlow: Flow<String?> = prefs.session
        .flatMapLatest { session ->
            val isPatient = !AccessPolicy.isCaregiver(session.tipo)
            val patientId = session.userId?.takeIf { it > 0 }

            if (!isPatient || patientId == null) {
                flowOf(null)
            } else {
                prefs.sentInvites(patientId)
                    .map { invites ->
                        invites.firstOrNull()
                            ?.nome
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                    }
            }
        }
        .distinctUntilChanged()

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

    private val detailedLinkFlow = combine(
        takenKeys,
        linkState,
        responsibleNameFlow
    ) { taken, link, responsibleName ->
        // Agrupamos em um Data Class auxiliar ou Triple para facilitar
        Triple(taken, link, responsibleName)
    }

    // 2. Agora o combine principal terá apenas 5 parâmetros, o que o Kotlin suporta nativamente
    val uiState: StateFlow<HomeUiState> = combine(
        prefs.userName,
        SessionMedications.observe(application),
        ticker,
        adherence,
        detailedLinkFlow // Este flow agora conta como 1 único parâmetro
    ) { name, medications, now, adherenceLabel, detailedLink ->

        // Desestruturamos o flow agrupado
        val (taken, link, responsibleName) = detailedLink

        build(
            name,
            medications,
            now,
            adherenceLabel,
            taken,
            link.isCaregiver
        ).copy(
            isCaregiver = link.isCaregiver,
            awaitingLink = link.awaiting,
            linkedPatientId = link.patientId,
            linkedPatientName = link.patientName,
            canRegisterDoses = link.canRegisterDoses,
            responsibleName = responsibleName
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState()
    )

    init {
        // DIAGNÓSTICO TEMPORÁRIO (só debug): relatório "MEDDIAG" no Logcat comparando Home e Remédios.
        if (com.andre.medalert.BuildConfig.DEBUG) {
            viewModelScope.launch {
                SessionMedications.observe(application)
                    .distinctUntilChanged()
                    .collectLatest { medications ->
                        com.andre.medalert.data.schedule.MedicationDiagnosticsLogger
                            .log(application, medications)
                    }
            }
        }

        // Reage à sessão: quando o cuidador aceita o vínculo o paciente_id muda e a Home carrega
        // os dados do paciente sem recriar o ViewModel nem pedir novo login.
        // Offline-first: se falhar, a Home continua com o que já está no Room.
        viewModelScope.launch {
            patientIdFlow.collectLatest { patientId ->
                if (patientId != null) medicationRepository.refreshForCurrentUser()
            }
        }

        // Adesão: consulta ao abrir, ao trocar de paciente e sempre que [refreshAdherence] for
        // chamado (voltar para a Home, dose registrada). Uma consulta nova cancela a anterior.
        viewModelScope.launch {
            combine(patientIdFlow, adherenceRefreshKey) { patientId, _ -> patientId }
                .collectLatest { patientId -> updateAdherence(patientId) }
        }
    }

    /**
     * Pede ao card de adesão que consulte de novo. Seguro chamar várias vezes (a consulta em
     * andamento é cancelada e refeita). A Home chama ao voltar para a tela.
     */
    fun refreshAdherence() {
        adherenceRefreshKey.update { it + 1 }
    }

    private suspend fun updateAdherence(patientId: Int?) {
        if (patientId == null) {
            adherencePatientId = null
            adherence.value = NO_ADHERENCE_DATA
            return
        }
        // Nunca mostra a adesão do paciente anterior enquanto a nova carrega.
        if (patientId != adherencePatientId) {
            adherencePatientId = patientId
            adherence.value = NO_ADHERENCE_DATA
        }

        val label = loadAdherenceLabel(patientId)
        // null = não deu para consultar agora (sem internet etc.): mantém o último valor válido.
        if (label != null && adherencePatientId == patientId) adherence.value = label
    }

    /**
     * Texto do card para [patientId]:
     * - resposta do servidor válida com percentual legível -> o percentual dele ("0%" só se o
     *   servidor realmente disse 0);
     * - servidor sem doses para avaliar -> cálculo no Room (como antes), ou "Sem dados";
     * - resposta malformada ou percentual ilegível -> "Sem dados" (NUNCA vira "0%");
     * - falha de rede/servidor -> cálculo no Room, ou null (mantém o valor anterior).
     */
    private suspend fun loadAdherenceLabel(patientId: Int): String? {
        val dashboard: DashboardResponse = try {
            // Paciente -> dashboard/{usuarioId}; cuidador vinculado -> dashboard/cuid/{pacienteId}.
            // Se o vínculo mudou desde que este paciente foi pedido, não consulta (nem mistura).
            val route = AdherenceRouting.route(prefs.session.first().identity())
            if (route !is AdherenceRoute.Remote || route.patientId != patientId) {
                AppLog.d(TAG, "adesão: sessão não aponta mais para o paciente $patientId; sem consulta")
                return NO_ADHERENCE_DATA
            }
            historyRepository.getDashboard(route)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return localAdherenceLabel(patientId)
        }

        if (dashboard.dosesEsp <= 0) return localAdherenceLabel(patientId) ?: NO_ADHERENCE_DATA

        val problem = dashboard.validationProblem()
        if (problem != null) {
            AppLog.w(TAG, "dashboard inválido ($problem): card sem dados, id=$patientId")
            return NO_ADHERENCE_DATA
        }

        val percent = parsePercentOrNull(dashboard.adesao) ?: return NO_ADHERENCE_DATA
        return formatPercentLabel(percent)
    }

    private suspend fun localAdherenceLabel(patientId: Int): String? =
        try {
            LocalAdherence.compute(getApplication(), patientId)
                ?.takeIf { it.expected > 0 }
                ?.percent
                ?.let { formatPercentLabel(it.toFloat()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
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
                    // Atualiza o card já e de novo depois, dando tempo de o envio ao servidor terminar.
                    refreshAdherence()
                    delay(ADHERENCE_REFRESH_AFTER_DOSE_MS)
                    refreshAdherence()
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

    /** 23.6f -> "23,6%", 50f -> "50%", 66.666f -> "66,7%". Só formata, não recalcula. */
    private fun formatPercentLabel(value: Float): String {
        val rounded = String.format(Locale("pt", "BR"), "%.1f", value.coerceIn(0f, 100f))
        return rounded.removeSuffix(",0") + "%"
    }

    private companion object {
        const val TAG = "HomeVM"
        const val NO_ADHERENCE_DATA = "Sem dados"
        const val ADHERENCE_REFRESH_AFTER_DOSE_MS = 6_000L
    }
}
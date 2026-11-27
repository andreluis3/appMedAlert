package com.andre.medalert.data.schedule

import android.content.Context
import com.andre.medalert.BuildConfig
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.data.session.PatientContextProvider
import com.andre.medalert.notification.AlarmScheduler
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * DIAGNÓSTICO TEMPORÁRIO. Escreve no Logcat (tag MEDDIAG) o relatório de
 * [MedicationDiagnostics.report] sempre que a lista que a Home observa mudar.
 *
 * Só funciona em build DEBUG (e AppLog já só escreve em debug). Não grava nada no Room, não
 * chama a API e não loga token. Os nomes dos medicamentos saem no log porque foram pedidos;
 * em release nada é escrito.
 *
 * Para remover depois da investigação: apague este arquivo e o bloco "MEDDIAG" do init do
 * HomeViewModel (e o log "MEDDIAG" do refresh em MedicationRepository).
 */
object MedicationDiagnosticsLogger {

    private const val TAG = "MEDDIAG"

    suspend fun log(context: Context, homeMedications: List<MedicationEntity>) {
        if (!BuildConfig.DEBUG) return
        try {
            val app = context.applicationContext
            val prefs = UserPreferencesRepository(app)
            val dao = AppDatabase.getInstance(app).medicationDao()

            val session = prefs.session.first()
            val patientIdHome =
                AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId)

            // Mesmo caminho que a MainActivity usa para alimentar a tela Remédios.
            val patientIdRemedios = withTimeoutOrNull(3_000) {
                PatientContextProvider(prefs).current().patientId
            }

            val porUsuario = dao.countByUser().associate { it.userId to it.total }
            val medicamentosRemedios =
                if (patientIdRemedios != null) dao.getAllByUser(patientIdRemedios) else emptyList()

            val relatorio = MedicationDiagnostics.report(
                day = LocalDate.now(),
                patientIdRemedios = patientIdRemedios,
                patientIdHome = patientIdHome,
                roomPorUsuario = porUsuario,
                medicamentosRemedios = medicamentosRemedios,
                medicamentosHome = homeMedications
            )
            // Uma linha de log por vez: o Logcat corta mensagens longas.
            relatorio.lines().forEach { AppLog.d(TAG, it) }

            // ALARMES: o que o AlarmManager realmente tem agendado para cada medicamento.
            AppLog.d(TAG, "----- ALARMES -----")
            AppLog.d(TAG, "Alarme exato permitido: ${AlarmScheduler.canScheduleExact(app)}")
            val agora = java.time.LocalDateTime.now()
            val zona = java.time.ZoneId.systemDefault()
            homeMedications.forEach { med ->
                val proxima = DoseScheduleCalculator.nextDose(med, agora)
                AppLog.d(
                    TAG,
                    "Medicamento id=${med.id}: próxima dose=${proxima ?: "nenhuma"} | " +
                            "alarme registrado=${AlarmScheduler.isScheduled(app, med.id)}" +
                            (proxima?.let { " | dispara em ${(it.atZone(zona).toInstant().toEpochMilli() - System.currentTimeMillis()) / 60_000} min" } ?: "")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Falha ao montar o diagnóstico", e)
        }
    }
}

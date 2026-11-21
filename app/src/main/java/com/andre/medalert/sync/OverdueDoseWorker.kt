package com.andre.medalert.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.model.HistoryDoseStatus
import com.andre.medalert.data.model.NotificationType
import com.andre.medalert.data.schedule.DoseTimelineBuilder
import com.andre.medalert.data.schedule.OverdueDoseDetector
import com.andre.medalert.data.session.AccessPolicy
import com.andre.medalert.notification.NotificationRecorder
import com.andre.medalert.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Grava na caixa de notificações as doses ATRASADAS e NÃO REGISTRADAS do paciente.
 *
 * - NÃO exige internet: usa só o Room (medicamentos e registros de dose deste aparelho).
 * - Só roda para o PACIENTE (quem registra doses neste aparelho). Para o cuidador, o Room pode não
 *   ter ainda a dose que o paciente tomou (aparelho do paciente sem conexão): avisar atraso com
 *   dados incompletos seria um aviso falso. O cuidador recebe esses avisos por FCM
 *   (RemoteEventHandler), quando o backend os enviar.
 * - O status vem do DoseTimelineBuilder (mesmas regras do Histórico/Calendário): atrasado = mais
 *   de 30 min sem registro; não tomado = mais de 120 min.
 * - Uma notificação por dose e por tipo (chave de deduplicação), então repetir a verificação não duplica.
 * - Só olha as últimas [LOOKBACK_HOURS] horas: não gera enxurrada de avisos antigos.
 * - Não cria alarme nem aviso do sistema: só grava na caixa. Não toca nos alarmes existentes.
 */
class OverdueDoseWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            checkOverdueDoses(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Falhou agora: a próxima execução periódica tenta de novo.
            AppLog.e(TAG, "Falha ao verificar doses atrasadas", e)
            Result.success()
        }
    }

    private suspend fun checkOverdueDoses(app: Context) {
        val session = UserPreferencesRepository(app).session.first()
        if (!AccessPolicy.canRegisterDose(session.tipo, session.userId, session.pacienteId)) return
        val patientId =
            AccessPolicy.patientIdFor(session.tipo, session.userId, session.pacienteId) ?: return

        val db = AppDatabase.getInstance(app)
        val medicationDao = db.medicationDao()
        val medications = (medicationDao.getSyncedByUser(patientId) +
                medicationDao.getUnsyncedByUser(patientId)).filter { it.ativo }
        if (medications.isEmpty()) return

        val records = db.doseHistoryDao().observeByUser(patientId).first()

        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val events = DoseTimelineBuilder.build(
            medications = medications,
            records = records,
            from = now.minusHours(LOOKBACK_HOURS),
            toExclusive = now.plusMinutes(1),
            now = now,
            zone = zone
        )

        val hour = DateTimeFormatter.ofPattern("HH:mm")

        OverdueDoseDetector.overdue(events).forEach { event ->
            val localId = event.medicationLocalId ?: return@forEach
            val medication = medications.firstOrNull { it.id == localId } ?: return@forEach

            // Mesma identidade de dose do alarme e do registro: horário previsto em millis, no minuto.
            val scheduledAt = event.scheduledAt.truncatedTo(ChronoUnit.MINUTES)
            val scheduledMillis = scheduledAt.atZone(zone).toInstant().toEpochMilli()
            val missed = event.status == HistoryDoseStatus.NAO_TOMADO

            val doseLine =
                if (medication.dosagem.isBlank()) medication.nome
                else "${medication.nome} (${medication.dosagem})"
            val hourLabel = hour.format(scheduledAt)

            NotificationRecorder.record(
                context = app,
                tipo = if (missed) NotificationType.MEDICATION_MISSED else NotificationType.MEDICATION_DELAYED,
                titulo = if (missed) "Dose não registrada" else "Dose atrasada",
                descricao = if (missed) {
                    "Não há registro da dose de $doseLine das $hourLabel."
                } else {
                    "A dose de $doseLine das $hourLabel ainda não foi registrada."
                },
                dedupeKey = NotificationRecorder.overdueKey(
                    missed = missed,
                    patientId = patientId,
                    medicationLocalId = medication.id,
                    previstoEm = scheduledMillis
                ),
                patientId = patientId,
                medicationLocalId = medication.id,
                medicationRemoteId = medication.remoteId,
                medicationNome = medication.nome,
                previstoEm = scheduledMillis
            )
        }
    }

    companion object {
        private const val TAG = "OverdueDoseWorker"
        private const val PERIODIC_NAME = "overdue_dose_check"
        private const val ONCE_NAME = "overdue_dose_check_once"

        /** Só doses previstas nas últimas horas são avaliadas (mesma ordem de grandeza da janela de ação). */
        private const val LOOKBACK_HOURS = 12L

        /** Verificação periódica (mínimo do WorkManager: 15 min), sem exigir internet. Agenda uma vez (KEEP). */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<OverdueDoseWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Uma verificação logo agora (abertura do processo). Se já há uma na fila, não empilha. */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<OverdueDoseWorker>().build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
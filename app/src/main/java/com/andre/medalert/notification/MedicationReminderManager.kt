package com.andre.medalert.notification

import android.content.Context
import com.andre.medalert.data.local.AppDatabase
import com.andre.medalert.data.local.UserPreferencesRepository
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.schedule.DoseScheduleCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Guarda quais medicamentos têm alarme, para cancelar os que sumiram do Room (excluídos, inativos). */
private class ScheduledAlarmStore(context: Context) {
    private val prefs = context.getSharedPreferences("medalert_alarm_store", Context.MODE_PRIVATE)

    fun ids(): Set<Long> =
        (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    fun save(ids: Set<Long>) {
        prefs.edit().putStringSet(KEY, ids.map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val KEY = "ids"
    }
}

object MedicationReminderManager {

    private val mutex = Mutex()

    /**
     * Sincroniza os alarmes com a lista recebida: agenda a próxima dose de cada medicamento ativo,
     * cancela os alarmes dos que deixaram de existir/ficaram inativos/terminaram.
     * [notBeforeMillis]: usado pelo receiver para nunca recalcular uma dose já disparada.
     */
    suspend fun apply(
        context: Context,
        medications: List<MedicationEntity>,
        notBeforeMillis: Long? = null
    ) = mutex.withLock {
        val app = context.applicationContext
        val store = ScheduledAlarmStore(app)
        val zone = ZoneId.systemDefault()

        var after = LocalDateTime.now()
        if (notBeforeMillis != null) {
            val fired = LocalDateTime.ofInstant(Instant.ofEpochMilli(notBeforeMillis), zone)
            if (fired > after) after = fired
        }

        val scheduled = mutableSetOf<Long>()
        medications.forEach { med ->
            val next = DoseScheduleCalculator.nextDose(med, after) ?: return@forEach
            AlarmScheduler.scheduleDose(
                context = app,
                medicationLocalId = med.id,
                medicationName = med.nome,
                triggerAtMillis = next.atZone(zone).toInstant().toEpochMilli()
            )
            scheduled += med.id
        }

        (store.ids() - scheduled).forEach { removedId ->
            AlarmScheduler.cancel(app, removedId)
            NotificationHelper.cancelNotification(app, NotificationHelper.notificationIdFor(removedId))
        }
        store.save(scheduled)
    }

    /** Relê o Room e reagenda tudo (abrir o app, boot, troca de fuso, permissão de alarme exato). */
    suspend fun rescheduleAll(context: Context, notBeforeMillis: Long? = null) {
        val app = context.applicationContext
        val medications = withContext(Dispatchers.IO) { loadPatientMedications(app) }
        apply(app, medications, notBeforeMillis)
    }

    /** Medicamento que ainda deve gerar notificação: existe, está ativo e é do paciente da sessão. */
    suspend fun findDeliverable(context: Context, localId: Long): MedicationEntity? =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val prefs = UserPreferencesRepository(app)
            if (prefs.userType.first() != "paciente") return@withContext null
            val userId = prefs.userId.first() ?: return@withContext null

            AppDatabase.getInstance(app).medicationDao().getById(localId)
                ?.takeIf { it.ativo && it.userId == userId }
        }

    private suspend fun loadPatientMedications(app: Context): List<MedicationEntity> {
        val prefs = UserPreferencesRepository(app)
        if (prefs.userType.first() != "paciente") return emptyList()
        val userId = prefs.userId.first()?.takeIf { it > 0 } ?: return emptyList()

        val dao = AppDatabase.getInstance(app).medicationDao()
        return dao.getSyncedByUser(userId) + dao.getUnsyncedByUser(userId)
    }
}
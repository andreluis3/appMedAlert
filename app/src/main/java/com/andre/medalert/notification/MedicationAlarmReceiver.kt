package com.andre.medalert.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val localId = intent.getLongExtra(AlarmScheduler.EXTRA_LOCAL_ID, -1L)
        val firedAt = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, System.currentTimeMillis())
        // No adiamento, EXTRA_TRIGGER_AT guarda o horário ORIGINAL da dose (identidade preservada).
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)

        NotificationHelper.createNotificationChannel(app)

        // Alarme de demonstração (sem ID do Room): só mostra o aviso.
        if (localId < 0) {
            val name = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_NAME) ?: "Medicamento"
            NotificationHelper.showMedicationNotification(
                app, name, NotificationHelper.NOTIFICATION_ID_BASE + name.hashCode()
            )
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isSnooze) {
                    // O adiamento venceu: some do armazenamento e mostra a mesma dose de novo,
                    // a menos que ela já tenha sido registrada como tomada.
                    SnoozeStore.clear(app, localId)
                    if (!DoseIntakeRegistrar.isAlreadyRegistered(app, localId, firedAt)) {
                        showFromRoom(app, localId, firedAt)
                    }
                    // O alarme da próxima dose regular já está agendado: não mexe nele aqui.
                } else {
                    // Dose nova: substitui qualquer adiamento pendente da dose anterior.
                    SnoozeStore.cancel(app, localId)
                    showFromRoom(app, localId, firedAt)
                    // Agenda a próxima dose (ou cancela, se o medicamento acabou/foi removido).
                    MedicationReminderManager.rescheduleAll(app, notBeforeMillis = firedAt)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Os dados vêm do Room (fonte de verdade), não dos extras do alarme. */
    private suspend fun showFromRoom(app: Context, localId: Long, scheduledAt: Long) {
        val med = MedicationReminderManager.findDeliverable(app, localId) ?: return
        NotificationHelper.showMedicationNotification(
            context = app,
            localId = med.id,
            medicationName = med.nome,
            dosage = med.dosagem,
            scheduledAtMillis = scheduledAt
        )
    }
}
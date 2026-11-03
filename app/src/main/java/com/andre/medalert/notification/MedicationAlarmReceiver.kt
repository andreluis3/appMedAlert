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
                // Os dados vêm do Room (fonte de verdade), não dos extras do alarme.
                val med = MedicationReminderManager.findDeliverable(app, localId)
                if (med != null) {
                    NotificationHelper.showMedicationNotification(
                        context = app,
                        localId = med.id,
                        medicationName = med.nome,
                        dosage = med.dosagem,
                        scheduledAtMillis = firedAt
                    )
                }
                // Agenda a próxima dose (ou cancela, se o medicamento acabou/foi removido).
                MedicationReminderManager.rescheduleAll(app, notBeforeMillis = firedAt)
            } finally {
                pending.finish()
            }
        }
    }
}
package com.andre.medalert.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Ação "Registrar como tomado" da notificação. */
class DismissAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        val localId = intent.getLongExtra(NotificationHelper.EXTRA_LOCAL_ID, -1L)
        val scheduledAt = intent.getLongExtra(NotificationHelper.EXTRA_SCHEDULED_AT, -1L)

        if (notificationId != -1) {
            NotificationHelper.cancelNotification(context, notificationId)
        }

        if (localId >= 0 && scheduledAt > 0) {
            val app = context.applicationContext
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DoseIntakeRegistrar.registerTaken(app, localId, scheduledAt)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
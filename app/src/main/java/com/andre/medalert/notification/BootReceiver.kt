package com.andre.medalert.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reagenda os alarmes após reinício, atualização do app, troca de fuso ou mudança da permissão
 * de alarme exato. Também restaura os adiamentos pendentes ("Adiar 5 min").
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationHelper.createNotificationChannel(app)
                MedicationReminderManager.rescheduleAll(app)
                SnoozeStore.restoreAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
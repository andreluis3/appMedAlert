package com.andre.medalert.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DismissAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("extra_notification_id", -1)
        if (notificationId != -1) {
            NotificationHelper.cancelNotification(context, notificationId)
        }
        // Nada salvo no banco/backend ainda - só encerra o alerta visual, como combinado.
    }
}
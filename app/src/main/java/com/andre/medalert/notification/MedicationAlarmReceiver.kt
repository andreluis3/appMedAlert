package com.andre.medalert.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationName = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_NAME) ?: "Medicamento"

        NotificationHelper.createNotificationChannel(context)

        val notificationId = NotificationHelper.NOTIFICATION_ID_BASE + medicationName.hashCode()
        NotificationHelper.showMedicationNotification(context, medicationName, notificationId)
    }
}
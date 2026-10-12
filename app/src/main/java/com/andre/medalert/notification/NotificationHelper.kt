package com.andre.medalert.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.andre.medalert.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_ID = "medalert_medication_channel"
    private const val CHANNEL_NAME = "Lembretes do MedAlert"
    const val NOTIFICATION_ID_BASE = 5000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = Uri.parse(
                "android.resource://${context.packageName}/${R.raw.som_notificacao_medalert}"
            )
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de horário de medicamentos do MedAlert"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(soundUri, audioAttributes)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showMedicationNotification(context: Context, medicationName: String, notificationId: Int) {
        val dismissIntent = Intent(context, DismissAlarmReceiver::class.java).apply {
            putExtra("extra_notification_id", notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val horarioAtual = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: trocar pelo ícone real do MedAlert
            .setContentTitle("💊 MedAlert — Hora do medicamento")
            .setContentText("$medicationName • às $horarioAtual")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$medicationName\nHorário: $horarioAtual")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(0, "JÁ TOMEI", dismissPendingIntent)
            .build()

        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.notify(notificationId, notification)
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.cancel(notificationId)
    }
}
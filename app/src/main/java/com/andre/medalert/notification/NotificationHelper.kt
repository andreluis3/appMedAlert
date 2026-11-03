package com.andre.medalert.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.andre.medalert.MainActivity
import com.andre.medalert.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_ID = "medalert_medication_channel"
    private const val CHANNEL_NAME = "Lembretes do MedAlert"
    const val NOTIFICATION_ID_BASE = 5000

    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_LOCAL_ID = "extra_local_id"
    const val EXTRA_SCHEDULED_AT = "extra_scheduled_at"

    /** Um ID por medicamento: uma dose nova substitui a anterior ainda não confirmada. */
    fun notificationIdFor(localId: Long): Int = NOTIFICATION_ID_BASE + (localId % 1_000_000L).toInt()

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

            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /** Notificação de dose real: "Está na hora de tomar Losartana — 50 mg". */
    @SuppressLint("MissingPermission")
    fun showMedicationNotification(
        context: Context,
        localId: Long,
        medicationName: String,
        dosage: String,
        scheduledAtMillis: Long
    ) {
        val manager = NotificationManagerCompat.from(context)
        // Usuário negou a permissão (ou desligou as notificações): não há o que mostrar.
        if (!manager.areNotificationsEnabled()) return

        val notificationId = notificationIdFor(localId)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            context, notificationId, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação "Registrar como tomado": só prepara a estrutura (ver DoseIntakeRegistrar).
        val takenIntent = Intent(context, DismissAlarmReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_LOCAL_ID, localId)
            putExtra(EXTRA_SCHEDULED_AT, scheduledAtMillis)
        }
        val takenPending = PendingIntent.getBroadcast(
            context, notificationId, takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (dosage.isBlank()) {
            "Está na hora de tomar $medicationName"
        } else {
            "Está na hora de tomar $medicationName — $dosage"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: trocar pelo ícone real do MedAlert
            .setContentTitle("Hora do medicamento")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setWhen(scheduledAtMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openAppPending)
            .addAction(0, "Registrar como tomado", takenPending)
            .build()

        try {
            manager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permissão revogada no meio do caminho.
        }
    }

    /** Aviso simples (alarme de demonstração, sem medicamento do Room). */
    @SuppressLint("MissingPermission")
    fun showMedicationNotification(context: Context, medicationName: String, notificationId: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val horarioAtual = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Hora do medicamento")
            .setContentText("Está na hora de tomar $medicationName • $horarioAtual")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
package com.andre.medalert.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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

    // Aviso de estoque baixo: canal próprio (importância normal, sem som de alarme).
    const val STOCK_CHANNEL_ID = "medalert_stock_channel"
    private const val STOCK_CHANNEL_NAME = "Estoque de medicamentos"
    private const val STOCK_NOTIFICATION_ID_BASE = 7_000_000

    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_LOCAL_ID = "extra_local_id"
    const val EXTRA_SCHEDULED_AT = "extra_scheduled_at"

    // Request codes dos botões. Os intents também diferem pela action, mas separar evita colisão.
    private const val TAKE_REQUEST_BASE = 2_000_000
    private const val SNOOZE_REQUEST_BASE = 3_000_000

    // Cores do tema do MedAlert (MedAlertBackground e MedAlertPrimary).
    private val COLOR_BACKGROUND = 0xFF0B0F10.toInt()
    private val COLOR_ACCENT = 0xFF2DD4CF.toInt()

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

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            manager.createNotificationChannel(
                NotificationChannel(
                    STOCK_CHANNEL_ID,
                    STOCK_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Avisa quando o estoque de um medicamento está acabando"
                }
            )
        }
    }

    /** Um ID por medicamento: o aviso de estoque substitui o anterior do mesmo remédio. */
    fun stockNotificationIdFor(localId: Long): Int =
        STOCK_NOTIFICATION_ID_BASE + (localId % 1_000_000L).toInt()

    /**
     * Aviso de estoque baixo/esgotado. Toque abre o app. Não faz nada se as notificações
     * estiverem desligadas.
     */
    @SuppressLint("MissingPermission")
    fun showLowStockNotification(
        context: Context,
        localId: Long,
        medicationName: String,
        remaining: Int
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val notificationId = stockNotificationIdFor(localId)
        val title = if (remaining <= 0) "Estoque esgotado" else "Estoque baixo"
        val text = if (remaining <= 0) {
            "Acabou o estoque de $medicationName. Reponha antes da próxima dose."
        } else {
            "Restam $remaining de $medicationName. Reponha em breve."
        }

        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, STOCK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_medalert)
            .setColor(COLOR_ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)

        try {
            manager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permissão revogada no meio do caminho.
        }
    }

    /**
     * Notificação de dose real, com ações rápidas que funcionam com o app fechado:
     * "Tomar agora" (registra no Room) e "Adiar 5 min" (só reagenda, sem histórico).
     */
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
        val offset = notificationId - NOTIFICATION_ID_BASE

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            context, notificationId, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takePending = actionPending(
            context, DismissAlarmReceiver.ACTION_TAKE, TAKE_REQUEST_BASE + offset,
            notificationId, localId, scheduledAtMillis
        )
        val snoozePending = actionPending(
            context, DismissAlarmReceiver.ACTION_SNOOZE, SNOOZE_REQUEST_BASE + offset,
            notificationId, localId, scheduledAtMillis
        )

        val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(scheduledAtMillis))
        val doseLine = if (dosage.isBlank()) medicationName else "$medicationName — $dosage"
        val title = "Hora do medicamento 💊"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_medalert)
            .setColor(COLOR_ACCENT)
            .setContentTitle(title)
            .setContentText(doseLine)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText("$doseLine\nEstá na hora de tomar seu medicamento.")
                    .setSummaryText("Programado para $timeLabel")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setWhen(scheduledAtMillis)
            .setShowWhen(true)
            // Tocar no corpo abre o app, mas a notificação continua até tomar ou adiar.
            .setAutoCancel(false)
            .setContentIntent(openAppPending)
            .addAction(0, "Tomar agora", takePending)
            .addAction(0, "Adiar 5 min", snoozePending)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        buildLargeIcon(context)?.let { builder.setLargeIcon(it) }

        try {
            manager.notify(notificationId, builder.build())
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
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_medalert)
            .setColor(COLOR_ACCENT)
            .setContentTitle("Hora do medicamento 💊")
            .setContentText("Está na hora de tomar $medicationName • $horarioAtual")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        buildLargeIcon(context)?.let { builder.setLargeIcon(it) }

        try {
            manager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    // ------------------------------------------------------------------ helpers

    private fun actionPending(
        context: Context,
        action: String,
        requestCode: Int,
        notificationId: Int,
        localId: Long,
        scheduledAtMillis: Long
    ): PendingIntent {
        val intent = Intent(context, DismissAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_LOCAL_ID, localId)
            putExtra(EXTRA_SCHEDULED_AT, scheduledAtMillis)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Ícone grande: a logo Dosy (res/drawable/dosy_logo.png) sobre um quadrado arredondado
     * escuro (cor de fundo do app) com um anel turquesa sutil.
     */
    private fun buildLargeIcon(context: Context): Bitmap? {
        return try {
            val density = context.resources.displayMetrics.density
            val size = (64 * density).toInt().coerceAtLeast(96)

            val logo = decodeSampled(context, R.drawable.dosy_logo, size) ?: return null

            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val s = size.toFloat()

            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BACKGROUND }
            canvas.drawRoundRect(RectF(0f, 0f, s, s), s * 0.24f, s * 0.24f, backgroundPaint)

            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = s * 0.025f
                color = COLOR_ACCENT
                alpha = 110
            }
            val inset = s * 0.02f
            canvas.drawRoundRect(
                RectF(inset, inset, s - inset, s - inset),
                s * 0.22f, s * 0.22f, ringPaint
            )

            // A logo é larga: cabe pela largura, centralizada.
            val maxSide = s * 0.78f
            val scale = minOf(maxSide / logo.width, maxSide / logo.height)
            val w = logo.width * scale
            val h = logo.height * scale
            val left = (s - w) / 2f
            val top = (s - h) / 2f
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(logo, null, RectF(left, top, left + w, top + h), logoPaint)

            output
        } catch (_: Throwable) {
            null
        }
    }

    /** Decodifica o PNG já reduzido, para não carregar a imagem inteira na memória. */
    private fun decodeSampled(context: Context, resId: Int, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inScaled = false
        }
        BitmapFactory.decodeResource(context.resources, resId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
        }
        return BitmapFactory.decodeResource(context.resources, resId, options)
    }
}
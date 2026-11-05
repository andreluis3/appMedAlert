package com.andre.medalert.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Ações dos botões da notificação (funcionam com o app fechado):
 * - ACTION_TAKE: registra a dose como tomada no Room e cancela a notificação e o adiamento.
 * - ACTION_SNOOZE: só reagenda a notificação para daqui a 5 min. NÃO mexe no histórico.
 */
class DismissAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TAKE = "com.andre.medalert.action.TAKE_DOSE"
        const val ACTION_SNOOZE = "com.andre.medalert.action.SNOOZE_DOSE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val extraNotificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        val localId = intent.getLongExtra(NotificationHelper.EXTRA_LOCAL_ID, -1L)
        val scheduledAt = intent.getLongExtra(NotificationHelper.EXTRA_SCHEDULED_AT, -1L)

        // Sem identificação da dose não há o que registrar nem adiar: só limpa a notificação.
        if (localId < 0 || scheduledAt <= 0) {
            if (extraNotificationId != -1) {
                NotificationHelper.cancelNotification(app, extraNotificationId)
            }
            return
        }

        val notificationId =
            if (extraNotificationId != -1) extraNotificationId
            else NotificationHelper.notificationIdFor(localId)

        when (intent.action) {
            ACTION_SNOOZE -> {
                // Não registra nada: a mesma dose (mesmo horário previsto) volta em 5 min.
                NotificationHelper.cancelNotification(app, notificationId)
                SnoozeStore.snooze(app, localId, scheduledAt)
            }

            else -> takeDose(app, notificationId, localId, scheduledAt)
        }
    }

    private fun takeDose(app: Context, notificationId: Int, localId: Long, scheduledAt: Long) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = DoseIntakeRegistrar.registerTaken(app, localId, scheduledAt)

                if (result == DoseRegisterResult.ERROR) {
                    // Mantém a notificação para o usuário tentar de novo.
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            app,
                            "Não foi possível registrar a dose. Tente novamente.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    // Registrada, já registrada ou medicamento indisponível: não há mais o que lembrar.
                    SnoozeStore.cancel(app, localId)
                    NotificationHelper.cancelNotification(app, notificationId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
package com.andre.medalert.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {

    const val EXTRA_MEDICATION_NAME = "extra_medication_name"

    /**
     * Agenda o alerta pra um horário específico (ex: 08:00).
     * Se esse horário já passou hoje, agenda automaticamente pra amanhã.
     */
    fun scheduleMedicationAlarm(context: Context, hour: Int, minute: Int, medicationName: String) {
        val triggerTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        scheduleAt(context, triggerTime.timeInMillis, medicationName, requestCode = medicationName.hashCode())
    }

    /**
     * Só pra DEMO: agenda o alerta pra daqui a ~1 minuto,
     * sem precisar esperar o horário real. Não substitui a função de cima.
     */
    fun scheduleTestAlarmIn1Minute(context: Context, medicationName: String = "Losartana (teste)") {
        val triggerTime = System.currentTimeMillis() + 60_000L
        scheduleAt(context, triggerTime, medicationName, requestCode = 999_999)
    }

    private fun scheduleAt(context: Context, triggerAtMillis: Long, medicationName: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_MEDICATION_NAME, medicationName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canUseExactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        if (canUseExactAlarm) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            // Sem permissão de alarme exato concedida: cai pro modo "inexato".
            // Ainda funciona pra demo, só pode atrasar alguns minutos.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }
}
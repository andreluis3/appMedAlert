package com.andre.medalert.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Um alarme por medicamento, sempre para a PRÓXIMA dose. O requestCode vem do ID local do Room,
 * então reagendar substitui o alarme anterior (sem duplicar). Ao disparar, o receiver agenda o seguinte.
 *
 * O adiamento ("Adiar 5 min") usa um alarme PRÓPRIO por medicamento (outro requestCode), então
 * nunca sobrescreve o alarme da próxima dose regular.
 */
object AlarmScheduler {

    const val EXTRA_MEDICATION_NAME = "extra_medication_name"
    const val EXTRA_LOCAL_ID = "extra_local_id"
    const val EXTRA_TRIGGER_AT = "extra_trigger_at"
    const val EXTRA_IS_SNOOZE = "extra_is_snooze"

    private const val REQUEST_CODE_BASE = 100_000
    private const val SNOOZE_REQUEST_CODE_BASE = 1_200_000
    private const val TEST_REQUEST_CODE = Int.MAX_VALUE - 1

    fun requestCodeFor(localId: Long): Int = REQUEST_CODE_BASE + (localId % 1_000_000L).toInt()

    fun snoozeRequestCodeFor(localId: Long): Int =
        SNOOZE_REQUEST_CODE_BASE + (localId % 1_000_000L).toInt()

    /** true quando o app pode usar alarmes exatos (Android 12+ exige permissão especial). */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return manager.canScheduleExactAlarms()
    }

    fun scheduleDose(
        context: Context,
        medicationLocalId: Long,
        medicationName: String,
        triggerAtMillis: Long
    ) {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_LOCAL_ID, medicationLocalId)
            putExtra(EXTRA_MEDICATION_NAME, medicationName)
            putExtra(EXTRA_TRIGGER_AT, triggerAtMillis)
        }
        setAlarm(context, triggerAtMillis, requestCodeFor(medicationLocalId), intent)
    }

    /**
     * Reapresenta a MESMA dose em [triggerAtMillis]. [scheduledAtMillis] é o horário original
     * previsto (identidade da dose) e volta no extra EXTRA_TRIGGER_AT.
     */
    fun scheduleSnooze(
        context: Context,
        medicationLocalId: Long,
        scheduledAtMillis: Long,
        triggerAtMillis: Long
    ) {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_LOCAL_ID, medicationLocalId)
            putExtra(EXTRA_TRIGGER_AT, scheduledAtMillis)
            putExtra(EXTRA_IS_SNOOZE, true)
        }
        setAlarm(context, triggerAtMillis, snoozeRequestCodeFor(medicationLocalId), intent)
    }

    fun cancel(context: Context, medicationLocalId: Long) {
        cancelByRequestCode(context, requestCodeFor(medicationLocalId))
    }

    fun cancelSnooze(context: Context, medicationLocalId: Long) {
        cancelByRequestCode(context, snoozeRequestCodeFor(medicationLocalId))
    }

    /** Só para DEMO: dispara daqui a ~1 minuto, sem passar pelo Room. */
    fun scheduleTestAlarmIn1Minute(context: Context, medicationName: String = "Losartana (teste)") {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_MEDICATION_NAME, medicationName)
        }
        setAlarm(context, System.currentTimeMillis() + 60_000L, TEST_REQUEST_CODE, intent)
    }

    /**
     * Antiga API de alarme único por nome (usava hashCode e não repetia).
     * Agora o agendamento é automático a partir do Room; mantida só para não quebrar o build.
     * Apague as chamadas a ela (procure por "scheduleMedicationAlarm") e remova esta função.
     */
    @Deprecated("O agendamento é automático a partir do Room. Apague as chamadas.")
    @Suppress("UNUSED_PARAMETER")
    fun scheduleMedicationAlarm(context: Context, hour: Int, minute: Int, medicationName: String) {
        // Intencionalmente vazio: evita notificação duplicada.
    }

    private fun cancelByRequestCode(context: Context, requestCode: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, MedicationAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
    }

    private fun setAlarm(context: Context, triggerAtMillis: Long, requestCode: Int, intent: Intent) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (canScheduleExact(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                return
            } catch (_: SecurityException) {
                // A permissão foi revogada entre a checagem e a chamada: cai no modo inexato.
            }
        }
        // Sem permissão de alarme exato: o lembrete ainda toca, mas pode atrasar alguns minutos.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
    }
}
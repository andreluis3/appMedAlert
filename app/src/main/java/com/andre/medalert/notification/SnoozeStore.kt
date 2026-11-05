package com.andre.medalert.notification

import android.content.Context

/**
 * Guarda os adiamentos pendentes (um por medicamento) para que sobrevivam a um reboot:
 * o AlarmManager perde todos os alarmes quando o aparelho reinicia.
 *
 * Valor salvo por medicamento: "horárioOriginalDaDose|horárioDoDisparo" (epoch millis).
 * O adiamento NÃO escreve nada no histórico.
 */
object SnoozeStore {

    const val DEFAULT_SNOOZE_MINUTES = 5

    private const val PREFS = "medalert_snooze_store"
    private const val SEPARATOR = "|"

    /** Reagenda a mesma dose para daqui a [minutes] min (substitui um adiamento anterior). */
    fun snooze(
        context: Context,
        localId: Long,
        scheduledAtMillis: Long,
        minutes: Int = DEFAULT_SNOOZE_MINUTES
    ): Long {
        val app = context.applicationContext
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        write(app, localId, scheduledAtMillis, triggerAt)
        AlarmScheduler.scheduleSnooze(app, localId, scheduledAtMillis, triggerAt)
        return triggerAt
    }

    /** Cancela o alarme do adiamento e apaga o registro. */
    fun cancel(context: Context, localId: Long) {
        val app = context.applicationContext
        AlarmScheduler.cancelSnooze(app, localId)
        clear(app, localId)
    }

    /** Só apaga o registro (o alarme já disparou). */
    fun clear(context: Context, localId: Long) {
        prefs(context).edit().remove(localId.toString()).commit()
    }

    /** Reagenda os adiamentos guardados (boot, atualização do app, troca de fuso). */
    fun restoreAll(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        prefs(app).all.forEach { (key, value) ->
            val localId = key.toLongOrNull() ?: return@forEach
            val parts = (value as? String)?.split(SEPARATOR) ?: return@forEach
            val scheduledAt = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
            val triggerAt = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            // Se o horário já passou durante o reboot, dispara logo em seguida.
            AlarmScheduler.scheduleSnooze(app, localId, scheduledAt, maxOf(triggerAt, now + 1_000L))
        }
    }

    private fun write(context: Context, localId: Long, scheduledAt: Long, triggerAt: Long) {
        prefs(context).edit()
            .putString(localId.toString(), "$scheduledAt$SEPARATOR$triggerAt")
            .commit()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
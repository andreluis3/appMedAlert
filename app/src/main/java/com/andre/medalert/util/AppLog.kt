package com.andre.medalert.util

import android.util.Log
import com.andre.medalert.BuildConfig

/**
 * Log do app. Só escreve em build de DEBUG e sempre passa por [LogRedactor].
 * Em release não sai nada no Logcat (nem mensagens, nem stack traces), para não expor
 * medicamentos, dados pessoais ou tokens.
 */
object AppLog {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, LogRedactor.redact(message))
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(tag, LogRedactor.redact(message), error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, LogRedactor.redact(message), error)
    }
}

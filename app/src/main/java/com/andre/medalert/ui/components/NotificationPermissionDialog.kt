package com.andre.medalert.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andre.medalert.notification.AlarmScheduler
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary

private const val UI_PREFS = "medalert_permission_ui"
private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

private enum class PermissionDialogType { NONE, NOTIFICATIONS, EXACT_ALARM }

/** Cada pedido aparece uma vez por abertura do app (não a cada volta para a Home). */
private object PromptState {
    @Volatile var notificationsShown = false
    @Volatile var exactAlarmShown = false
}

/**
 * Popup (no lugar do antigo ReminderPermissionCard) que aparece ao entrar no app:
 * 1) pede a permissão de notificações (Android 13+) explicando o motivo;
 * 2) depois, se faltar, pede a permissão de alarmes exatos (os avisos podem atrasar sem ela).
 * [refreshKey] muda quando o usuário volta das Configurações.
 */
@Composable
fun NotificationPermissionDialog(refreshKey: Int) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }

    var notificationsOn by remember(refreshKey) {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val exactOn = remember(refreshKey) { AlarmScheduler.canScheduleExact(context) }
    var dialog by remember { mutableStateOf(PermissionDialogType.NONE) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    LaunchedEffect(refreshKey, notificationsOn, exactOn) {
        when {
            !notificationsOn -> if (!PromptState.notificationsShown) {
                PromptState.notificationsShown = true
                dialog = PermissionDialogType.NOTIFICATIONS
            }

            !exactOn -> if (!PromptState.exactAlarmShown) {
                PromptState.exactAlarmShown = true
                dialog = PermissionDialogType.EXACT_ALARM
            }
        }
    }

    when (dialog) {
        PermissionDialogType.NOTIFICATIONS -> AlertDialog(
            onDismissRequest = { dialog = PermissionDialogType.NONE },
            containerColor = MedAlertCard,
            title = {
                Text("Ativar lembretes?", color = MedAlertTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "O MedAlert precisa enviar notificações para avisar a hora de cada medicamento. " +
                            "Você poderá tomar a dose ou adiar direto pela notificação.",
                    color = MedAlertTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = PermissionDialogType.NONE
                    val runtimeMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                    val alreadyAsked = uiPrefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)

                    if (runtimeMissing && !alreadyAsked) {
                        uiPrefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Já negou antes (o sistema não mostra mais o pedido): leva às configurações.
                        openNotificationSettings(context)
                    }
                }) { Text("Ativar", color = MedAlertPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = PermissionDialogType.NONE }) {
                    Text("Agora não", color = MedAlertTextSecondary)
                }
            }
        )

        PermissionDialogType.EXACT_ALARM -> AlertDialog(
            onDismissRequest = { dialog = PermissionDialogType.NONE },
            containerColor = MedAlertCard,
            title = {
                Text("Permitir alarmes exatos?", color = MedAlertTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Sem essa permissão, os avisos podem atrasar alguns minutos.",
                    color = MedAlertTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = PermissionDialogType.NONE
                    openExactAlarmSettings(context)
                }) { Text("Permitir", color = MedAlertPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = PermissionDialogType.NONE }) {
                    Text("Agora não", color = MedAlertTextSecondary)
                }
            }
        )

        PermissionDialogType.NONE -> Unit
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}
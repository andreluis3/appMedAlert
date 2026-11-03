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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andre.medalert.notification.AlarmScheduler
import com.andre.medalert.ui.theme.MedAlertBackground
import com.andre.medalert.ui.theme.MedAlertCard
import com.andre.medalert.ui.theme.MedAlertPrimary
import com.andre.medalert.ui.theme.MedAlertStatusOrange
import com.andre.medalert.ui.theme.MedAlertTextPrimary
import com.andre.medalert.ui.theme.MedAlertTextSecondary

private const val UI_PREFS = "medalert_permission_ui"
private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

/**
 * Pede POST_NOTIFICATIONS (Android 13+) explicando o motivo, e avisa quando as notificações ou os
 * alarmes exatos estão desativados. [refreshKey] muda quando o usuário volta das Configurações.
 */
@Composable
fun ReminderPermissionCard(refreshKey: Int) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }

    var notificationsOn by remember(refreshKey) {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val exactOn = remember(refreshKey) { AlarmScheduler.canScheduleExact(context) }
    var showRationale by remember { mutableStateOf(false) }

    val runtimePermissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Momento adequado: primeira vez que a Home aparece com a permissão pendente.
    LaunchedEffect(refreshKey) {
        if (runtimePermissionMissing && !uiPrefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) {
            showRationale = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            containerColor = MedAlertCard,
            title = {
                Text("Ativar lembretes?", color = MedAlertTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "O MedAlert precisa enviar notificações para avisar a hora de cada medicamento.",
                    color = MedAlertTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    uiPrefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("Ativar", color = MedAlertPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    uiPrefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
                }) { Text("Agora não", color = MedAlertTextSecondary) }
            }
        )
    }

    if (!notificationsOn) {
        PermissionBanner(
            title = "Lembretes desativados",
            message = "Sem notificações, o MedAlert não consegue avisar a hora dos seus medicamentos.",
            buttonLabel = if (runtimePermissionMissing &&
                !uiPrefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
            ) "Ativar notificações" else "Abrir configurações",
            onClick = {
                if (runtimePermissionMissing &&
                    !uiPrefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
                ) {
                    showRationale = true
                } else {
                    openNotificationSettings(context)
                }
            }
        )
    } else if (!exactOn) {
        PermissionBanner(
            title = "Permita alarmes exatos",
            message = "Sem essa permissão, os avisos podem atrasar alguns minutos.",
            buttonLabel = "Permitir",
            onClick = { openExactAlarmSettings(context) }
        )
    }
}

@Composable
private fun PermissionBanner(
    title: String,
    message: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(MedAlertStatusOrange.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(title, color = MedAlertTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(message, color = MedAlertTextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MedAlertPrimary,
                contentColor = MedAlertBackground
            )
        ) { Text(buttonLabel, fontWeight = FontWeight.Bold) }
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
package com.andre.medalert.fcm

import com.andre.medalert.sync.MedicationSyncWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Entrada do FCM. Mensagens e tokens apenas SINALIZAM: o trabalho real (enviar o token à API,
 * baixar medicamentos e histórico, atualizar o Room) acontece no [MedicationSyncWorker].
 *
 * Observação para o backend: só mensagens de dados (sem o bloco "notification") chegam aqui com
 * o app em segundo plano ou fechado. Com "notification", o sistema mostra o aviso sozinho e este
 * serviço não é chamado, então a sincronização não dispara.
 */
class MedAlertMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // O worker confere a sessão e envia o token novo (só cuidador tem endpoint hoje).
        MedicationSyncWorker.enqueueFollowUp(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        RemoteEventHandler.handle(
            context = applicationContext,
            data = message.data,
            notifTitle = message.notification?.title,
            notifBody = message.notification?.body
        )
    }
}

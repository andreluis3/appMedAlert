package com.andre.medalert.data.mock

import com.andre.medalert.data.model.NotificationType
import com.andre.medalert.data.model.NotificationUiModel

/** Lista inicial usada pelo MockNotificationRepository. */
val mockNotificacoesIniciais = listOf(
    NotificationUiModel(
        id = 1,
        titulo = "Medicamento não tomado",
        descricao = "Você não confirmou a dose de Losartana 50mg.",
        horario = "Hoje - 08:15",
        tipo = NotificationType.MEDICATION_MISSED,
        lida = false,
        medicationName = "Losartana 50mg",
        scheduledTime = "08:00"
    ),
    NotificationUiModel(
        id = 2,
        titulo = "Dose atrasada",
        descricao = "Você ainda não confirmou Dipirona 500mg.",
        horario = "Hoje - 14:30",
        tipo = NotificationType.MEDICATION_DELAYED,
        lida = false,
        medicationName = "Dipirona 500mg",
        scheduledTime = "14:00"
    ),
    NotificationUiModel(
        id = 3,
        titulo = "Hora do medicamento",
        descricao = "Está na hora de tomar Omeprazol 20mg.",
        horario = "Hoje - 07:00",
        tipo = NotificationType.MEDICATION_REMINDER,
        lida = true,
        medicationName = "Omeprazol 20mg",
        scheduledTime = "07:00"
    ),
    NotificationUiModel(
        id = 4,
        titulo = "Dose não registrada",
        descricao = "A dose de Paracetamol das 20:00 não foi confirmada.",
        horario = "Ontem",
        tipo = NotificationType.MEDICATION_MISSED,
        lida = true,
        medicationName = "Paracetamol",
        scheduledTime = "20:00"
    ),
    NotificationUiModel(
        id = 5,
        titulo = "Novo acompanhante",
        descricao = "Maria agora acompanha seu tratamento.",
        horario = "Ontem",
        tipo = NotificationType.RESPONSIBLE_ADDED,
        lida = true
    ),
    NotificationUiModel(
        id = 6,
        titulo = "Novo medicamento cadastrado",
        descricao = "Losartana 50mg foi adicionada ao seu tratamento.",
        horario = "Ontem",
        tipo = NotificationType.NEW_MEDICATION,
        lida = true
    ),
    NotificationUiModel(
        id = 7,
        titulo = "Seu resumo semanal está disponível",
        descricao = "Você tomou 85% das doses previstas nesta semana.",
        horario = "Há 2 dias",
        tipo = NotificationType.ADHERENCE_SUMMARY,
        lida = true
    )
)
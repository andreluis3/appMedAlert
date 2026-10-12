package com.andre.medalert.data.mock

import com.andre.medalert.data.remote.dto.AdesaoDiaDto
import com.andre.medalert.data.remote.dto.AdesaoRemedioDto
import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.remote.dto.HorarioEsquecidoDto

/**
 * Mock com a mesma estrutura (e o mesmo exemplo) da documentação de
 * GET /api/historys/dashboard?id=userId.
 */
val mockDashboardResponse = DashboardResponse(
    adesao = "23.6",
    dosesEsp = 416,
    dosesTom = 98,
    dosesAtr = 64,
    dosesNT = 248,
    adesaoD = listOf(
        AdesaoDiaDto(adesao = "46.2", diaSem = "domingo"),
        AdesaoDiaDto(adesao = "50.0", diaSem = "segunda-feira"),
        AdesaoDiaDto(adesao = "42.3", diaSem = "terça-feira"),
        AdesaoDiaDto(adesao = "42.3", diaSem = "quarta-feira"),
        AdesaoDiaDto(adesao = "23.1", diaSem = "quinta-feira"),
        AdesaoDiaDto(adesao = "0.0", diaSem = "sexta-feira"),
        AdesaoDiaDto(adesao = "0.0", diaSem = "sábado")
    ),
    adesaoR = listOf(
        AdesaoRemedioDto(remedio = "Dipirona", dosesEsp = 126, dosesTom = 28, adesao = "22"),
        AdesaoRemedioDto(remedio = "Paracetamol", dosesEsp = 95, dosesTom = 21, adesao = "22"),
        AdesaoRemedioDto(remedio = "Amoxicilina", dosesEsp = 63, dosesTom = 16, adesao = "25"),
        AdesaoRemedioDto(remedio = "Omeprazol", dosesEsp = 32, dosesTom = 4, adesao = "13"),
        AdesaoRemedioDto(remedio = "Ibuprofeno", dosesEsp = 94, dosesTom = 29, adesao = "31")
    ),
    horariosEsq = listOf(
        HorarioEsquecidoDto(horario = "06:00", qtd = 7, porcentagem = "50.0"),
        HorarioEsquecidoDto(horario = "07:00", qtd = 5, porcentagem = "35.7"),
        HorarioEsquecidoDto(horario = "11:00", qtd = 5, porcentagem = "35.7"),
        HorarioEsquecidoDto(horario = "15:00", qtd = 7, porcentagem = "50.0"),
        HorarioEsquecidoDto(horario = "17:00", qtd = 7, porcentagem = "50.0"),
        HorarioEsquecidoDto(horario = "18:00", qtd = 5, porcentagem = "35.7")
    )
)

/** Usuário novo, sem nenhuma dose esperada ainda. Serve para testar o estado "sem dados". */
val mockDashboardEmpty = DashboardResponse(
    adesao = "0",
    dosesEsp = 0,
    dosesTom = 0,
    dosesAtr = 0,
    dosesNT = 0,
    adesaoD = emptyList(),
    adesaoR = emptyList(),
    horariosEsq = emptyList()
)
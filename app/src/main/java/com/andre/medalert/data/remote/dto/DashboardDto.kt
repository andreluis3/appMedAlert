package com.andre.medalert.data.remote.dto

data class AdesaoDiaDto(
    val adesao: String,
    val diaSem: String
)

data class AdesaoRemedioDto(
    val remedio: String,
    val dosesEsp: Int,
    val dosesTom: Int,
    val adesao: String
)

data class HorarioEsquecidoDto(
    val horario: String,
    val qtd: Int,
    val porcentagem: String
)

data class DashboardResponse(
    val adesao: String,
    val dosesEsp: Int,
    val dosesTom: Int,
    val dosesAtr: Int,
    val dosesNT: Int,
    val adesaoD: List<AdesaoDiaDto>,
    val adesaoR: List<AdesaoRemedioDto>,
    val horariosEsq: List<HorarioEsquecidoDto>
)
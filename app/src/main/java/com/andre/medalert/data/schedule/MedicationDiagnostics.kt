package com.andre.medalert.data.schedule

import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.local.entity.horariosLista
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * DIAGNÓSTICO (temporário) de por que a Home mostra ou descarta um medicamento.
 * Sem dependência de Android: testável em JVM. Não altera nenhum dado.
 *
 * O motivo vem de [DoseScheduleCalculator.diagnose], que é a MESMA decisão que o cálculo real
 * toma, então o relatório nunca diverge do que a Home faz.
 */
data class MedicationDiagnosis(
    val nome: String,
    val idLocal: Long,
    val remoteId: Int?,
    val userId: Int,
    val ativo: Boolean,
    val sincronizado: Boolean,
    val dataInicio: String?,
    val dataFim: String?,
    val horarioDose: String?,
    val frequencia: Int?,
    /** Coluna "horarios" do Room (o que veio em doseDiaria). */
    val horariosArmazenados: List<String>,
    /** O que a tela Remédios exibe (withServerHorarios). Só exibição: não vai para o Room. */
    val horariosExibidosNaTelaRemedios: List<String>,
    /** Horários das doses que o cálculo da Home gera no dia. */
    val horariosCalculados: List<String>,
    val dosesPrevistasNoDia: Int,
    val plano: String,
    val observacoes: List<String>,
    /** Preenchido quando o medicamento NÃO gera doses no dia. */
    val motivoDescartado: String?
)

object MedicationDiagnostics {

    fun inspect(med: MedicationEntity, day: LocalDate): MedicationDiagnosis {
        val plano = DoseScheduleCalculator.diagnose(med)
        val doses = DoseScheduleCalculator.dosesOnDay(listOf(med), day)

        val motivo = if (doses.isNotEmpty()) null else plano.motivo ?: motivoNoDia(med, day)

        return MedicationDiagnosis(
            nome = med.nome,
            idLocal = med.id,
            remoteId = med.remoteId,
            userId = med.userId,
            ativo = med.ativo,
            sincronizado = med.isSynced,
            dataInicio = med.dataInicio,
            dataFim = med.dataFim,
            horarioDose = med.horarioDose,
            frequencia = med.frequencia,
            horariosArmazenados = med.horariosLista(),
            horariosExibidosNaTelaRemedios = med.withServerHorarios().horariosLista(),
            horariosCalculados = doses.map { "%02d:%02d".format(it.at.hour, it.at.minute) },
            dosesPrevistasNoDia = doses.size,
            plano = plano.plano,
            observacoes = plano.observacoes,
            motivoDescartado = motivo
        )
    }

    /** Há plano, mas nenhuma dose cai no dia: encerrado, ainda não começou ou sem horário no dia. */
    private fun motivoNoDia(med: MedicationEntity, day: LocalDate): String {
        val fim = parseDay(med.dataFim)
        if (fim != null && day.isAfter(fim)) return "tratamento encerrado em $fim (dataFim anterior a $day)"
        val inicio = parseDay(med.dataInicio)
        if (inicio != null && day.isBefore(inicio)) return "tratamento só começa em $inicio"
        return "há plano, mas nenhuma dose cai em $day"
    }

    private fun parseDay(raw: String?): LocalDate? =
        try {
            raw?.trim()?.take(10)?.let { LocalDate.parse(it) }
        } catch (_: DateTimeParseException) {
            null
        }

    /** Bloco de texto de UM medicamento, no formato pedido. */
    fun format(d: MedicationDiagnosis): String = buildString {
        appendLine("Nome: ${d.nome}")
        appendLine("ID local: ${d.idLocal}")
        appendLine("Remote ID: ${d.remoteId}")
        appendLine("User ID: ${d.userId}")
        appendLine("Ativo: ${d.ativo}")
        appendLine("Sincronizado: ${d.sincronizado}")
        appendLine("Data início: ${d.dataInicio}")
        appendLine("Data fim: ${d.dataFim}")
        appendLine("Horário da primeira dose: ${d.horarioDose}")
        appendLine("Frequência: ${d.frequencia}")
        appendLine("Horários armazenados: ${d.horariosArmazenados}")
        appendLine("Horários exibidos na tela Remédios: ${d.horariosExibidosNaTelaRemedios}")
        appendLine("Horários calculados: ${d.horariosCalculados}")
        appendLine("Quantidade de doses previstas hoje: ${d.dosesPrevistasNoDia}")
        appendLine("Plano usado pelo cálculo: ${d.plano}")
        if (d.observacoes.isNotEmpty()) appendLine("Observações: ${d.observacoes.joinToString("; ")}")
        append("Motivo pelo qual foi descartado: ${d.motivoDescartado ?: "não foi descartado"}")
    }

    /**
     * Relatório completo: comparação Home x Remédios e um bloco por medicamento.
     *
     * @param patientIdRemedios paciente que a tela Remédios recebe (PatientContext)
     * @param patientIdHome paciente que a Home observa (SessionMedications)
     * @param roomPorUsuario contagem de linhas do Room por userId (TODOS os usuários)
     * @param medicamentosRemedios linhas que a tela Remédios observa (dao.observeByUser)
     * @param medicamentosHome linhas que a Home recebeu
     */
    fun report(
        day: LocalDate,
        patientIdRemedios: Int?,
        patientIdHome: Int?,
        roomPorUsuario: Map<Int, Int>,
        medicamentosRemedios: List<MedicationEntity>,
        medicamentosHome: List<MedicationEntity>
    ): String {
        val idsRemedios = medicamentosRemedios.map { it.id }.toSet()
        val idsHome = medicamentosHome.map { it.id }.toSet()
        val ativos = medicamentosHome.filter { it.ativo }
        val inspecoes = medicamentosHome.map { inspect(it, day) }
        val geramDoses = inspecoes.count { it.dosesPrevistasNoDia > 0 }

        return buildString {
            appendLine("===== DIAGNÓSTICO DE MEDICAMENTOS ($day) =====")
            appendLine("PATIENT ID DA TELA REMÉDIOS: $patientIdRemedios")
            appendLine("PATIENT ID DA HOME: $patientIdHome")
            appendLine("Mesmo paciente nas duas telas: ${patientIdRemedios == patientIdHome}")
            appendLine("QUANTIDADE DE MEDICAMENTOS NO ROOM (todos os usuários): ${roomPorUsuario.values.sum()} | por userId: $roomPorUsuario")
            appendLine("Medicamentos que a tela Remédios observa: ${medicamentosRemedios.size}")
            appendLine("Medicamentos que a Home observa: ${medicamentosHome.size}")
            appendLine("Ids só na Remédios: ${(idsRemedios - idsHome).sorted()} | só na Home: ${(idsHome - idsRemedios).sorted()}")
            appendLine("QUANTIDADE DE MEDICAMENTOS ATIVOS: ${ativos.size}")
            appendLine("QUANTIDADE DE MEDICAMENTOS QUE GERAM DOSES HOJE: $geramDoses")
            medicamentosHome.forEachIndexed { i, _ ->
                appendLine("----- medicamento ${i + 1} de ${medicamentosHome.size} -----")
                appendLine(format(inspecoes[i]))
            }
            append("===== FIM DO DIAGNÓSTICO =====")
        }
    }
}

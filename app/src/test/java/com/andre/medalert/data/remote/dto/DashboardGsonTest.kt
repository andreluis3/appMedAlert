package com.andre.medalert.data.remote

import com.andre.medalert.data.remote.dto.DashboardResponse
import com.andre.medalert.data.remote.dto.parsePercentOrNull
import com.andre.medalert.data.remote.dto.validationProblem
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desserialização REAL com Gson (o mesmo conversor do Retrofit) do JSON do endpoint do cuidador. */
class DashboardGsonTest {

    private val gson = Gson()
    private fun parse(json: String): DashboardResponse = gson.fromJson(json, DashboardResponse::class.java)

    /** Resposta real de GET /api/historys/dashboard/cuid/1, como o Kayky enviou. */
    private val respostaDoCuidador = """
    {
      "adesao": 1.6, "dosesEsp": 186, "dosesTom": 3, "dosesAtr": 3, "dosesNT": 183,
      "adesaoD": [
        {"adesao": "0.0", "diaSem": "domingo"}, {"adesao": "0.0", "diaSem": "segunda-feira"},
        {"adesao": "0.0", "diaSem": "terça-feira"}, {"adesao": "33.3", "diaSem": "quarta-feira"},
        {"adesao": "0.0", "diaSem": "quinta-feira"}, {"adesao": "16.7", "diaSem": "sexta-feira"},
        {"adesao": "0.0", "diaSem": "sábado"}
      ],
      "adesaoR": [
        {"remedio": "teste", "dosesEsp": 93, "dosesTom": 2, "adesao": "2"},
        {"remedio": "teste_user1", "dosesEsp": 93, "dosesTom": 1, "adesao": "1"}
      ],
      "horariosEsq": [
        {"horario": "03:00", "qtd": 14, "porcentgem": "100.0"},
        {"horario": "11:00", "qtd": 13, "porcentgem": "92.9"},
        {"horario": "19:00", "qtd": 12, "porcentgem": "85.7"}
      ]
    }
    """.trimIndent()

    @Test
    fun respostaRealDoCuidador_adesaoNumerica_viraTextoELegivel() {
        val r = parse(respostaDoCuidador)
        assertEquals("1.6", r.adesao)
        assertEquals(1.6f, parsePercentOrNull(r.adesao)!!, 0.0001f)
        assertEquals(186, r.dosesEsp)
        assertEquals(3, r.dosesTom)
        assertEquals(3, r.dosesAtr)
        assertEquals(183, r.dosesNT)
        assertNull(r.validationProblem())
    }

    @Test
    fun respostaRealDoCuidador_listasCompletas() {
        val r = parse(respostaDoCuidador)
        assertEquals(7, r.adesaoD.size)
        assertEquals("quarta-feira", r.adesaoD[3].diaSem)
        assertEquals("33.3", r.adesaoD[3].adesao)
        assertEquals(2, r.adesaoR.size)
        assertEquals("teste", r.adesaoR[0].remedio)
        assertEquals(93, r.adesaoR[0].dosesEsp)
        assertEquals("2", r.adesaoR[0].adesao)
    }

    @Test
    fun campoPorcentgem_comAGrafiaDoBackend_eLido() {
        val r = parse(respostaDoCuidador)
        assertEquals(3, r.horariosEsq.size)
        assertEquals("03:00", r.horariosEsq[0].horario)
        assertEquals(14, r.horariosEsq[0].qtd)
        assertEquals("100.0", r.horariosEsq[0].porcentagem)
        assertEquals("85.7", r.horariosEsq[2].porcentagem)
    }

    @Test
    fun campoPorcentagem_seOBackendCorrigirAGrafia_tambemFunciona() {
        val r = parse("""{"adesao":"50","dosesEsp":2,"dosesTom":1,"dosesAtr":0,"dosesNT":1,
            "adesaoD":[],"adesaoR":[],"horariosEsq":[{"horario":"08:00","qtd":1,"porcentagem":"100.0"}]}""")
        assertEquals("100.0", r.horariosEsq.single().porcentagem)
    }

    @Test
    fun adesaoEmString_continuaFuncionando() {
        val r = parse("""{"adesao":"23.6","dosesEsp":10,"dosesTom":2,"dosesAtr":0,"dosesNT":8,"adesaoD":[],"adesaoR":[],"horariosEsq":[]}""")
        assertEquals("23.6", r.adesao)
        assertEquals(23.6f, parsePercentOrNull(r.adesao)!!, 0.0001f)
        assertNull(r.validationProblem())
    }

    @Test
    fun adesaoInteira_numeroSemPontoDecimal() {
        val r = parse("""{"adesao":50,"dosesEsp":2,"dosesTom":1,"dosesAtr":0,"dosesNT":1,"adesaoD":[],"adesaoR":[],"horariosEsq":[]}""")
        assertEquals("50", r.adesao)
        assertEquals(50f, parsePercentOrNull(r.adesao)!!, 0.0001f)
    }

    @Test
    fun percentualDeItemDeLista_podeVirNumero() {
        val r = parse("""{"adesao":10,"dosesEsp":5,"dosesTom":1,"dosesAtr":0,"dosesNT":4,
            "adesaoD":[{"adesao":33.3,"diaSem":"quarta-feira"}],
            "adesaoR":[{"remedio":"x","dosesEsp":5,"dosesTom":1,"adesao":20}],
            "horariosEsq":[{"horario":"08:00","qtd":2,"porcentgem":50}]}""")
        assertEquals("33.3", r.adesaoD.single().adesao)
        assertEquals("20", r.adesaoR.single().adesao)
        assertEquals("50", r.horariosEsq.single().porcentagem)
    }

    @Test
    fun listasVazias_comDosesEsperadas_saoValidas() {
        val r = parse("""{"adesao":"0.0","dosesEsp":4,"dosesTom":0,"dosesAtr":0,"dosesNT":4,"adesaoD":[],"adesaoR":[],"horariosEsq":[]}""")
        assertTrue(r.adesaoD.isEmpty() && r.adesaoR.isEmpty() && r.horariosEsq.isEmpty())
        assertNull(r.validationProblem())
    }

    @Test
    fun semDosesEsperadas_naoValidaORestante() {
        val r = parse("""{"adesao":"0.0","dosesEsp":0,"dosesTom":0,"dosesAtr":0,"dosesNT":0,"adesaoD":[],"adesaoR":[],"horariosEsq":[]}""")
        assertNull(r.validationProblem())
        assertEquals(0, r.dosesEsp)
    }

    @Test
    fun respostaIncompleta_listaAusente_eInvalida() {
        val r = parse("""{"adesao":"10","dosesEsp":5,"dosesTom":1,"dosesAtr":0,"dosesNT":4}""")
        assertNotNull(r.validationProblem())
    }

    @Test
    fun adesaoGeralIlegivel_eInvalida() {
        val r = parse("""{"adesao":"abc","dosesEsp":5,"dosesTom":1,"dosesAtr":0,"dosesNT":4,"adesaoD":[],"adesaoR":[],"horariosEsq":[]}""")
        assertEquals("adesao geral ausente ou ilegível", r.validationProblem())
    }

    @Test(expected = JsonSyntaxException::class)
    fun respostaQueNaoEUmObjeto_lancaErro() {
        parse("[]")
    }

    @Test
    fun oMesmoDtoServeAosDoisEndpoints_semCamposExtrasQuebrarem() {
        // Campo novo do servidor que o app não conhece não pode quebrar a leitura.
        val r = parse("""{"adesao":"10","dosesEsp":5,"dosesTom":1,"dosesAtr":0,"dosesNT":4,"campoNovo":"x",
            "adesaoD":[],"adesaoR":[],"horariosEsq":[]}""")
        assertEquals(5, r.dosesEsp)
    }
}

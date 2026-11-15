package com.andre.medalert.data.remote.dto

import com.andre.medalert.data.mapper.toEntity
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CuidadorMedicationResponseTest {

    private val gson = Gson()

    private val json = """
        {
          "usuarios": {
            "nome": "Usuario Teste domingo",
            "medicamentos": [
              {
                "id": 1,
                "nome": "teste",
                "dosagem": "50",
                "unidade_dosagem": "mg",
                "horario_dose": "1970-01-01T11:00:00.000Z",
                "frequencia": 8
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `le o nome do paciente e os medicamentos`() {
        val response = gson.fromJson(json, CuidadorMedicationResponse::class.java)

        assertEquals("Usuario Teste domingo", response.usuarios?.nome)
        val meds = response.usuarios?.medicamentos
        assertNotNull(meds)
        assertEquals(1, meds!!.size)
        assertEquals(1, meds[0].id)
        assertEquals("50", meds[0].dosagem)
        assertEquals("mg", meds[0].unidadeDosagem)
        assertEquals(8, meds[0].frequencia)
    }

    @Test
    fun `medicamento do cuidador vira linha do PACIENTE e sincronizada`() {
        val item = gson.fromJson(json, CuidadorMedicationResponse::class.java)
            .usuarios!!.medicamentos!![0]

        val entity = item.toEntity(userId = 7) // 7 = id do paciente, nunca o do cuidador

        assertEquals(7, entity.userId)
        assertEquals(1, entity.remoteId)
        assertTrue(entity.isSynced)
        assertEquals("50 mg", entity.dosagem)
        assertEquals(50.0, entity.dosagemValor!!, 0.0)
        assertTrue(entity.ativo) // a API não mandou "ativo": padrão ativo
    }

    @Test
    fun `resposta sem usuarios chega como nula`() {
        val response = gson.fromJson("{}", CuidadorMedicationResponse::class.java)
        assertNull(response.usuarios)
    }

    @Test
    fun `payload de cadastro nao leva estoque`() {
        val body = gson.toJson(
            MedicamentoData(
                nome = "teste",
                dataInicio = "2026-11-14T00:00:00.000Z",
                dosagem = 50.0,
                frequencia = 8,
                quantidade = 1,
                unidadeDosagem = "mg",
                unidadeQuantidade = "comprimido",
                userId = 7,
                horarioDose = "08:00"
            )
        )
        assertFalse(body.contains("estoque"))
    }
}
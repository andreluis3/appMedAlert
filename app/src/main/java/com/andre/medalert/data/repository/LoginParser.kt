package com.andre.medalert.data.repository

import com.andre.medalert.data.remote.dto.LoginResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Interpretação TOLERANTE das respostas de login/cadastro da API (texto puro ou JSON).
 * Fica fora do UserRepository para ser testada em JVM, sem rede e sem Android.
 *
 * Formatos aceitos no login:
 *  - número puro: `5` ou `"5"` (versão antiga) -> paciente;
 *  - `{"id":1,"tipo":"paciente"}`;
 *  - `{"id":3,"tipo":"cuidador","pacienteId":1}` (também paciente_id / pacientId);
 *  - versões antigas com `userId` em vez de `id`.
 * Sem id > 0 -> null (credenciais recusadas).
 */
internal object LoginParser {

    private val gson = Gson()

    /** Devolve o LoginResponse se houver um id > 0; senão null. */
    fun parseLogin(raw: String): LoginResponse? {
        val puro = raw.trim().trim('"').toIntOrNull()
        if (puro != null) {
            return if (puro > 0) LoginResponse(id = puro, tipo = "paciente") else null
        }
        val obj = asJsonObject(raw) ?: return null
        val parsed = try {
            gson.fromJson(obj, LoginResponse::class.java)
        } catch (e: Exception) {
            null
        } ?: return null
        // Versões antigas mandavam "userId" em vez de "id".
        val id = parsed.id ?: obj.get("userId")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
        return if (id != null && id > 0) parsed.copy(id = id) else null
    }

    /** Aceita "4", "\"4\"" ou {"id": 4} / {"userId": 4}. Devolve null se não achar ID. */
    fun extractId(raw: String): Int? {
        raw.trim().trim('"').toIntOrNull()?.let { return it }
        val obj = asJsonObject(raw) ?: return null
        return listOf("id", "userId").firstNotNullOfOrNull { key ->
            obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
        }
    }

    /** Pega o "message" de {"message": "..."}, se existir. */
    fun extractMessage(raw: String): String? {
        val obj = asJsonObject(raw) ?: return null
        return obj.get("message")?.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun asJsonObject(raw: String): JsonObject? {
        return try {
            JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        }
    }
}

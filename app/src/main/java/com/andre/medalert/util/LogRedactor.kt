package com.andre.medalert.util

/**
 * Remove de um texto de log os campos que identificam a pessoa ou revelam o tratamento.
 * Sem dependência de Android (testável em JVM).
 *
 * Mascara o VALOR (entre aspas) das chaves JSON: token, senha, email, nome, telefone,
 * parentesco e observacoes. Ids, códigos HTTP, datas e frequências continuam visíveis,
 * que é o que ajuda a depurar a integração.
 */
object LogRedactor {

    private val SENSITIVE_KEYS = listOf(
        "token", "senha", "email", "nome", "telefone", "parentesco", "observacoes"
    )

    private val JSON_FIELD = Regex(
        "(\"(?:${SENSITIVE_KEYS.joinToString("|")})\"\\s*:\\s*\")((?:[^\"\\\\]|\\\\.)*)(\")",
        RegexOption.IGNORE_CASE
    )

    private val BEARER = Regex("(Bearer\\s+)[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE)

    fun redact(text: String): String =
        BEARER.replace(JSON_FIELD.replace(text) { "${it.groupValues[1]}***${it.groupValues[3]}" }) {
            "${it.groupValues[1]}***"
        }
}

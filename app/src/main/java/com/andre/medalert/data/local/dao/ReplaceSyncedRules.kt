package com.andre.medalert.data.local.dao

/**
 * Regras puras (sem Room, testáveis em JVM) de quais medicamentos sincronizados o
 * [MedicationDao.replaceSynced] remove depois de ler a lista da API.
 */
object ReplaceSyncedRules {

    /** Linha sincronizada que o Room já tem. */
    data class ExistingRow(val id: Long, val remoteId: Int?)

    /**
     * Ids locais que podem ser removidos.
     *
     * Só sai uma linha que NÃO foi mantida pela resposta ([keepIds]) E que:
     * - é sobra duplicada de um remoteId que a API devolveu (continua sendo limpa); ou
     * - tem a ausência CONFIRMADA ([confirmedAbsentRemoteIds], decidido por
     *   [com.andre.medalert.data.repository.SyncAbsencePolicy]).
     *
     * Quem não está nessas duas condições fica: resposta vazia ou parcial nunca apaga sozinha
     * (ela só abre a quarentena). Linhas sincronizadas sem remoteId também ficam: não há como
     * saber se a API ainda as conhece.
     */
    fun idsToDelete(
        existing: List<ExistingRow>,
        keepIds: Set<Long>,
        incomingRemoteIds: Set<Int>,
        confirmedAbsentRemoteIds: Set<Int>
    ): List<Long> =
        existing
            .filter { it.id !in keepIds }
            .filter { row ->
                val remoteId = row.remoteId ?: return@filter false
                remoteId in incomingRemoteIds || remoteId in confirmedAbsentRemoteIds
            }
            .map { it.id }
}

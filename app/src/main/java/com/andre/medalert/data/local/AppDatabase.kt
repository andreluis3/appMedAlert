package com.andre.medalert.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.andre.medalert.data.local.dao.DoseHistoryDao
import com.andre.medalert.data.local.dao.MedicationDao
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.MedicationEntity

@Database(
    entities = [
        MedicationEntity::class,
        DoseHistoryEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao

    abstract fun doseHistoryDao(): DoseHistoryDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 2 -> 3:
         * Adiciona estoque, estoqueMinimo, observacoes e ativo.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE medications ADD COLUMN estoque INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE medications ADD COLUMN estoqueMinimo INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE medications ADD COLUMN observacoes TEXT"
                )
                db.execSQL(
                    "ALTER TABLE medications ADD COLUMN ativo INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * 3 -> 4:
         * Adiciona o horário inicial da dose.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE medications ADD COLUMN horario_dose TEXT"
                )
            }
        }

        /**
         * 4 -> 5:
         * dosagemValor passa de INTEGER (Int) para REAL (Double), para não perder 0.5 / 2.5.
         *
         * O SQLite não altera o tipo de uma coluna, então a tabela é recriada:
         * 1. cria medications_new com o esquema exato que o Room espera para a nova entity;
         * 2. copia TODAS as linhas mantendo o mesmo id (o ID local não muda) e convertendo
         *    dosagemValor com CAST(... AS REAL) (50 vira 50.0; NULL continua NULL);
         * 3. apaga a tabela antiga, renomeia a nova e recria o índice de userId.
         * Tudo roda na transação da migration: se algo falhar, o banco antigo fica intacto.
         *
         * Proteção: se alguma outra tabela tiver FOREIGN KEY apontando para medications,
         * o DROP TABLE poderia apagar linhas filhas em cascata. Nesse caso a migration
         * ABORTA antes de mexer em qualquer dado (envie a DoseHistoryEntity para adaptarmos).
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.query(
                    "SELECT name FROM sqlite_master " +
                            "WHERE type = 'table' AND name != 'medications' " +
                            "AND sql LIKE '%REFERENCES%medications%'"
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        throw IllegalStateException(
                            "Migration 4->5 abortada: a tabela '${cursor.getString(0)}' tem " +
                                    "FOREIGN KEY para medications; recriar medications poderia apagar dados."
                        )
                    }
                }

                db.execSQL("DROP TABLE IF EXISTS medications_new")

                db.execSQL(
                    """
                    CREATE TABLE `medications_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `nome` TEXT NOT NULL,
                        `dosagem` TEXT NOT NULL,
                        `horarios` TEXT NOT NULL,
                        `horario_dose` TEXT,
                        `dataInicio` TEXT,
                        `dataFim` TEXT,
                        `dosagemValor` REAL,
                        `frequencia` INTEGER,
                        `quantidade` INTEGER,
                        `unidadeDosagem` TEXT,
                        `unidadeQuantidade` TEXT,
                        `isSynced` INTEGER NOT NULL,
                        `remoteId` INTEGER,
                        `estoque` INTEGER,
                        `estoqueMinimo` INTEGER,
                        `observacoes` TEXT,
                        `ativo` INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `medications_new` (
                        `id`, `userId`, `nome`, `dosagem`, `horarios`, `horario_dose`,
                        `dataInicio`, `dataFim`, `dosagemValor`, `frequencia`, `quantidade`,
                        `unidadeDosagem`, `unidadeQuantidade`, `isSynced`, `remoteId`,
                        `estoque`, `estoqueMinimo`, `observacoes`, `ativo`
                    )
                    SELECT
                        `id`, `userId`, `nome`, `dosagem`, `horarios`, `horario_dose`,
                        `dataInicio`, `dataFim`, CAST(`dosagemValor` AS REAL), `frequencia`, `quantidade`,
                        `unidadeDosagem`, `unidadeQuantidade`, `isSynced`, `remoteId`,
                        `estoque`, `estoqueMinimo`, `observacoes`, `ativo`
                    FROM `medications`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `medications`")
                db.execSQL("ALTER TABLE `medications_new` RENAME TO `medications`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_medications_userId` ON `medications` (`userId`)"
                )
            }
        }

        /**
         * 5 -> 6:
         * dose_history ganha os campos que identificam uma dose registrada pela notificação:
         * medicationLocalId, previstoEm, tomadoEm e dia (todos anuláveis, sem perder nenhuma linha).
         * O índice único (medicationLocalId, previstoEm) impede registro duplicado da mesma dose.
         * Linhas antigas ficam com dia = NULL e não aparecem na consulta do dia atual.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `dose_history` ADD COLUMN `medicationLocalId` INTEGER")
                db.execSQL("ALTER TABLE `dose_history` ADD COLUMN `previstoEm` INTEGER")
                db.execSQL("ALTER TABLE `dose_history` ADD COLUMN `tomadoEm` INTEGER")
                db.execSQL("ALTER TABLE `dose_history` ADD COLUMN `dia` TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_dose_history_medicationLocalId_previstoEm` " +
                            "ON `dose_history` (`medicationLocalId`, `previstoEm`)"
                )
            }
        }

        /**
         * 6 -> 7 (só ADD COLUMN: nenhuma linha é apagada ou reescrita):
         * - medications.estoqueConsumido (unidades descontadas localmente, padrão 0);
         * - medications.acompanhadoDesde (epoch millis; linhas existentes passam a ser
         *   acompanhadas a partir do momento da migração);
         * - dose_history.medicamentoRemovido (0 = medicamento existe; 1 = foi excluído e o
         *   registro da dose foi preservado).
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `medications` ADD COLUMN `estoqueConsumido` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE `medications` ADD COLUMN `acompanhadoDesde` INTEGER")
                db.execSQL(
                    "UPDATE `medications` SET `acompanhadoDesde` = CAST(strftime('%s','now') AS INTEGER) * 1000"
                )
                db.execSQL(
                    "ALTER TABLE `dose_history` ADD COLUMN `medicamentoRemovido` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 7 -> 8 (só ADD COLUMN, UPDATE e CREATE INDEX: nenhuma linha é apagada):
         * - dose_history.clientEventId / origem / registradoPorId: identidade e autoria da administração;
         * - índice (medicamentoId, previstoEm), NÃO único: linhas antigas podem repetir o par e
         *   esta migration não pode apagar registros (a deduplicação é feita no DAO).
         *
         * Administrações que ainda estão pendentes de envio (isSynced = 0) só podem ter sido
         * registradas pelo paciente (cuidador era somente leitura), então recebem
         * origem = "paciente" e um clientEventId novo, estável a partir de agora.
         * Linhas já sincronizadas ficam com origem NULL (autor desconhecido).
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `dose_history` ADD COLUMN `clientEventId` TEXT")
                db.execSQL("ALTER TABLE `dose_history` ADD COLUMN `origem` TEXT")
                db.execSQL("ALTER TABLE `dose_history` ADD COLUMN `registradoPorId` INTEGER")
                db.execSQL(
                    "UPDATE `dose_history` " +
                            "SET `clientEventId` = lower(hex(randomblob(16))), " +
                            "`origem` = 'paciente', `registradoPorId` = `userId` " +
                            "WHERE `isSynced` = 0 AND `medicationLocalId` IS NOT NULL"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_dose_history_medicamentoId_previstoEm` " +
                            "ON `dose_history` (`medicamentoId`, `previstoEm`)"
                )
            }
        }

        /** Todas as migrations, na ordem. Usada pelo app e pelos testes de migração. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8
        )

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medalert.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
    }
}
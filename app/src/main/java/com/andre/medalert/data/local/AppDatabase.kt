package com.andre.medalert.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.andre.medalert.data.local.dao.DoseHistoryDao
import com.andre.medalert.data.local.dao.MedicationDao
import com.andre.medalert.data.local.entity.DoseHistoryEntity
import com.andre.medalert.data.local.entity.MedicationEntity

@Database(
    entities = [MedicationEntity::class, DoseHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun doseHistoryDao(): DoseHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medalert.db"
                )
                    // Fase de desenvolvimento: se mudar o schema, recria o banco.
                    // Antes de publicar, trocar por Migrations de verdade.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
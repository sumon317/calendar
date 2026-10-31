package com.sumon.calc.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CalcDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        private const val DB_NAME = "calc.db"

        @Volatile
        private var instance: CalcDatabase? = null

        fun get(context: Context): CalcDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CalcDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
    }
}

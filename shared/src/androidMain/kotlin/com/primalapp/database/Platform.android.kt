package com.primalapp.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual class PlatformContext(val context: Context)

actual fun createPrimalDatabase(context: PlatformContext): PrimalDatabase {
    return Room.databaseBuilder(context.context, PrimalDatabase::class.java, "primal.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SQLiteConnection) {
                super.onCreate(db)
                seedBosses(db)
            }
        })
        .fallbackToDestructiveMigration(true)
        .build()
}

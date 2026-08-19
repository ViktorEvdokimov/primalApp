package com.primalapp.database

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection

actual fun currentTimeMillis(): Long = (platform.Foundation.NSDate().timeIntervalSince1970 * 1000).toLong()

actual class PlatformContext

actual fun createPrimalDatabase(context: PlatformContext): PrimalDatabase {
    val dbPath = (NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).first() as String) + "/primal.db"
    return Room.databaseBuilder<PrimalDatabase>(name = dbPath)
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

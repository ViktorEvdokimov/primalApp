package com.primalapp.database

import android.content.Context
import androidx.room.Room

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual class PlatformContext(val context: Context)

actual fun createPrimalDatabase(context: PlatformContext): PrimalDatabase {
    return Room.databaseBuilder(context.context, PrimalDatabase::class.java, "primal.db")
        .addMigrations(*ALL_MIGRATIONS)
        .fallbackToDestructiveMigration(true)
        .build()
}

actual fun readBundledText(context: PlatformContext, name: String): String? = try {
    context.context.assets.open(name).use { stream -> stream.readBytes().decodeToString() }
} catch (exception: java.io.IOException) {
    null
}

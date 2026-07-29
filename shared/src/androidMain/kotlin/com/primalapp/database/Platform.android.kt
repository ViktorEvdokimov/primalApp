package com.primalapp.database

import android.content.Context
import androidx.room.Room

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual class PlatformContext(val context: Context)

actual fun createPrimalDatabase(context: PlatformContext): PrimalDatabase {
    return Room.databaseBuilder(context.context, PrimalDatabase::class.java, "primal.db").build()
}

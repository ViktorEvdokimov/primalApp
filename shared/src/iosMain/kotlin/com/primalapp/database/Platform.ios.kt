package com.primalapp.database

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import androidx.room.Room

actual fun currentTimeMillis(): Long = (platform.Foundation.NSDate().timeIntervalSince1970 * 1000).toLong()

actual class PlatformContext

actual fun createPrimalDatabase(context: PlatformContext): PrimalDatabase {
    val dbPath = (NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).first() as String) + "/primal.db"
    return Room.databaseBuilder<PrimalDatabase>(name = dbPath).build()
}

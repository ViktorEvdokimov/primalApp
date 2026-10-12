package com.primalapp.database

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile

actual fun currentTimeMillis(): Long = (platform.Foundation.NSDate().timeIntervalSince1970 * 1000).toLong()

actual class PlatformContext

actual fun createPrimalDatabase(context: PlatformContext): PrimalDatabase {
    val dbPath = (NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).first() as String) + "/primal.db"
    return Room.databaseBuilder<PrimalDatabase>(name = dbPath)
        .addMigrations(*ALL_MIGRATIONS)
        .fallbackToDestructiveMigration(true)
        .build()
}

@OptIn(ExperimentalForeignApi::class)
actual fun readBundledText(context: PlatformContext, name: String): String? {
    val path = NSBundle.mainBundle.pathForResource(name.substringBeforeLast('.'), name.substringAfterLast('.')) ?: return null
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
}

package com.primalapp.database

expect class PlatformContext

expect fun currentTimeMillis(): Long

expect fun createPrimalDatabase(context: PlatformContext): PrimalDatabase

/** Файл, поставляемый с приложением (каталог и «Инфо» по умолчанию); нет файла — `null`. */
expect fun readBundledText(context: PlatformContext, name: String): String?

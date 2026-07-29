package com.primalapp.database

expect class PlatformContext

expect fun currentTimeMillis(): Long

expect fun createPrimalDatabase(context: PlatformContext): PrimalDatabase

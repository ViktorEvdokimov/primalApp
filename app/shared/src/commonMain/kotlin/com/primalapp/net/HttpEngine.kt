package com.primalapp.net

import io.ktor.client.engine.HttpClientEngine

/** HTTP-движок платформы: OkHttp на Android, NSURLSession на iOS. */
expect fun createHttpEngine(): HttpClientEngine

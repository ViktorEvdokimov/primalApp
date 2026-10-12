package com.primalapp.net

import com.primalapp.database.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Сервер недоступен: нет сети, неверный адрес или сервер не отвечает. */
class OfflineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Ответ сервера с ошибкой: [status] HTTP и [code] из `application/problem+json`. */
class ApiException(val status: Int, val code: String?, val detail: String?, val retryAfter: Int? = null) :
    Exception(detail ?: "Ошибка сервера ($status)") {
    val userMessage: String
        get() = when {
            code == "RATE_LIMITED" && retryAfter != null -> "Слишком много попыток. Повторите через $retryAfter с."
            !detail.isNullOrBlank() -> detail
            status == 401 -> "Войдите в аккаунт."
            status == 404 -> "Не найдено."
            else -> "Ошибка сервера ($status)."
        }
}

/** Условный ответ: не изменилось (`304`) или новое тело с ETag. */
sealed interface Fetched<out T> {
    data object NotModified : Fetched<Nothing>
    data class Body<T>(val value: T, val etag: String?) : Fetched<T>
}

/** Событие SSE кампании: `campaign.updated` (версия) или `access.revoked`. */
data class CampaignEvent(val type: String, val data: String)

/**
 * Клиент API сервера (`web/doc/api.md`): вход по cookie устройства, CSRF-токен в заголовке изменяющих запросов,
 * условные запросы справочников по ETag. Адрес сервера берётся из настроек при каждом запросе.
 */
class PrimalApi(
    engine: HttpClientEngine,
    private val serverUrl: () -> String,
    private val cookies: CookieJar
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }

    private val client = HttpClient(engine) {
        expectSuccess = false
        followRedirects = true
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    /** Адрес API: `https://primalhelper.ru/api/v1`. */
    fun apiBase(): String = normalizeServerUrl(serverUrl()) + "/api/v1"

    // region Вход

    suspend fun me(): MeDto = get("/auth/me", MeDto.serializer())

    suspend fun login(login: String, password: String): MeDto =
        send(HttpMethod.Post, "/auth/login", LoginRequest(login, password), LoginRequest.serializer(), MeDto.serializer())

    suspend fun register(login: String, password: String, displayName: String): MeDto =
        send(HttpMethod.Post, "/auth/register", RegisterRequest(login, password, displayName), RegisterRequest.serializer(), MeDto.serializer())

    suspend fun logout() {
        sendEmpty(HttpMethod.Post, "/auth/logout")
    }

    suspend fun rename(displayName: String): MeDto =
        send(HttpMethod.Patch, "/auth/me", DisplayNameRequest(displayName), DisplayNameRequest.serializer(), MeDto.serializer())

    suspend fun setExpansions(expansions: List<String>): MeDto =
        send(HttpMethod.Put, "/auth/me/expansions", ExpansionsRequest(expansions), ExpansionsRequest.serializer(), MeDto.serializer())

    // endregion

    // region Справочники

    suspend fun catalogBundle(etag: String?): Fetched<String> = conditional("/catalog/bundle", etag)

    suspend fun info(etag: String?): Fetched<String> = conditional("/info", etag)

    /** Картинка «Инфо» по адресу из статьи (`/api/v1/info/images/…`). */
    suspend fun infoImage(path: String): ByteArray {
        val response = execute(HttpMethod.Get, absolute(path), null)
        if (response.status.value != 200) throw problem(response)
        return response.readBytes()
    }

    // endregion

    // region Кампании

    suspend fun campaigns(): List<CampaignSummaryDto> = get("/campaigns", ListSerializer(CampaignSummaryDto.serializer()))

    suspend fun snapshot(id: Long, version: Int?): Fetched<CampaignSnapshotDto> {
        val response = execute(HttpMethod.Get, apiBase() + "/campaigns/$id/snapshot", null) {
            if (version != null) header(HttpHeaders.IfNoneMatch, "\"v$version\"")
        }
        return when (response.status.value) {
            304 -> Fetched.NotModified
            200 -> Fetched.Body(json.decodeFromString(CampaignSnapshotDto.serializer(), response.bodyAsText()), null)
            else -> throw problem(response)
        }
    }

    suspend fun putSnapshot(id: Long, expectedVersion: Int, state: SnapshotStateDto): CampaignSnapshotDto =
        send(HttpMethod.Put, "/campaigns/$id/snapshot", SnapshotUpdateDto(expectedVersion, state), SnapshotUpdateDto.serializer(),
            CampaignSnapshotDto.serializer())

    suspend fun importCampaign(state: SnapshotStateDto): CampaignSnapshotDto =
        send(HttpMethod.Post, "/campaigns/import", SnapshotImportDto(state), SnapshotImportDto.serializer(), CampaignSnapshotDto.serializer())

    suspend fun deleteCampaign(id: Long) {
        sendEmpty(HttpMethod.Delete, "/campaigns/$id")
    }

    // endregion

    // region Совместная игра

    /** Ссылка на кампанию; ссылки нет — `null`. */
    suspend fun shareLink(id: Long): ShareLinkDto? {
        val response = execute(HttpMethod.Get, apiBase() + "/campaigns/$id/share-link", null)
        return when (response.status.value) {
            200 -> json.decodeFromString(ShareLinkDto.serializer(), response.bodyAsText())
            404 -> null
            else -> throw problem(response)
        }
    }

    /** Создать ссылку или перевыпустить: старая перестаёт действовать. */
    suspend fun createShareLink(id: Long): ShareLinkDto = sendNoBody(HttpMethod.Post, "/campaigns/$id/share-link", ShareLinkDto.serializer())

    suspend fun revokeShareLink(id: Long) {
        sendEmpty(HttpMethod.Delete, "/campaigns/$id/share-link")
    }

    suspend fun shareInfo(token: String): ShareInfoDto = get("/share/$token", ShareInfoDto.serializer())

    suspend fun join(token: String, displayName: String?): JoinResultDto =
        send(HttpMethod.Post, "/share/$token/join", JoinRequest(displayName?.takeIf { it.isNotBlank() }), JoinRequest.serializer(),
            JoinResultDto.serializer())

    /**
     * Поток событий кампании (SSE): события только уведомляют о новой версии — приложение само запрашивает снимок.
     * Поток завершается при обрыве соединения.
     */
    fun events(id: Long): Flow<CampaignEvent> = flow {
        val url = apiBase() + "/campaigns/$id/events"
        val statement = client.prepareRequest {
            prepare(this, HttpMethod.Get, url)
            header(HttpHeaders.Accept, "text/event-stream")
            timeout {
                requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = 90_000
            }
        }
        try {
            statement.execute { response ->
                receiveCookies(response)
                if (response.status.value != 200) throw problem(response)
                val channel = response.bodyAsChannel()
                var event = "message"
                val data = StringBuilder()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) emit(CampaignEvent(event, data.toString()))
                            event = "message"
                            data.clear()
                        }
                        line.startsWith(":") -> Unit
                        line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ApiException) {
            throw exception
        } catch (exception: Exception) {
            throw OfflineException("Соединение с сервером прервано.", exception)
        }
    }

    // endregion

    // region Запросы

    private suspend fun conditional(path: String, etag: String?): Fetched<String> {
        val response = execute(HttpMethod.Get, apiBase() + path, null) {
            if (etag != null) header(HttpHeaders.IfNoneMatch, etag)
        }
        return when (response.status.value) {
            304 -> Fetched.NotModified
            200 -> Fetched.Body(response.bodyAsText(), response.headers[HttpHeaders.ETag])
            else -> throw problem(response)
        }
    }

    private suspend fun <T> get(path: String, serializer: KSerializer<T>): T {
        val response = execute(HttpMethod.Get, apiBase() + path, null)
        if (response.status.value !in 200..299) throw problem(response)
        return json.decodeFromString(serializer, response.bodyAsText())
    }

    private suspend fun <B, T> send(method: HttpMethod, path: String, body: B, bodySerializer: KSerializer<B>, serializer: KSerializer<T>): T {
        val response = execute(method, apiBase() + path, json.encodeToString(bodySerializer, body))
        if (response.status.value !in 200..299) throw problem(response)
        return json.decodeFromString(serializer, response.bodyAsText())
    }

    private suspend fun <T> sendNoBody(method: HttpMethod, path: String, serializer: KSerializer<T>): T {
        val response = execute(method, apiBase() + path, null)
        if (response.status.value !in 200..299) throw problem(response)
        return json.decodeFromString(serializer, response.bodyAsText())
    }

    private suspend fun sendEmpty(method: HttpMethod, path: String) {
        val response = execute(method, apiBase() + path, null)
        if (response.status.value !in 200..299) throw problem(response)
    }

    /**
     * Запрос с cookie устройства; изменяющий запрос — с CSRF-токеном из cookie `XSRF-TOKEN` (нет cookie — сначала
     * `GET /auth/csrf`). Сетевая ошибка — [OfflineException].
     */
    private suspend fun execute(
        method: HttpMethod,
        url: String,
        body: String?,
        configure: HttpRequestBuilder.() -> Unit = {}
    ): HttpResponse {
        if (method != HttpMethod.Get && csrfToken(url) == null) {
            execute(HttpMethod.Get, apiBase() + "/auth/csrf", null)
        }
        val response = try {
            client.request {
                prepare(this, method, url)
                if (body != null) setBody(TextContent(body, ContentType.Application.Json))
                configure()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw OfflineException("Сервер недоступен: ${normalizeServerUrl(serverUrl())}", exception)
        }
        receiveCookies(response)
        return response
    }

    private fun prepare(builder: HttpRequestBuilder, method: HttpMethod, url: String) {
        builder.method = method
        builder.url(url)
        val parsed = Url(url)
        cookies.header(parsed, currentTimeMillis())?.let { builder.header(HttpHeaders.Cookie, it) }
        if (method != HttpMethod.Get) {
            csrfToken(url)?.let { builder.header("X-XSRF-TOKEN", it) }
        }
        builder.header(HttpHeaders.Accept, "application/json")
        builder.header(HttpHeaders.UserAgent, "PrimalApp Android")
    }

    private fun csrfToken(url: String): String? = cookies.value(Url(url), "XSRF-TOKEN", currentTimeMillis())

    private fun receiveCookies(response: HttpResponse) {
        cookies.receive(response.call.request.url, response.headers.getAll(HttpHeaders.SetCookie).orEmpty(), currentTimeMillis())
    }

    private suspend fun problem(response: HttpResponse): ApiException {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val problem = runCatching { json.decodeFromString(ProblemDto.serializer(), text) }.getOrNull()
        val detail = problem?.errors?.firstOrNull()?.message?.takeIf { problem.code == "VALIDATION_FAILED" } ?: problem?.detail
        val retryAfter = problem?.retryAfter ?: response.headers[HttpHeaders.RetryAfter]?.toIntOrNull()
        return ApiException(response.status.value, problem?.code, detail, retryAfter)
    }

    private fun absolute(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path else normalizeServerUrl(serverUrl()) + path

    // endregion

    companion object {
        const val DEFAULT_SERVER = "https://primalhelper.ru/"

        /** Адрес сервера без завершающей косой черты; без схемы — https. */
        fun normalizeServerUrl(url: String): String {
            val trimmed = url.trim().ifEmpty { DEFAULT_SERVER }
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
            return withScheme.trimEnd('/')
        }
    }
}

package com.primalapp.net

import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader
import kotlinx.serialization.Serializable

/** Cookie сервера: устройство (`PRIMAL_DEVICE`) и CSRF (`XSRF-TOKEN`). */
@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long? = null,
    val secure: Boolean = false
)

/**
 * Cookie сервера в приложении — как в браузере: сохраняются из `Set-Cookie`, отправляются с запросами к тому же
 * адресу и пути. Хранятся в настройках ([onChange]), поэтому вход не теряется при перезапуске.
 */
class CookieJar(initial: List<StoredCookie>, private val onChange: (List<StoredCookie>) -> Unit) {

    private var cookies: List<StoredCookie> = initial

    val all: List<StoredCookie> get() = cookies

    /** Заголовок `Cookie` для запроса; `null` — нечего отправлять. */
    fun header(url: Url, now: Long): String? {
        val matching = cookies.filter { matches(it, url, now) }
        return if (matching.isEmpty()) null else matching.joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun value(url: Url, name: String, now: Long): String? = cookies.firstOrNull { it.name == name && matches(it, url, now) }?.value

    /** Ответ сервера: `Set-Cookie` заменяет cookie с тем же именем, доменом и путём; истёкшая удаляется. */
    fun receive(url: Url, setCookieHeaders: List<String>, now: Long) {
        if (setCookieHeaders.isEmpty()) return
        var next = cookies
        for (header in setCookieHeaders) {
            val parsed = runCatching { parseServerSetCookieHeader(header) }.getOrNull() ?: continue
            val domain = parsed.domain?.trimStart('.')?.lowercase() ?: url.host.lowercase()
            val path = parsed.path?.takeIf { it.startsWith("/") } ?: defaultPath(url)
            val expiresAt = when {
                parsed.maxAge > 0 -> now + parsed.maxAge * 1000L
                parsed.maxAge < 0 || header.contains("Max-Age=0", ignoreCase = true) -> 0L
                parsed.expires != null -> parsed.expires!!.timestamp
                else -> null
            }
            next = next.filterNot { it.name == parsed.name && it.domain == domain && it.path == path }
            if (expiresAt == null || expiresAt > now) {
                next = next + StoredCookie(parsed.name, parsed.value, domain, path, expiresAt, parsed.secure)
            }
        }
        if (next != cookies) {
            cookies = next
            onChange(next)
        }
    }

    /** Cookie, сохранённые при прошлом запуске. */
    fun receiveStored(saved: List<StoredCookie>) {
        cookies = saved
    }

    fun clear() {
        if (cookies.isNotEmpty()) {
            cookies = emptyList()
            onChange(cookies)
        }
    }

    private fun matches(cookie: StoredCookie, url: Url, now: Long): Boolean {
        val host = url.host.lowercase()
        val domainMatches = host == cookie.domain || host.endsWith("." + cookie.domain)
        val path = url.encodedPath.ifEmpty { "/" }
        val pathMatches = path == cookie.path || path.startsWith(cookie.path.trimEnd('/') + "/") || cookie.path == "/"
        val alive = cookie.expiresAt == null || cookie.expiresAt > now
        val secureOk = !cookie.secure || url.protocol.name == "https"
        return domainMatches && pathMatches && alive && secureOk
    }

    private fun defaultPath(url: Url): String {
        val path = url.encodedPath
        val slash = path.lastIndexOf('/')
        return if (slash <= 0) "/" else path.substring(0, slash)
    }
}

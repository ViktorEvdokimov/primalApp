package com.primalapp.net

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Cookie устройства и CSRF — как в браузере: по адресу, пути, сроку и HTTPS. */
class CookieJarTest {

    @Test
    fun `cookie сохраняются из Set-Cookie и отправляются по пути`() {
        // Подготовка
        var saved: List<StoredCookie> = emptyList()
        val jar = CookieJar(emptyList()) { saved = it }
        val url = Url("https://primalhelper.ru/api/v1/auth/login")

        // Вызов проверяемого кода
        jar.receive(url, listOf("PRIMAL_DEVICE=abc; Path=/api; Max-Age=3600; HttpOnly; Secure", "XSRF-TOKEN=t1; Path=/"), now = 0)

        // Проверка
        assertEquals(2, saved.size)
        assertEquals("PRIMAL_DEVICE=abc; XSRF-TOKEN=t1", jar.header(Url("https://primalhelper.ru/api/v1/campaigns"), now = 1000))
        assertEquals("XSRF-TOKEN=t1", jar.header(Url("https://primalhelper.ru/"), now = 1000))
        assertEquals("t1", jar.value(url, "XSRF-TOKEN", now = 1000))
        assertNull(jar.header(Url("https://other.ru/api"), now = 1000))
    }

    @Test
    fun `истёкшая и secure-cookie по HTTP не отправляются, Max-Age=0 удаляет`() {
        // Подготовка
        val jar = CookieJar(emptyList()) {}
        val url = Url("https://primalhelper.ru/api/v1/auth/login")
        jar.receive(url, listOf("PRIMAL_DEVICE=abc; Path=/api; Max-Age=10; Secure"), now = 0)

        // Проверка
        assertNull(jar.header(Url("http://primalhelper.ru/api/v1"), now = 1))
        assertNull(jar.header(url, now = 20_000))

        // Вызов проверяемого кода
        jar.receive(url, listOf("PRIMAL_DEVICE=; Path=/api; Max-Age=0"), now = 2)

        // Проверка
        assertEquals(emptyList(), jar.all)
    }
}

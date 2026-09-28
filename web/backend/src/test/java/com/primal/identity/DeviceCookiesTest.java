package com.primal.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.primal.support.TestProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ResponseCookie;

@DisplayName("Cookie устройства и подпись браузера")
class DeviceCookiesTest {

    @Test
    @DisplayName("cookie Secure только при https-адресе сайта")
    void secureOnlyOnHttps() {
        // вызов
        ResponseCookie local = new DeviceCookies(TestProperties.primal("http://localhost:8088")).issue("token");
        ResponseCookie server = new DeviceCookies(TestProperties.primal("https://primal.example.com")).issue("token");

        // проверка
        assertThat(local.isSecure()).isFalse();
        assertThat(server.isSecure()).isTrue();
        assertThat(server.isHttpOnly()).isTrue();
        assertThat(server.getSameSite()).isEqualTo("Lax");
        assertThat(server.getPath()).isEqualTo("/api");
        assertThat(server.getMaxAge().toDays()).isEqualTo(400);
    }

    @Test
    @DisplayName("выход стирает cookie")
    void clearRemovesCookie() {
        // вызов
        ResponseCookie cleared = new DeviceCookies(TestProperties.primal("https://primal.example.com")).clear();

        // проверка
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge().isZero()).isTrue();
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36|Chrome, Android",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1|Safari, iOS",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0|Firefox, Windows",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36 Edg/140.0|Edge, Windows",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 YaBrowser/25.8 Safari/537.36|Яндекс Браузер, macOS",
        "curl/8.9.1|Неизвестный браузер",
    })
    @DisplayName("короткая подпись браузера для списка устройств")
    void userAgentShortName(String userAgent, String expected) {
        // вызов и проверка
        assertThat(UserAgents.shortName(userAgent)).isEqualTo(expected);
    }

    @Test
    @DisplayName("без заголовка User-Agent — «Неизвестный браузер»")
    void missingUserAgent() {
        // вызов и проверка
        assertThat(UserAgents.shortName(null)).isEqualTo("Неизвестный браузер");
    }
}

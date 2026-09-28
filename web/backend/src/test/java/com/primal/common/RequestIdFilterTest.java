package com.primal.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.primal.common.web.RequestIdFilter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("Номер запроса X-Request-Id")
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    /** Номер запроса в логах — значение MDC, пока запрос обрабатывается. */
    private String[] run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> duringRequest = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> duringRequest.set(MDC.get(RequestIdFilter.MDC_KEY)));
        return new String[] {duringRequest.get(), response.getHeader(RequestIdFilter.HEADER)};
    }

    @Test
    @DisplayName("без заголовка — новый номер: в ответе и в логах запроса, после запроса MDC очищен")
    void generated() throws Exception {
        // вызов
        String[] ids = run(new MockHttpServletRequest("GET", "/api/v1/catalog/bosses"), new MockHttpServletResponse());

        // проверка
        assertThat(ids[0]).isNotBlank().isEqualTo(ids[1]);
        assertThat(ids[0]).matches("[0-9a-f-]{36}");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("номер от прокси принимается, опасный — заменяется новым")
    void incoming() throws Exception {
        // подготовка
        MockHttpServletRequest fromProxy = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        fromProxy.addHeader(RequestIdFilter.HEADER, "caddy-42");
        MockHttpServletRequest injected = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        injected.addHeader(RequestIdFilter.HEADER, "bad\nheader");

        // вызов и проверка
        assertThat(run(fromProxy, new MockHttpServletResponse())).containsExactly("caddy-42", "caddy-42");
        assertThat(run(injected, new MockHttpServletResponse())[1]).doesNotContain("bad").hasSize(36);
    }
}

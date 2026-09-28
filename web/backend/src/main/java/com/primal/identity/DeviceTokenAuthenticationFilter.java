package com.primal.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Аутентификация каждого запроса по cookie устройства {@code PRIMAL_DEVICE}; HTTP-сессий нет.
 * Нет cookie или устройство не действует — запрос идёт дальше без аутентификации, защищённые пути ответят 401.
 * Создаётся в {@link SecurityConfig}, а не бином: иначе Spring Boot зарегистрировал бы его ещё и в сервлет-контейнере.
 */
class DeviceTokenAuthenticationFilter extends OncePerRequestFilter {

    private final DeviceService devices;
    private final DeviceCookies cookies;

    DeviceTokenAuthenticationFilter(DeviceService devices, DeviceCookies cookies) {
        this.devices = devices;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        cookies.read(request).ifPresent(token -> devices.authenticate(token).ifPresent(authentication -> {
            PrimalPrincipal principal = authentication.principal();
            String role = principal instanceof PrimalPrincipal.UserPrincipal ? "ROLE_USER" : "ROLE_GUEST";
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    new PreAuthenticatedAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(role))));
            SecurityContextHolder.setContext(context);
            if (authentication.renewCookie()) {
                response.addHeader(HttpHeaders.SET_COOKIE, cookies.issue(token).toString());
            }
        }));
        chain.doFilter(request, response);
    }
}

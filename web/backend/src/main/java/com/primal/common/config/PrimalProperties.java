package com.primal.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки приложения {@code primal.*}. Значения приходят из переменных окружения {@code .env}
 * (см. {@code doc/setup.md}, раздел «Переменные окружения»).
 *
 * @param publicUrl адрес сайта для пользователей; из него строятся ссылки-приглашения,
 *                  {@code https://} включает защищённые cookie и строгую проверку секретов
 * @param shareLink ссылки-приглашения
 * @param campaign  ограничения кампаний
 * @param mail      отправка писем (пока не используется: вход — по логину и паролю)
 * @param rateLimit ограничения частоты с одного IP
 */
@Validated
@ConfigurationProperties(prefix = "primal")
public record PrimalProperties(
        @NotBlank String publicUrl,
        @NotNull @Valid ShareLink shareLink,
        @NotNull @Valid Campaign campaign,
        @NotNull @Valid Mail mail,
        @NotNull @Valid RateLimit rateLimit) {

    /** @param key ключ подписи ссылок-приглашений ({@code PRIMAL_SHARE_LINK_KEY}) */
    public record ShareLink(@NotBlank String key) {
    }

    /**
     * @param maxPerUser         не больше стольких собственных кампаний у пользователя
     * @param hunters            допустимое число охотников в отряде
     * @param battleWarningHours сколько часов отметка о начале боя считается идущим боем
     */
    public record Campaign(@Min(1) int maxPerUser, @NotNull @Valid Hunters hunters, @Min(1) int battleWarningHours) {
    }

    public record Hunters(@Min(1) int min, @Min(1) int max) {
    }

    /**
     * Способ отправки ({@code MAIL_PROVIDER}), SMTP-сервер ({@code SMTP_*}), HTTP API Mailgun
     * ({@code MAILGUN_*}) и отправитель ({@code MAIL_FROM}).
     *
     * @param provider {@code SMTP} — через {@code SMTP_*} (локально Mailpit), {@code MAILGUN} — HTTP API Mailgun
     * @param tls      {@code NONE} — без шифрования (Mailpit), {@code STARTTLS} — порт 587, {@code SSL} — порт 465
     */
    public record Mail(
            @NotNull MailProvider provider,
            @NotBlank String host,
            @Min(1) int port,
            String username,
            String password,
            @NotNull MailTls tls,
            @NotBlank String from,
            @NotNull @Valid Mailgun mailgun) {
    }

    public enum MailProvider { SMTP, MAILGUN }

    public enum MailTls { NONE, STARTTLS, SSL }

    /**
     * HTTP API Mailgun — нужен при {@code MAIL_PROVIDER=mailgun}.
     *
     * @param baseUrl {@code MAILGUN_BASE_URL}: {@code https://api.mailgun.net}, для европейского региона —
     *                {@code https://api.eu.mailgun.net}
     * @param domain  {@code MAILGUN_DOMAIN}: домен отправки, например sandbox-домен
     * @param apiKey  {@code MAILGUN_API_KEY}: секрет, в {@link #toString()} скрыт
     */
    public record Mailgun(@NotBlank String baseUrl, String domain, String apiKey) {

        @Override
        public String toString() {
            return "Mailgun[baseUrl=" + baseUrl + ", domain=" + domain + ", apiKey="
                    + (apiKey == null || apiKey.isBlank() ? "" : "***") + "]";
        }
    }

    /**
     * Лимиты с одного IP ({@code doc/architecture.md} §6). Лимит попыток входа по одному номеру не
     * настраивается: он защищает пароль от перебора.
     *
     * @param registrationsPerIpHour регистраций в час ({@code PRIMAL_REGISTRATIONS_PER_IP_HOUR})
     * @param loginsPerIpHour        попыток входа в час ({@code PRIMAL_LOGINS_PER_IP_HOUR})
     * @param joinsPerIpHour         входов по ссылкам-приглашениям в час ({@code PRIMAL_JOINS_PER_IP_HOUR})
     */
    public record RateLimit(@Min(1) int registrationsPerIpHour, @Min(1) int loginsPerIpHour, @Min(1) int joinsPerIpHour) {
    }

    /** Сайт открывается по HTTPS — значит, это настоящее развёртывание. */
    public boolean isHttps() {
        return publicUrl.startsWith("https://");
    }
}

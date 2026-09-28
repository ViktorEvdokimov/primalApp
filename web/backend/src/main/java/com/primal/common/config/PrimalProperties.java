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
 * @param otp       коды входа
 * @param shareLink ссылки-приглашения
 * @param campaign  ограничения кампаний
 * @param mail      отправка писем с кодами входа
 * @param rateLimit ограничения частоты с одного IP
 */
@Validated
@ConfigurationProperties(prefix = "primal")
public record PrimalProperties(
        @NotBlank String publicUrl,
        @NotNull @Valid Otp otp,
        @NotNull @Valid ShareLink shareLink,
        @NotNull @Valid Campaign campaign,
        @NotNull @Valid Mail mail,
        @NotNull @Valid RateLimit rateLimit) {

    /** @param pepper секрет для HMAC кодов входа ({@code PRIMAL_OTP_PEPPER}) */
    public record Otp(@NotBlank String pepper) {
    }

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
     * SMTP-сервер ({@code SMTP_*}) и отправитель ({@code MAIL_FROM}).
     *
     * @param tls {@code NONE} — без шифрования (Mailpit), {@code STARTTLS} — порт 587, {@code SSL} — порт 465
     */
    public record Mail(
            @NotBlank String host,
            @Min(1) int port,
            String username,
            String password,
            @NotNull MailTls tls,
            @NotBlank String from) {
    }

    public enum MailTls { NONE, STARTTLS, SSL }

    /**
     * Лимиты с одного IP ({@code doc/architecture.md} §6). Лимиты на адрес почты не настраиваются: они
     * защищают почтовые ящики.
     *
     * @param codesPerIpHour         писем с кодом в час ({@code PRIMAL_CODES_PER_IP_HOUR})
     * @param verificationsPerIpHour проверок кода в час ({@code PRIMAL_VERIFICATIONS_PER_IP_HOUR})
     * @param joinsPerIpHour         входов по ссылкам-приглашениям в час ({@code PRIMAL_JOINS_PER_IP_HOUR})
     */
    public record RateLimit(@Min(1) int codesPerIpHour, @Min(1) int verificationsPerIpHour, @Min(1) int joinsPerIpHour) {
    }

    /** Сайт открывается по HTTPS — значит, это настоящее развёртывание. */
    public boolean isHttps() {
        return publicUrl.startsWith("https://");
    }
}

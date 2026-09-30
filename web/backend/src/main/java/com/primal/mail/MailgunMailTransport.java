package com.primal.mail;

import com.primal.common.config.PrimalProperties;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Отправка через HTTP API Mailgun: {@code POST {MAILGUN_BASE_URL}/v3/{MAILGUN_DOMAIN}/messages}, ключ —
 * Basic-авторизация {@code api:{MAILGUN_API_KEY}}. Отказ Mailgun (неверный ключ, у sandbox-домена — адрес
 * не из списка разрешённых получателей) — {@link MailSendException} с кодом и ответом Mailgun, без ключа.
 */
class MailgunMailTransport implements MailTransport {

    private static final int MAX_REASON_LENGTH = 300;

    private final RestClient client;
    private final String domain;

    MailgunMailTransport(RestClient.Builder builder, PrimalProperties.Mailgun mailgun) {
        this.client = builder
                .baseUrl(mailgun.baseUrl().replaceAll("/+$", ""))
                .defaultHeaders(headers -> headers.setBasicAuth("api", mailgun.apiKey()))
                .build();
        this.domain = mailgun.domain();
    }

    @Override
    public void send(OutgoingMail mail) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("from", mail.from().toUnicodeString());
        form.add("to", mail.to());
        form.add("subject", mail.subject());
        form.add("text", mail.text());
        form.add("html", mail.html());
        try {
            client.post()
                    .uri("/v3/{domain}/messages", domain)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new MailSendException("Mailgun отказал (" + exception.getStatusCode().value() + "): "
                    + reason(exception.getResponseBodyAsString()), exception);
        } catch (RestClientException exception) {
            throw new MailSendException("Mailgun недоступен: " + exception.getMessage(), exception);
        }
    }

    private static String reason(String body) {
        String oneLine = body.replaceAll("\\s+", " ").strip();
        return oneLine.length() > MAX_REASON_LENGTH ? oneLine.substring(0, MAX_REASON_LENGTH) + "…" : oneLine;
    }
}

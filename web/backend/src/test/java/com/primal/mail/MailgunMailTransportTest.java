package com.primal.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.primal.common.config.PrimalProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MailgunMailTransportTest {

    private static final String KEY = "test-mailgun-key";
    private static final String DOMAIN = "sandbox123.mailgun.org";
    private static final String MESSAGES = "https://api.mailgun.net/v3/" + DOMAIN + "/messages";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer mailgun = MockRestServiceServer.bindTo(builder).build();

    private MailgunMailTransport transport(String baseUrl) {
        return new MailgunMailTransport(builder, new PrimalProperties.Mailgun(baseUrl, DOMAIN, KEY));
    }

    private static OutgoingMail mail() {
        return new OutgoingMail(MailService.parseFrom("Праймал <postmaster@" + DOMAIN + ">"), "hunter@example.com",
                "Код входа в Primal: 123456", "Ваш код: 123456", "<p>Ваш код: <b>123456</b></p>");
    }

    @Test
    void sendsFormWithBasicAuthToDomainMessages() {
        // подготовка
        String auth = Base64.getEncoder().encodeToString(("api:" + KEY).getBytes(StandardCharsets.UTF_8));
        mailgun.expect(requestTo(MESSAGES))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + auth))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "from", "\"Праймал\" <postmaster@" + DOMAIN + ">",
                        "to", "hunter@example.com",
                        "subject", "Код входа в Primal: 123456",
                        "text", "Ваш код: 123456",
                        "html", "<p>Ваш код: <b>123456</b></p>")))
                .andRespond(withSuccess("{\"id\":\"<1@" + DOMAIN + ">\",\"message\":\"Queued. Thank you.\"}",
                        MediaType.APPLICATION_JSON));

        // вызов
        transport("https://api.mailgun.net").send(mail());

        // проверка
        mailgun.verify();
    }

    @Test
    void trailingSlashInBaseUrlIsIgnored() {
        // подготовка
        mailgun.expect(requestTo(MESSAGES)).andRespond(withSuccess());

        // вызов
        transport("https://api.mailgun.net/").send(mail());

        // проверка
        mailgun.verify();
    }

    @Test
    void sandboxRefusalExplainsReasonWithoutKey() {
        // подготовка: получатель не добавлен в разрешённые у sandbox-домена
        mailgun.expect(requestTo(MESSAGES)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\": \"Domain " + DOMAIN + " is not allowed to send: Free accounts are for test "
                        + "purposes only. Please upgrade or add the address to your authorized recipients in "
                        + "Account Settings.\"}"));

        // вызов и проверка
        assertThatThrownBy(() -> transport("https://api.mailgun.net").send(mail()))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("Mailgun отказал (403)")
                .hasMessageContaining("authorized recipients")
                .message().doesNotContain(KEY);
    }

    @Test
    void wrongKeyIsReportedAsRefusal() {
        // подготовка
        mailgun.expect(requestTo(MESSAGES)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("Forbidden"));

        // вызов и проверка
        assertThatThrownBy(() -> transport("https://api.mailgun.net").send(mail()))
                .isInstanceOf(MailSendException.class)
                .hasMessage("Mailgun отказал (401): Forbidden");
    }

    @Test
    void longResponseIsShortened() {
        // подготовка
        mailgun.expect(requestTo(MESSAGES)).andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("x".repeat(1000)));

        // вызов и проверка
        assertThatThrownBy(() -> transport("https://api.mailgun.net").send(mail()))
                .isInstanceOf(MailSendException.class)
                .satisfies(error -> assertThat(error.getMessage()).hasSizeLessThan(350).endsWith("…"));
    }
}

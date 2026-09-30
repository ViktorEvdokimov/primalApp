package com.primal.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.primal.common.config.PrimalProperties;
import com.primal.support.TestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class MailConfigTest {

    private final MailConfig config = new MailConfig();

    private static PrimalProperties withMail(PrimalProperties.MailProvider provider, PrimalProperties.Mailgun mailgun) {
        PrimalProperties base = TestProperties.primal("http://localhost:8088");
        PrimalProperties.Mail mail = base.mail();
        return new PrimalProperties(base.publicUrl(), base.shareLink(), base.campaign(),
                new PrimalProperties.Mail(provider, mail.host(), mail.port(), mail.username(), mail.password(),
                        mail.tls(), mail.from(), mailgun),
                base.rateLimit());
    }

    @Test
    void smtpByDefault() {
        // вызов
        MailTransport transport = config.mailTransport(TestProperties.primal("http://localhost:8088"), new JavaMailSenderImpl());

        // проверка
        assertThat(transport).isInstanceOf(SmtpMailTransport.class);
    }

    @Test
    void mailgunWhenProviderIsMailgun() {
        // подготовка
        PrimalProperties properties = withMail(PrimalProperties.MailProvider.MAILGUN,
                new PrimalProperties.Mailgun("https://api.mailgun.net", "sandbox123.mailgun.org", "test-key"));

        // вызов
        MailTransport transport = config.mailTransport(properties, new JavaMailSenderImpl());

        // проверка
        assertThat(transport).isInstanceOf(MailgunMailTransport.class);
    }

    @Test
    void mailgunWithoutKeyAndDomainStopsStartupNamingVariables() {
        // подготовка
        PrimalProperties properties = withMail(PrimalProperties.MailProvider.MAILGUN,
                new PrimalProperties.Mailgun("https://api.mailgun.net", "", " "));

        // вызов и проверка
        assertThatThrownBy(() -> config.mailTransport(properties, new JavaMailSenderImpl()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAILGUN_API_KEY и MAILGUN_DOMAIN");
    }

    @Test
    void mailgunKeyIsHiddenInToString() {
        // вызов
        String text = new PrimalProperties.Mailgun("https://api.mailgun.net", "sandbox123.mailgun.org", "secret-key")
                .toString();

        // проверка
        assertThat(text).contains("sandbox123.mailgun.org").contains("apiKey=***").doesNotContain("secret-key");
    }
}

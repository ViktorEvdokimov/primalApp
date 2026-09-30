package com.primal.mail;

import com.primal.common.config.PrimalProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Способ отправки — {@code MAIL_PROVIDER}: {@code smtp} (переменные {@code SMTP_*}) или {@code mailgun}
 * (HTTP API, {@code MAILGUN_*}). {@code SMTP_TLS}: {@code none} — без шифрования (Mailpit),
 * {@code starttls} — обычно порт 587, {@code ssl} — порт 465.
 */
@Configuration(proxyBeanMethods = false)
class MailConfig {

    private static final String TIMEOUT_MS = "10000";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Bean
    MailTransport mailTransport(PrimalProperties properties, JavaMailSender javaMailSender) {
        return switch (properties.mail().provider()) {
            case SMTP -> new SmtpMailTransport(javaMailSender);
            case MAILGUN -> new MailgunMailTransport(mailgunClient(), requireMailgun(properties.mail().mailgun()));
        };
    }

    /** Без ключа или домена письма не уйдут — сайт не запускается и называет недостающие переменные. */
    static PrimalProperties.Mailgun requireMailgun(PrimalProperties.Mailgun mailgun) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(mailgun.apiKey())) {
            missing.add("MAILGUN_API_KEY");
        }
        if (!StringUtils.hasText(mailgun.domain())) {
            missing.add("MAILGUN_DOMAIN");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("MAIL_PROVIDER=mailgun: задайте " + String.join(" и ", missing)
                    + " в .env (doc/setup.md, раздел 3.3)");
        }
        return mailgun;
    }

    private static RestClient.Builder mailgunClient() {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requests.setReadTimeout(TIMEOUT);
        return RestClient.builder().requestFactory(requests);
    }

    @Bean
    JavaMailSender javaMailSender(PrimalProperties properties) {
        PrimalProperties.Mail mail = properties.mail();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.host());
        sender.setPort(mail.port());
        sender.setDefaultEncoding("UTF-8");

        Properties smtp = sender.getJavaMailProperties();
        // Без этих свойств JavaMail для Message-ID и EHLO спрашивает имя машины у DNS (InetAddress.getLocalHost),
        // а это может занимать секунды
        smtp.put("mail.from", MailService.parseFrom(mail.from()).getAddress());
        smtp.put("mail.smtp.localhost", URI.create(properties.publicUrl()).getHost());
        smtp.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        smtp.put("mail.smtp.timeout", TIMEOUT_MS);
        smtp.put("mail.smtp.writetimeout", TIMEOUT_MS);
        if (StringUtils.hasText(mail.username())) {
            sender.setUsername(mail.username());
            sender.setPassword(mail.password());
            smtp.put("mail.smtp.auth", "true");
        }
        switch (mail.tls()) {
            case NONE -> {
            }
            case STARTTLS -> {
                smtp.put("mail.smtp.starttls.enable", "true");
                smtp.put("mail.smtp.starttls.required", "true");
            }
            case SSL -> smtp.put("mail.smtp.ssl.enable", "true");
        }
        return sender;
    }
}

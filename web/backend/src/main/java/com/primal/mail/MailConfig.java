package com.primal.mail;

import com.primal.common.config.PrimalProperties;
import java.net.URI;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

/**
 * SMTP из переменных {@code SMTP_*}. {@code SMTP_TLS}: {@code none} — без шифрования (Mailpit),
 * {@code starttls} — обычно порт 587, {@code ssl} — порт 465.
 */
@Configuration(proxyBeanMethods = false)
class MailConfig {

    private static final String TIMEOUT_MS = "10000";

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

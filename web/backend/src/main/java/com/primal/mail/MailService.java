package com.primal.mail;

import com.primal.common.config.PrimalProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Отправка письма: текст и HTML в одном сообщении, UTF-8. */
@Service
public class MailService {

    private final JavaMailSender sender;
    private final InternetAddress from;

    public MailService(JavaMailSender sender, PrimalProperties properties) {
        this.sender = sender;
        this.from = parseFrom(properties.mail().from());
    }

    public void send(String to, String subject, String text, String html) {
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
        } catch (MessagingException exception) {
            throw new MailPreparationException(exception);
        }
        sender.send(message);
    }

    /** {@code MAIL_FROM} — «Primal <no-reply@example.com>» или просто адрес. */
    static InternetAddress parseFrom(String value) {
        try {
            InternetAddress address = new InternetAddress(value, true);
            return address.getPersonal() == null
                    ? address
                    : new InternetAddress(address.getAddress(), address.getPersonal(), "UTF-8");
        } catch (jakarta.mail.internet.AddressException | UnsupportedEncodingException exception) {
            throw new IllegalStateException("MAIL_FROM — некорректный адрес отправителя: " + value, exception);
        }
    }
}

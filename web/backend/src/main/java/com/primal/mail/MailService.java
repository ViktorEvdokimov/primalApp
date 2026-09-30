package com.primal.mail;

import com.primal.common.config.PrimalProperties;
import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import org.springframework.stereotype.Service;

/** Отправка письма: текст и HTML одного содержания, UTF-8. Способ доставки — {@link MailTransport}. */
@Service
public class MailService {

    private final MailTransport transport;
    private final InternetAddress from;

    MailService(MailTransport transport, PrimalProperties properties) {
        this.transport = transport;
        this.from = parseFrom(properties.mail().from());
    }

    public void send(String to, String subject, String text, String html) {
        transport.send(new OutgoingMail(from, to, subject, text, html));
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

package com.primal.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Отправка по SMTP ({@code SMTP_*}): текст и HTML в одном сообщении, UTF-8. Локально — Mailpit. */
class SmtpMailTransport implements MailTransport {

    private final JavaMailSender sender;

    SmtpMailTransport(JavaMailSender sender) {
        this.sender = sender;
    }

    @Override
    public void send(OutgoingMail mail) {
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mail.from());
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
        } catch (MessagingException exception) {
            throw new MailPreparationException(exception);
        }
        sender.send(message);
    }
}

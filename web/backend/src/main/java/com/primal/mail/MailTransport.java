package com.primal.mail;

/**
 * Как письмо доходит до почтового сервиса ({@code MAIL_PROVIDER}): SMTP или HTTP API Mailgun.
 * Неудача — {@link org.springframework.mail.MailException} с объяснением, без секретов.
 */
interface MailTransport {

    void send(OutgoingMail mail);
}

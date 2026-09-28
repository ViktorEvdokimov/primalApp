package com.primal.identity;

import com.primal.mail.LoginCodeMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Письмо с кодом отправляется после коммита и в фоне: медленный или недоступный SMTP не задерживает ответ.
 * Ошибка пишется в лог, пользователь может запросить код ещё раз.
 */
@Component
class LoginCodeMailSender {

    private static final Logger log = LoggerFactory.getLogger(LoginCodeMailSender.class);

    private final LoginCodeMail mail;

    LoginCodeMailSender(LoginCodeMail mail) {
        this.mail = mail;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void send(LoginCodeRequested event) {
        try {
            mail.send(event.email(), event.code(), event.validity());
        } catch (RuntimeException exception) {
            log.warn("Не удалось отправить письмо с кодом входа на {}: {}", event.email(), exception.getMessage());
        }
    }
}

package com.primal.support;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Письма в тестах: вместо SMTP сохраняются в памяти. Письмо с кодом уходит асинхронно после коммита,
 * поэтому методы {@code await…} ждут его до 5 секунд.
 */
public class MailCapture extends JavaMailSenderImpl {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    public MailCapture() {
        // Как в MailConfig: без этого Message-ID строится через медленный InetAddress.getLocalHost()
        getJavaMailProperties().put("mail.from", "no-reply@primal.local");
    }

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
        RuntimeException current = failure;
        if (current != null) {
            throw current;
        }
        for (MimeMessage message : mimeMessages) {
            try {
                message.saveChanges(); // как при настоящей отправке: заголовки и типы частей
            } catch (MessagingException exception) {
                throw new IllegalStateException("Письмо не собирается", exception);
            }
            sent.add(message);
        }
    }

    public void clear() {
        sent.clear();
        failure = null;
    }

    /** Следующие письма не отправляются: SMTP недоступен. */
    public void failWith(RuntimeException exception) {
        failure = exception;
    }

    public List<MimeMessage> sentTo(String email) {
        return sent.stream().filter(message -> isAddressedTo(message, email)).toList();
    }

    /** Ждёт письмо на адрес сверх {@code alreadySent} уже полученных и возвращает последнее. */
    public MimeMessage awaitMessageTo(String email, int alreadySent) {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            List<MimeMessage> messages = sentTo(email);
            if (messages.size() > alreadySent) {
                return messages.getLast();
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Письмо на " + email + " не пришло за " + WAIT.toSeconds() + " с; получены письма на "
                + sent.stream().map(MailCapture::recipients).toList());
    }

    /** Код из нового письма на адрес. */
    public String awaitCode(String email, int alreadySent) {
        Matcher matcher = CODE.matcher(text(awaitMessageTo(email, alreadySent)));
        if (!matcher.find()) {
            throw new AssertionError("В письме нет кода из 6 цифр");
        }
        return matcher.group(1);
    }

    /** Текстовая часть письма. */
    public static String text(MimeMessage message) {
        return part(message, "text/plain");
    }

    /** HTML-часть письма. */
    public static String html(MimeMessage message) {
        return part(message, "text/html");
    }

    private static String part(Part part, String mimeType) {
        try {
            Object content = part.getContent();
            if (content instanceof String text && part.isMimeType(mimeType)) {
                return text;
            }
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart body = multipart.getBodyPart(i);
                    String found = part(body, mimeType);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        } catch (MessagingException | IOException exception) {
            throw new IllegalStateException("Не удалось прочитать письмо", exception);
        }
    }

    private static String recipients(MimeMessage message) {
        try {
            return Arrays.toString(message.getAllRecipients());
        } catch (MessagingException exception) {
            return "?";
        }
    }

    private static boolean isAddressedTo(MimeMessage message, String email) {
        try {
            return Arrays.stream(message.getAllRecipients())
                    .map(address -> ((InternetAddress) address).getAddress())
                    .anyMatch(email::equalsIgnoreCase);
        } catch (MessagingException exception) {
            return false;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** Главный {@link JavaMailSender}: сервисы получают перехватчик вместо настоящего SMTP. */
        @Bean
        @Primary
        MailCapture mailCapture() {
            return new MailCapture();
        }
    }
}

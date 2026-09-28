package com.primal.mail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Письмо с кодом входа ({@code doc/api.md} §3). Шаблоны — {@code mail/login-code.txt} и {@code .html},
 * подстановки {@code {{code}}} и {@code {{minutes}}}.
 */
@Component
public class LoginCodeMail {

    private final MailService mail;
    private final String textTemplate = template("mail/login-code.txt");
    private final String htmlTemplate = template("mail/login-code.html");

    public LoginCodeMail(MailService mail) {
        this.mail = mail;
    }

    public void send(String email, String code, Duration validity) {
        Map<String, String> values = Map.of("code", code, "minutes", String.valueOf(validity.toMinutes()));
        mail.send(email, "Код входа в Primal: " + code, fill(textTemplate, values, false), fill(htmlTemplate, values, true));
    }

    private static String fill(String template, Map<String, String> values, boolean html) {
        String result = template;
        for (Map.Entry<String, String> value : values.entrySet()) {
            String replacement = html ? HtmlUtils.htmlEscape(value.getValue()) : value.getValue();
            result = result.replace("{{" + value.getKey() + "}}", replacement);
        }
        return result;
    }

    private static String template(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Нет шаблона письма " + path, exception);
        }
    }
}

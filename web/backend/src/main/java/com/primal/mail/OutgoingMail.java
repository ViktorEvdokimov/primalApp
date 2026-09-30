package com.primal.mail;

import jakarta.mail.internet.InternetAddress;

/** Письмо к отправке: текст и HTML одного содержания. */
record OutgoingMail(InternetAddress from, String to, String subject, String text, String html) {
}

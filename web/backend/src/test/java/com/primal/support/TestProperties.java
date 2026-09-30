package com.primal.support;

import com.primal.common.config.PrimalProperties;

/** Настройки {@code primal.*} для unit-тестов без Spring. */
public final class TestProperties {

    private TestProperties() {
    }

    public static PrimalProperties primal(String publicUrl, String shareKey) {
        return new PrimalProperties(
                publicUrl,
                new PrimalProperties.ShareLink(shareKey),
                new PrimalProperties.Campaign(10, new PrimalProperties.Hunters(2, 5), 24),
                new PrimalProperties.Mail(PrimalProperties.MailProvider.SMTP, "localhost", 1025, "", "",
                        PrimalProperties.MailTls.NONE, "Primal <no-reply@primal.local>",
                        new PrimalProperties.Mailgun("https://api.mailgun.net", "", "")),
                new PrimalProperties.RateLimit(20, 60, 30));
    }

    public static PrimalProperties primal(String publicUrl) {
        return primal(publicUrl, "test-share-link-key");
    }
}

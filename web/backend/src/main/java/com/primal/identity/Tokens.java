package com.primal.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Секреты входа: коды, токены устройств и их хеши. В БД попадают только хеши. */
final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {
    }

    /** Код из письма: 6 цифр. */
    static String newCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** Токен устройства: 32 случайных байта в base64url. */
    static String newDeviceToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** {@code HMAC-SHA256(pepper, challengeId || code)}: без pepper утёкшую БД не перебрать офлайн. */
    static byte[] codeHash(String pepper, UUID challengeId, String code) {
        byte[] codeBytes = code.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer input = ByteBuffer.allocate(16 + codeBytes.length)
                .putLong(challengeId.getMostSignificantBits())
                .putLong(challengeId.getLeastSignificantBits())
                .put(codeBytes);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(input.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 недоступен", exception);
        }
    }

    static byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }
}

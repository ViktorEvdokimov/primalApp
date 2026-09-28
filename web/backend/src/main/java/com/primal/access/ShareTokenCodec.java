package com.primal.access;

import com.primal.common.config.PrimalProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Токен ссылки-приглашения: {@code base64url(linkId ‖ HMAC-SHA256(key, linkId)[0..16])}, 43 символа. Сам токен
 * не хранится — его можно вычислить из id ссылки в любой момент, а подделать без ключа нельзя
 * ({@code doc/data-model.md} §3.5). Подпись сравнивается за постоянное время.
 */
@Component
public class ShareTokenCodec {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int ID_BYTES = 16;
    private static final int MAC_BYTES = 16;

    private final SecretKeySpec key;

    @Autowired
    ShareTokenCodec(PrimalProperties properties) {
        this(properties.shareLink().key());
    }

    ShareTokenCodec(String key) {
        this.key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String encode(UUID linkId) {
        byte[] id = bytes(linkId);
        byte[] token = ByteBuffer.allocate(ID_BYTES + MAC_BYTES).put(id).put(mac(id)).array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    /** Id ссылки из токена; пусто — токен повреждён или подделан. */
    public Optional<UUID> decode(String token) {
        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(token);
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        if (raw.length != ID_BYTES + MAC_BYTES) {
            return Optional.empty();
        }
        byte[] id = Arrays.copyOfRange(raw, 0, ID_BYTES);
        byte[] signature = Arrays.copyOfRange(raw, ID_BYTES, raw.length);
        if (!MessageDigest.isEqual(mac(id), signature)) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(id);
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }

    private byte[] mac(byte[] id) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return Arrays.copyOf(mac.doFinal(id), MAC_BYTES);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HMAC-SHA256 недоступен", impossible);
        }
    }

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(ID_BYTES).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
}

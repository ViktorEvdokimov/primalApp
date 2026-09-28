package com.primal.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Токен ссылки-приглашения")
class ShareTokenCodecTest {

    private final ShareTokenCodec codec = new ShareTokenCodec("test-share-key");

    @Test
    @DisplayName("id ссылки восстанавливается из токена; токен — 43 символа base64url")
    void roundTrip() {
        // подготовка
        UUID id = UUID.randomUUID();

        // вызов
        String token = codec.encode(id);

        // проверка
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(codec.decode(token)).contains(id);
        assertThat(codec.encode(id)).isEqualTo(token);
    }

    @Test
    @DisplayName("подделанная подпись, чужой ключ, мусор и неверная длина не принимаются")
    void forged() {
        // подготовка
        UUID id = UUID.randomUUID();
        byte[] raw = Base64.getUrlDecoder().decode(codec.encode(id));
        raw[raw.length - 1] ^= 1;
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        // вызов и проверка
        assertThat(codec.decode(tampered)).isEmpty();
        assertThat(codec.decode(new ShareTokenCodec("other-key").encode(id))).isEmpty();
        assertThat(codec.decode("не base64!")).isEmpty();
        assertThat(codec.decode("AAAA")).isEmpty();
    }
}

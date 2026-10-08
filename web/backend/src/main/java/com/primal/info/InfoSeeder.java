package com.primal.info;

import com.primal.info.InfoService.InfoSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * «Инфо» по умолчанию (qa № 145): при запуске, если в «Инфо» нет ни одной статьи, оно заполняется из
 * {@code info/default-info.json} — выгрузки, подготовленной на локальном сайте ({@code deploy/export-info.sh}).
 * Уже заполненное «Инфо» не трогается: правки на сайте не теряются. В тестах выключено ({@code primal.info.seed}).
 */
@Component
class InfoSeeder {

    static final String DEFAULT_FILE = "info/default-info.json";
    private static final Logger log = LoggerFactory.getLogger(InfoSeeder.class);
    private static final JsonMapper JSON = new JsonMapper();

    private final InfoService info;
    private final boolean enabled;

    InfoSeeder(InfoService info, @Value("${primal.info.seed:true}") boolean enabled) {
        this.info = info;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    void seedOnStartup() {
        if (enabled) {
            seed(new ClassPathResource(DEFAULT_FILE));
        }
    }

    /** Сколько статей добавлено: 0 — «Инфо» уже заполнено или файла нет. */
    int seed(Resource file) {
        if (!file.exists() || !info.isEmpty()) {
            return 0;
        }
        try (InputStream in = file.getInputStream()) {
            int count = info.restore(JSON.readValue(in, InfoSnapshot.class), null);
            log.info("«Инфо» заполнено из {}: статей {}", DEFAULT_FILE, count);
            return count;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

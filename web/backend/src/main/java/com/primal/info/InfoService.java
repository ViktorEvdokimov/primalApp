package com.primal.info;

import com.primal.common.api.ApiNullable;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.info.RulesKeywordParser.ParsedEntry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.text.Collator;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Раздел «Инфо» (qa № 142): статьи по разделам с текстом и картинкой. Читают все (и без входа — бой экспедиции),
 * правит администратор (права проверяет вызывающий, модуль {@code admin}).
 */
@Service
public class InfoService {

    /**
     * Статья: {@code body} — абзацы через пустую строку, {@code **жирный**}; {@code imageUrl} — картинка или null;
     * {@code title} — null у символа реакции (в правилах у них только картинка, qa № 143).
     */
    public record InfoEntry(long id, InfoSection section, @ApiNullable String title, String body,
                            @ApiNullable String imageUrl) {
    }

    /** {@code titled = false} — у статей нет названия (символы реакций). */
    public record InfoSectionView(InfoSection code, String title, boolean titled, List<InfoEntry> entries) {
    }

    /** Все разделы по порядку меню, статьи — по алфавиту (символы реакций — в порядке добавления); {@code version} — для ETag. */
    public record InfoView(List<InfoSectionView> sections, String version) {
    }

    public record Image(String contentType, byte[] data) {
    }

    /** Итог импорта: найдено в файле, добавлено, пропущено (статья с таким названием уже есть). */
    public record ImportResult(int found, int created, int skipped) {
    }

    /**
     * Выгрузка «Инфо» (qa № 145): все статьи с картинками (Base64) в порядке добавления — файл по умолчанию для
     * развёртывания ({@code info/default-info.json}) и перенос между сайтами.
     */
    public record InfoSnapshot(int format, List<SnapshotEntry> entries) {
    }

    public record SnapshotEntry(InfoSection section, @ApiNullable String title, String body,
                                @ApiNullable SnapshotImage image) {
    }

    public record SnapshotImage(String contentType, String data) {
    }

    static final int SNAPSHOT_FORMAT = 1;
    static final int IMAGE_MAX_BYTES = 2 * 1024 * 1024;
    private static final String IMAGE_PATH = "/api/v1/info/images/";
    private static final Comparator<InfoEntry> BY_TITLE = Comparator.comparing(InfoEntry::title,
            Collator.getInstance(Locale.forLanguageTag("ru")));

    private final JdbcTemplate jdbc;
    private final Clock clock;

    InfoService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InfoView view() {
        List<InfoEntry> all = jdbc.query("select id, section, title, body, image_id from info_entry", ENTRY);
        List<InfoSectionView> sections = Arrays.stream(InfoSection.values())
                .map(section -> new InfoSectionView(section, section.title(), section.titled(), all.stream()
                        .filter(entry -> entry.section() == section)
                        .sorted(section.titled() ? BY_TITLE : Comparator.comparingLong(InfoEntry::id))
                        .toList()))
                .toList();
        return new InfoView(sections, version(all));
    }

    @Transactional(readOnly = true)
    public Optional<Image> image(UUID id) {
        return jdbc.query("select content_type, data from info_image where id = ?",
                (rs, row) -> new Image(rs.getString(1), rs.getBytes(2)), id).stream().findFirst();
    }

    @Transactional
    public InfoEntry create(InfoSection section, String title, String body, long adminId) {
        String cleanTitle = title(section, title);
        try {
            Long id = jdbc.queryForObject("""
                    insert into info_entry (section, title, body, created_at, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?) returning id""", Long.class,
                    section.name(), cleanTitle, body(body), now(), now(), adminId);
            return entry(id);
        } catch (DuplicateKeyException e) {
            throw duplicate(cleanTitle);
        }
    }

    @Transactional
    public InfoEntry update(long id, InfoSection section, String title, String body, long adminId) {
        entry(id);
        String cleanTitle = title(section, title);
        try {
            jdbc.update("update info_entry set section = ?, title = ?, body = ?, updated_at = ?, updated_by = ? where id = ?",
                    section.name(), cleanTitle, body(body), now(), adminId, id);
        } catch (DuplicateKeyException e) {
            throw duplicate(cleanTitle);
        }
        return entry(id);
    }

    @Transactional
    public void delete(long id) {
        InfoEntry entry = entry(id);
        jdbc.update("delete from info_entry where id = ?", id);
        deleteImage(entry);
    }

    /** Картинка статьи: PNG, JPEG, WebP или GIF до 2 МБ; тип — по содержимому файла, а не по имени. */
    @Transactional
    public InfoEntry setImage(long id, byte[] data, long adminId) {
        InfoEntry entry = entry(id);
        if (data.length == 0 || data.length > IMAGE_MAX_BYTES) {
            throw invalid("file", "Картинка — до 2 МБ.");
        }
        String contentType = imageType(data)
                .orElseThrow(() -> invalid("file", "Картинка — PNG, JPEG, WebP или GIF."));
        UUID imageId = UUID.randomUUID();
        jdbc.update("insert into info_image (id, content_type, data) values (?, ?, ?)", imageId, contentType, data);
        jdbc.update("update info_entry set image_id = ?, updated_at = ?, updated_by = ? where id = ?", imageId, now(), adminId, id);
        deleteImage(entry);
        return entry(id);
    }

    @Transactional
    public InfoEntry removeImage(long id, long adminId) {
        InfoEntry entry = entry(id);
        jdbc.update("update info_entry set image_id = null, updated_at = ?, updated_by = ? where id = ?", now(), adminId, id);
        deleteImage(entry);
        return entry(id);
    }

    /**
     * Ключевые слова из Markdown-файла правил: раздел «Ключевые слова» разбирается {@link RulesKeywordParser};
     * статьи с уже существующим названием не меняются (правки администратора не теряются).
     */
    @Transactional
    public ImportResult importKeywords(byte[] file, long adminId) {
        List<ParsedEntry> parsed = RulesKeywordParser.parse(new String(file, StandardCharsets.UTF_8));
        if (parsed.isEmpty()) {
            throw invalid("file", "В файле нет раздела «Ключевые слова».");
        }
        int created = 0;
        for (ParsedEntry entry : parsed) {
            created += jdbc.update("""
                    insert into info_entry (section, title, body, created_at, updated_at, updated_by)
                    values ('KEYWORDS', ?, ?, ?, ?, ?)
                    on conflict (section, lower(title)) where title is not null do nothing""",
                    entry.title(), entry.body(), now(), now(), adminId);
        }
        return new ImportResult(parsed.size(), created, parsed.size() - created);
    }

    /** В «Инфо» нет ни одной статьи — его можно заполнить файлом по умолчанию. */
    @Transactional(readOnly = true)
    public boolean isEmpty() {
        Integer count = jdbc.queryForObject("select count(*) from info_entry", Integer.class);
        return count == null || count == 0;
    }

    @Transactional(readOnly = true)
    public InfoSnapshot export() {
        List<SnapshotEntry> entries = jdbc.query("""
                select e.section, e.title, e.body, i.content_type, i.data
                from info_entry e left join info_image i on i.id = e.image_id order by e.id""",
                (rs, row) -> {
                    byte[] data = rs.getBytes(5);
                    return new SnapshotEntry(InfoSection.valueOf(rs.getString(1)), rs.getString(2), rs.getString(3),
                            data == null ? null : new SnapshotImage(rs.getString(4), Base64.getEncoder().encodeToString(data)));
                });
        return new InfoSnapshot(SNAPSHOT_FORMAT, entries);
    }

    /**
     * Всё «Инфо» — из выгрузки: старые статьи и картинки удаляются, новые добавляются в порядке файла. Сначала
     * проверяется вся выгрузка (названия, тексты, картинки) — при ошибке ничего не меняется.
     */
    @Transactional
    public int restore(InfoSnapshot snapshot, Long adminId) {
        if (snapshot == null || snapshot.format() != SNAPSHOT_FORMAT || snapshot.entries() == null) {
            throw invalid("file", "Это не выгрузка «Инфо» (формат " + SNAPSHOT_FORMAT + ").");
        }
        record Prepared(InfoSection section, String title, String body, String contentType, byte[] image) {
        }
        List<Prepared> prepared = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        int number = 0;
        for (SnapshotEntry entry : snapshot.entries()) {
            number++;
            if (entry == null || entry.section() == null) {
                throw invalid("file", "Статья " + number + ": не указан раздел.");
            }
            String title = title(entry.section(), entry.title());
            if (title != null && !titles.add(entry.section() + "|" + title.toLowerCase(Locale.ROOT))) {
                throw invalid("file", "Статья «" + title + "» повторяется.");
            }
            String contentType = null;
            byte[] image = null;
            if (entry.image() != null) {
                try {
                    image = Base64.getMimeDecoder().decode(entry.image().data());
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw invalid("file", "Статья " + number + ": картинка повреждена.");
                }
                String where = "Статья " + number;
                contentType = imageType(image).orElseThrow(() -> invalid("file", where
                        + ": картинка — не PNG, JPEG, WebP или GIF."));
                if (image.length > IMAGE_MAX_BYTES) {
                    throw invalid("file", "Статья " + number + ": картинка больше 2 МБ.");
                }
            }
            prepared.add(new Prepared(entry.section(), title, body(entry.body()), contentType, image));
        }
        jdbc.update("delete from info_entry");
        jdbc.update("delete from info_image");
        for (Prepared entry : prepared) {
            UUID imageId = null;
            if (entry.image() != null) {
                imageId = UUID.randomUUID();
                jdbc.update("insert into info_image (id, content_type, data) values (?, ?, ?)", imageId, entry.contentType(),
                        entry.image());
            }
            jdbc.update("""
                    insert into info_entry (section, title, body, image_id, created_at, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, ?)""",
                    entry.section().name(), entry.title(), entry.body(), imageId, now(), now(), adminId);
        }
        return prepared.size();
    }

    private InfoEntry entry(long id) {
        return jdbc.query("select id, section, title, body, image_id from info_entry where id = ?", ENTRY, id).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Статьи нет."));
    }

    private void deleteImage(InfoEntry entry) {
        if (entry.imageUrl() != null) {
            jdbc.update("delete from info_image where id = ?", UUID.fromString(entry.imageUrl().substring(IMAGE_PATH.length())));
        }
    }

    private static final RowMapper<InfoEntry> ENTRY = (rs, row) -> {
        UUID image = rs.getObject(5, UUID.class);
        return new InfoEntry(rs.getLong(1), InfoSection.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4),
                image == null ? null : IMAGE_PATH + image);
    };

    /** Сигнатура файла: PNG, JPEG, GIF, WebP. */
    static Optional<String> imageType(byte[] data) {
        if (startsWith(data, 0x89, 'P', 'N', 'G')) {
            return Optional.of("image/png");
        }
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(data, 'G', 'I', 'F', '8')) {
            return Optional.of("image/gif");
        }
        if (data.length >= 12 && startsWith(data, 'R', 'I', 'F', 'F') && data[8] == 'W' && data[9] == 'E'
                && data[10] == 'B' && data[11] == 'P') {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String version(List<InfoEntry> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            entries.stream().sorted(Comparator.comparingLong(InfoEntry::id)).forEach(entry -> digest.update(
                    (entry.id() + "|" + entry.section() + "|" + entry.title() + "|" + entry.body() + "|" + entry.imageUrl() + "\n")
                            .getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest()).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Название обязательно, кроме символов реакций — у них его нет (переданное не сохраняется). */
    private static String title(InfoSection section, String title) {
        if (!section.titled()) {
            return null;
        }
        String clean = title == null ? "" : title.replaceAll("\\s+", " ").strip();
        if (clean.isEmpty() || clean.length() > 120) {
            throw invalid("title", "Название — от 1 до 120 символов.");
        }
        return clean;
    }

    private static String body(String body) {
        String clean = body == null ? "" : body.replace("\r\n", "\n").strip();
        if (clean.length() > 20_000) {
            throw invalid("body", "Текст — не длиннее 20 000 символов.");
        }
        return clean;
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    private static ApiException duplicate(String title) {
        return invalid("title", "Статья «" + title + "» в этом разделе уже есть.");
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Проверьте заполнение полей.")
                .with("errors", List.of(Map.of("field", field, "message", message)));
    }
}
